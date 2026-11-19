# River Runner EE V0.5 replay coach

Six independent toggles are available in the normal Settings panel, under **Replay coach settings**.

1. Blind replay – Choose before exposing the original action or strategy frequencies. The scenario only provides hero hole cards, board visible at decision time, pot, legal choices and preceding public actions; invalid hashes are rejected.
2. Weakness report – Aggregates per-profile, matched decision-time traces by situation, position, effective stack, recent bet size and pot odds; shows sample sizes. A low-frequency model action is **not** established to be GTO-inaccurate.
3. Spaced drills – Generates questions from flagged historical decisions. Reviewing changes a separately stored due day (1/3/7/14-day schedule); due scheduling cannot reveal opponent private cards or later community cards.
4. Preflop matrix – 13×13 class-by-class, position- and depth-dependent heuristic widths, with clear RFI / facing-raise / short-stack limits. **Not an independently verified solver or multi-way equilibrium chart.**
5. River what-if – Changes bet fraction, villain fold rate, range-strength assumption and remaining effective stack budget at an otherwise frozen decision. **Only** supported heads-up cash river contexts; raked/side-pot and future-decision cases are declined. Do not interpret these numbers as realized winnings.
6. Live GTO / strategy reference toggle – OFF by default. During hero decision only, shows exact-context imported *unverified* declared reference where available; otherwise labels a heuristic decision-time baseline as **NOT GTO**. The option is named for discoverability and **does not bundle or imply verified GTO data**.

## Data and compatibility
- Historical H1/H2 hands and the original strategy trace binary format are unchanged.
- Review schedule stored separately as `study_spaced_<profile>.json`. Old saves remain readable.
- Historical evidence may be missing if recording was disabled; modules display an explicit no-data state, not fabricated answers.
- Feature flags are separate SharedPreferences entries and apply immediately to next view/turn. Closing the app preserves them.
- Preview/CI APK is debug-signed; no physical-device install guarantee, source-level verification must include CI.
