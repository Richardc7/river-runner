# Build and migration status

## Verification gates

Run from a complete checkout, with JDK 17 and Android SDK platform 35 installed:

```
./gradlew :engine:test :app:testDebugUnitTest :app:assembleDebug
./gradlew :engine:strategyBenchmark -PbenchmarkBlocks=8 -PbenchmarkHands=100
```

The Actions workflow runs these test/build gates before uploading the debug APK. At implementation time no workflow run was observed in this fork. The editing container has Kotlin/Java but no Android SDK or downloadable Gradle dependencies. Therefore no Android APK or passing full Gradle build is claimed here.

`python3 tools/offline_engine_tests.py` is a secondary plain-Kotlin assertion runner. It makes temporary copies, removes JUnit annotations and invokes tests with kotlin-test.jar. It does not validate Android dependencies, Compose rendering, Gradle configuration or device behavior. The implementation environment ran the newly added engine test suite, not a full Android build.

## Package identity

The fork uses `com.riverrunner.exploit`, retaining the source namespace `com.riverrunner`. This allows side-by-side installation instead of attempting an update signed with the upstream developer's private key. Existing upstream app data is not automatically accessible from the new app sandbox. H1 format compatibility does not mean automatic cross-app migration. Do not uninstall the old app before separately preserving any accessible backup.

Debug signing is for testing. Hosted-runner debug keys can vary between builds; a stable production upgrade path requires the owner's private release keystore. No key is checked into source, generated as a published asset or exposed in logs. Release signing uses the existing RR_* environment variables or private Gradle properties.

## Data compatibility

Existing fork H1 files and save-slot records remain readable. Missing/corrupt/newer strategy sidecars are ignored without deleting the hand. Current session opponent profiles are deliberately not restored by seat number or name. New preference fields use safe defaults.

## Exploratory simulation

Paired decks and common policy-sampling random numbers isolate strategy changes. Standard error is estimated across independent seed blocks, not falsely treating correlated hands as independent. Small benchmarks are smoke tests and not evidence of real-world profitability. The stored sample report is deliberately not used as a release pass/fail profit threshold.
