# V0.3 simulated cash house rules

These are explicit configurable house variants, not a claim that every private game uses identical rules. They apply only to cash sessions; tournament sessions ignore cash stack/rake/rebuy options.

## Live straddle

At three or more dealt-in seats, the first seat left of the big blind posts a live 2BB straddle after antes and blinds, only if fully funded. Underfunded straddles and heads-up straddles are skipped. Preflop action starts left of the straddler; the straddler retains the final unraised option. The initial full raise increment is 2BB. Postflop order and minimum bet return to ordinary nominal-blind rules. The nominal BB used by histories/statistics does not change. The original heuristic bot is not claimed to be optimally calibrated for all house variants.

## Rake

Rake uses integer basis points and a per-hand nominal-BB cap. It is deducted only after uncalled excess is returned. No-flop-no-drop is optional. All-in runouts that actually deal a flop qualify; a preflop fold-out does not. Side pots share a single cap. Proportional deductions use largest remainders to conserve integer chips. Pot.amount is the net awarded pot, Pot.rake is its deduction, and HandResult.rake is the hand total. Final stacks plus collected rake equal initial stacks. Uncalled returned money is never raked.

The table pot-odds display remains gross before fees. Conditional river EV is withheld when rake is enabled rather than pretending the unraked model is fee-aware. This limitation is shown in settings and the trace.

## Buy-ins

Per-seat initial stacks allow 10-10000 nominal BB through configuration. Automatic bust rebuys are bounded by a configured count per seat and occur at the next hand, not during settlement. Manual top-ups are only allowed between hands, and the UI tops up to the configured starting target. Every additional buy-in is recorded. Session cash profit is final stack minus all paid-in chips, not final stack minus only the first buy-in. Buy-ins are not counted as hand winnings. No money or payments are involved.

## History compatibility

Default-rule hands still write unchanged H1. Non-default cash hands write H2 with appended house-rule configuration and actual collected rake. Existing ActionType ordinals are preserved; STRADDLE is appended and treated as a forced post. Replay reconstructs the same rules, including rake and side pots. New code reads H1 and H2; old upstream builds cannot read H2, so downgrade is not promised for new-rule hands. Default H1 decision fingerprints are unchanged.
