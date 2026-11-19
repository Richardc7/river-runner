# Nine-player V2 foundation: multiway river closure

This is the first **incremental** nine-player engine patch, not the complete V2
multi-street AI or full multiway GTO evaluator.

- `JointRangeSampler` samples **up to eight live opponent hands** without
  shared cards. Sequential draws are only proposals; the returned product of
  available range masses supplies an importance weight. Weighted averages
  target the product of marginal inferred ranges **conditioned on legal cards**.
  This assumes independent marginal ranges before conditioning, not a solved
  correlated nine-way strategy.
- `MultiwayRiverEv` estimates **terminal** 3-9-way cash river CHECK or CALL
  versus FOLD. It checks that all other players have completed their river
  actions, estimates payouts against weighted legal joint hands, honors
  eligible side pots, unmatched wager refunds, tied winners and the game's
  `RakeSettlement` rules.
- The `low` / `high` fields for this calculator are approximate
  two-standard-error **sampling** bands, **not** confidence intervals for
  the range model, future opponents' responses, or Solver/GTO correctness.
  StudyCoach uses non-overlapping sampling bands for multiway model gaps.
- `StrategySession.capture` saves model values for eligible 3-9-way terminal
  river decisions. Unsupported cases explicitly say that further action
  modelling is required, rather than pretending a closing call has a
  known showdown payoff.

**Not yet covered:** nonterminal river decisions; bet, raise and re-raise
continuations; multi-street 3-9-way action EV; tournament ICM in multiway
action evaluation; calibrated humanlike persona policies. Existing HU
model remains unchanged.

## Validation

`./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug`

New tests include a biased-sequential-sampling counterexample, eight
non-overlapping opponent hands, 3- and 9-way terminal calls, side pots,
rake and invalid nonterminal contexts. GitHub Actions runs on the
`exploit/**` branch before opening the PR.
