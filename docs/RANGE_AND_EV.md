# V0.2 range and river-value model

`WeightedRange` represents normalized, non-duplicated concrete two-card combinations. Blocked cards are removed and remaining weights normalized. `RangeModel` combines position-aware preflop widths with likelihood weights for publicly observed actions. This is an explicit heuristic model, not a solver export or an unbiased measurement of an opponent's bluff frequency.

`RiverEv` enumerates unblocked combinations exactly, conditional on the supplied range. The scope is cash, one live opponent, river, no prior side-pot mismatch, and terminal check/call decisions. Unsupported situations return a reason instead of a guessed number. A free out-of-position check is excluded because future betting remains. Folded dead money is only included when eligible.

Values are incremental chip EV from the decision: fold is zero, terminal check is equity times the pot, and call is equity times the contestable pot after calling minus the call. Unmatched excess against a short stack is excluded. Bet/raise amounts are capped by effective opposing stacks; uncalled excess is refunded. Opponent fold/call/raise likelihood depends on their own model hand and public context, never on the actual hero cards. Bet/raise branches assume the hero folds to a re-raise; this is a specified continuation, not a complete optimal tree.

Posterior response rates fall back to labelled engineering priors when evidence is sparse. Displayed low/high values vary response assumptions; they are **not statistical confidence intervals**, exclude range-model error, and do not certify a solver EV loss. The stored summary names the range and EV model versions and includes top weighted hand classes.

V0.2 stores these values in the contemporaneous review trace. They are coaching-only and do not silently override the conservative V0.1 live policy. No real opponent cards, future observations, internet API or unlicensed solver datasets are used.

Tests cover normalization, sampling, card removal, exact pot-odds break-even, short-stack uncalled money, effective-stack value bets, pure-bluff endpoints, unsupported contexts, public range inference and saved-trace integration. The initial pure-bluff fixture was corrected because A-2-3-4-5 was actually a straight; this was a test-data error, not an EV formula error.
