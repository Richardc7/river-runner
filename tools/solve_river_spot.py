#!/usr/bin/env python3
"""Solve an exported River Runner spot with a locally supplied TexasSolver console."""

import argparse
import base64
from decimal import Decimal
from hashlib import sha256
import json
import math
from pathlib import Path
import re
import subprocess
import tempfile


CARD = r"[2-9TJQKA][cdhs]"
RANGE_TERM = re.compile(rf"(?:(?:{CARD}){{2}}|[2-9TJQKA]{{2}}[so]?)(?::(?:0?\.\d+|0|1(?:\.0+)?))?")
SIZED_ACTION = re.compile(r"(BET|RAISE) ([0-9]+(?:\.[0-9]+)?)")
PLAYER = {"IP": 0, "OOP": 1}


def read_spot(path):
    spot = json.loads(path.read_text(encoding="utf-8"))
    if spot["format"] != "RR-SOLVER-SPOT-1" or not re.fullmatch(r"[a-f0-9]{64}", spot["contextHash"]):
        raise ValueError("Use a River Runner solver spot JSON file.")
    board = spot["board"]
    hero = spot["heroCards"]
    if len(board) != 5 or not all(re.fullmatch(CARD, card) for card in board):
        raise ValueError("The solver spot must contain a five-card river board.")
    if not re.fullmatch(rf"(?:{CARD}){{2}}", hero) or len(set([*board, hero[:2], hero[2:]])) != 7:
        raise ValueError("The board and hero cards must be distinct.")
    for field in ("startingPot", "effectiveStack", "bigBlind"):
        if type(spot[field]) is not int or not 1 <= spot[field] <= 10_000_000:
            raise ValueError(f"Invalid chip amount: {field}.")
    actions = spot["riverActions"]
    if spot["heroRole"] not in PLAYER or PLAYER[spot["heroRole"]] != 1 - len(actions) % 2:
        raise ValueError("The action path does not end at the hero.")
    for action in actions:
        kind, amount = action["type"], action["amount"]
        if (kind not in ("CHECK", "BET", "RAISE") or type(amount) is not int
                or (amount != 0 if kind == "CHECK" else not 0 < amount <= spot["effectiveStack"])):
            raise ValueError("Invalid river action path.")
    return spot


def range_text(value):
    terms = value.replace(" ", "").split(",")
    if not 1 <= len(terms) <= 1326 or not all(RANGE_TERM.fullmatch(term) for term in terms):
        raise argparse.ArgumentTypeError("Supply comma-separated hands or combos with optional weights, e.g. AA,KQs:0.5,AsKd.")
    return ",".join(terms)


def size_list(value):
    try:
        sizes = [float(part) for part in value.split(",")]
    except ValueError as error:
        raise argparse.ArgumentTypeError("Supply comma-separated percentages, e.g. 50,100.") from error
    if not 1 <= len(sizes) <= 4 or not all(1 <= size <= 1000 for size in sizes):
        raise argparse.ArgumentTypeError("Each of the one to four sizes must be between 1% and 1000%.")
    return sizes


def build_input(spot, args):
    sizes = {(role, kind): list(values) for role in PLAYER
             for kind, values in (("bet", args.bet_sizes), ("raise", args.raise_sizes))}
    # TexasSolver's sized labels contain the added chips, including the call on a raise.
    committed = {"IP": 0, "OOP": 0}
    for index, action in enumerate(spot["riverActions"]):
        role = "OOP" if index % 2 == 0 else "IP"
        if action["type"] != "CHECK":
            high = max(committed.values())
            increase = action["amount"] - (high - committed[role])
            if increase <= 0:
                raise ValueError("The exported raise does not increase the wager.")
            percentage = 100 * increase / (spot["startingPot"] + 2 * high)
            sizes[role, action["type"].lower()].append(percentage)
            committed[role] += action["amount"]
    commands = [
        f"set_pot {spot['startingPot']}",
        f"set_effective_stack {spot['effectiveStack']}",
        "set_board " + ",".join(spot["board"]),
        f"set_range_ip {args.ip_range}",
        f"set_range_oop {args.oop_range}",
    ]
    for (role, kind), values in sizes.items():
        distinct = sorted({f"{value:.8f}" for value in values}, key=float)
        commands.append(f"set_bet_sizes {role.lower()},river,{kind}," + ",".join(distinct))
    commands += [
        "set_allin_threshold 1",
        "build_tree",
        "set_thread_num 1",
        "set_use_isomorphism 0",
        "set_accuracy 0.2",
        f"set_max_iteration {args.iterations}",
        "start_solve",
        "set_dump_rounds 1",
        "dump_result result.json",
    ]
    return "\n".join(commands) + "\n"


def action_key(label):
    if label in ("CHECK", "FOLD", "CALL"):
        return label, 0
    match = SIZED_ACTION.fullmatch(label)
    if match:
        amount = Decimal(match[2])
        if amount == int(amount) and amount > 0:
            return match[1], int(amount)
    raise ValueError("Unsupported action in the TexasSolver export.")


def extract_strategy(tree, spot):
    node = tree
    for action in spot["riverActions"]:
        expected = action["type"], action["amount"]
        children = node.get("childrens") or {}
        branches = [child for label, child in children.items() if action_key(label) == expected]
        if len(branches) != 1:
            raise ValueError(f"The solve has no exact branch for {expected[0]} {expected[1]} chips.")
        node = branches[0]
    if node["node_type"] != "action_node" or node["player"] != PLAYER[spot["heroRole"]]:
        raise ValueError("The selected solver node is not the hero's decision.")
    strategy = node["strategy"]
    labels = node["actions"]
    if labels != strategy["actions"]:
        raise ValueError("The solver action and frequency tables disagree.")
    hand = spot["heroCards"]
    reversed_hand = hand[2:] + hand[:2]
    probabilities = strategy["strategy"].get(hand, strategy["strategy"].get(reversed_hand))
    if probabilities is None:
        raise ValueError("The hero's combo is not in the supplied range.")
    if (len(labels) != len(probabilities) or not all(math.isfinite(p) and 0 <= p <= 1 for p in probabilities)
            or abs(sum(probabilities) - 1) > 0.0001):
        raise ValueError("Invalid solver frequencies for the hero's combo.")
    legal = spot["legal"]
    result = {}
    for label, probability in zip(labels, probabilities):
        kind, amount = action_key(label)
        if kind in ("BET", "RAISE"):
            target = legal["heroStreetBet"] + amount
            allowed = legal["canRaise"] and legal["minRaiseTo"] <= target <= legal["maxRaiseTo"]
            move = "RAISE", target
        else:
            allowed = {"CHECK": legal["canCheck"], "CALL": legal["canCall"], "FOLD": not legal["canCheck"]}[kind]
            move = kind, 0
        if not allowed:
            raise ValueError(f"The solver included an illegal action for this spot: {label}. Adjust the tree sizes.")
        if move in result:
            raise ValueError(f"The solver returned duplicate action {label}.")
        result[move] = probability
    total = sum(result.values())
    return [(kind, target, frequency / total) for (kind, target), frequency in result.items() if frequency > 0]


def write_reference(path, spot, strategy, author, rights, input_text):
    bb = spot["bigBlind"]
    config = sha256(input_text.encode()).hexdigest()[:12]
    context = (f"River · pot {spot['startingPot'] / bb:g} BB · effective {spot['effectiveStack'] / bb:g} BB · "
               f"TexasSolver tree {config}")
    fields = ["TexasSolver console", author, rights, context]
    if not all(1 <= len(value) <= 200 and "\n" not in value and "\t" not in value for value in fields):
        raise ValueError("Source information must be between 1 and 200 characters per field.")
    encoded = [base64.urlsafe_b64encode(value.encode()).decode().rstrip("=") for value in fields]
    lines = ["RR-REFERENCE-1", "\t".join(encoded), f"N\t{spot['contextHash']}"]
    lines.extend(f"A\t{kind}\t{target}\t{frequency:.17g}" for kind, target, frequency in strategy)
    lines.append("E")
    with path.open("x", encoding="utf-8") as output:
        output.write("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("spot", type=Path)
    parser.add_argument("output", type=Path, help="New RR-REFERENCE-1 file")
    parser.add_argument("--solver", type=Path, required=True, help="Path to a trusted console_solver executable")
    parser.add_argument("--resources", type=Path, required=True, help="TexasSolver resources directory")
    parser.add_argument("--ip-range", type=range_text, required=True)
    parser.add_argument("--oop-range", type=range_text, required=True)
    parser.add_argument("--bet-sizes", type=size_list, default=[50, 100])
    parser.add_argument("--raise-sizes", type=size_list, default=[50])
    parser.add_argument("--iterations", type=int, default=200, choices=range(1, 2001), metavar="1–2000")
    parser.add_argument("--author", required=True, help="Person or organisation responsible for the solve")
    parser.add_argument("--rights", required=True, help="Licence or permission statement for the result")
    args = parser.parse_args()
    try:
        if args.output.exists():
            raise ValueError("The output already exists. Choose a new filename.")
        spot = read_spot(args.spot)
        input_text = build_input(spot, args)
        with tempfile.TemporaryDirectory(prefix="river-runner-solve-") as working:
            directory = Path(working)
            (directory / "spot.txt").write_text(input_text, encoding="utf-8")
            subprocess.run(
                [str(args.solver.resolve()), "--input_file", "spot.txt",
                 "--resource_dir", str(args.resources.resolve()), "--mode", "holdem"],
                cwd=directory, check=True, timeout=300,
            )
            tree = json.loads((directory / "result.json").read_text(encoding="utf-8"))
        strategy = extract_strategy(tree, spot)
        write_reference(args.output, spot, strategy, args.author, args.rights, input_text)
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        parser.exit(1, f"Solver import preparation failed: {error}\n")
    print(f"Saved {args.output}")


if __name__ == "__main__":
    main()
