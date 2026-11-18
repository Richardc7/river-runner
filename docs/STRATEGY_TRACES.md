# Contemporaneous strategy traces

H1 hand histories are unchanged. Optional strategy sidecars are stored as `analysis_<profileId>/<handId>.rrt`, one atomic file per completed hand. This deliberately replaces the earlier JSONL proposal: bounded binary records allow plain-JVM tests, per-hand atomic replacement and cheap pruning without adding a JSON runtime to the engine.

The RRTX envelope has a schema number, payload length (maximum 4 MiB) and CRC32. Payloads use stable enum names, not ordinals. Unknown schemas, malformed probability distributions, oversized counts, corruption and mismatched hand ids are rejected. Missing analysis never prevents a legacy replay. CRC is for accidental corruption, not adversarial authentication.

Before each human decision, the session captures both policies, evidence counters, opponent snapshots, model/RNG versions and a SHA-256 fingerprint of the actor's information set. Other hole cards, future board cards and actual opponent preset labels are absent. Later observations cannot alter the saved snapshot. Replay validates the fingerprint before showing advice and does not reinterpret old hands using future opponent knowledge.

The authoritative hand is written first. Optional analysis write failures leave it intact. Sidecars are pruned to the retained hand ids after hand saves and before review; manual history clears are therefore garbage-collected on the next save/review. Favorite ids survive. Deleting a profile removes its analysis directory. Opponent learning remains session-local.

The report shows frequency changes and evidence, not a fabricated solver EV-loss score. Action-value fields are reserved for explicitly labelled, scope-limited model calculations. Evidence weight is not a probability that a recommendation is correct.
