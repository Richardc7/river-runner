# Opponent observation contract

Statistics use public before-action opportunities, never hidden cards or preset labels. Instances live for one table session only. Snapshots copy immutable counters; later hands cannot alter an earlier decision's evidence.

VPIP/PFR have one opportunity per hand in which the player can act voluntarily; later preflop investment/raising changes that hand's success, not its denominator. A forced blind all-in with no decision is not an opportunity (unlike dealt-hand HUD VPIP). Three-bet opportunities require exactly one preceding voluntary raise by another player. Fold-to-three-bet requires exactly two raises and prior voluntary investment by the subject.

Postflop fold/call/raise frequencies use actual facing-bet decisions. Free-bet frequency uses actual decisions with no amount to call. Continuation bets require exactly one current-street bet by the preceding street's last aggressor. Heads-up and multiway observations are separate. Size buckets measure the original bettor's raise increment over the pot after calling; aggregate street buckets are recorded separately. Sparse detailed buckets fall back within the same street and player-count class only.

Beta-prior parameters and evidence weight are engineering modelling choices, not universal population facts. Evidence weight n/(n+k) is not the probability that a poker recommendation is correct. Revealed hands and showdown results are deliberately not used to claim an unbiased bluff-rate estimate.

Duplicate event retries are idempotent. Conflicting duplicates, stale events and skipped action indices are rejected.
