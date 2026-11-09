# Native Android Foundation

**Status:** Native Android source skeleton and initial security-domain modules authored. Latest branch head passed full CI on 2026-11-09; this remains a foundation build, not a production companion.

## Current technical baseline

- Native Kotlin Android application with Jetpack Compose and Material 3.
- Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.3.21 and matching Compose compiler plugin.
- JDK 17 for the Gradle process and Java/Kotlin bytecode target.
- Compile/target SDK 36; minimum SDK 26 (Android 8.0).
- Provisional application ID and namespace: `com.javasultra.app`.
- Initial app shell deliberately contains no production authentication, backend credentials, private memory, sensor permissions or external-action capability.
- A local Kotlin security-domain module now models session/device preflight, capability and resource-scope checks, approval binding, protected-action states and audit-event structure. It is not connected to a trusted server and cannot authorise real remote side effects.

## Package identity warning

`com.javasultra.app` is provisional for the first build and must be confirmed before any public store release or distribution that needs a stable upgrade identity. Changing application ID after distribution creates a different Android app and does not upgrade existing installations.

## Build pipeline

The GitHub Actions workflow installs JDK 17, Gradle 8.13 and Android SDK platform/build tools, generates a Gradle wrapper in the clean CI workspace, runs unit tests, assembles a debug APK and uploads the APK/reports and generated wrapper files as workflow artefacts.

The wrapper is currently generated inside CI. Latest run uploaded `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and `gradle/wrapper/gradle-wrapper.properties`; these four files are verified in the artifact but are not yet committed to the repository. A clean local clone therefore still does not have the wrapper until those files are committed.

## Latest full verification — evidence

CI run `42588010169` completed successfully on branch head `464b8fc6c0806374915990af45bd05c0c3f1ee4c`.

- JDK 17, Gradle 8.13 and Android SDK setup passed.
- Gradle wrapper generation and configuration verification passed.
- `testDebugUnitTest` passed: 20 tests total (15 capability-authorisation tests, 4 protected-action policy tests and 1 foundation test), with zero failures, zero errors and zero skipped tests. Counts were checked in the three JUnit XML reports.
- `assembleDebug` passed and produced `app-debug.apk` (11,732,010 bytes).
- Artifact `JAVAS-ULTRA-debug-and-test-reports` (ID `13660595352`) includes the APK, XML/HTML reports and all four generated Gradle wrapper files.
- APK SHA-256: `cdcab5990c765cc9a0d419c0cbed13559977a88ee298e2ee3d3767f6d40e00cf`.
- The wrapper files are verified in the artifact, but are not yet committed to the branch.

## Earlier verification history — evidence

CI run `42587189930` completed successfully on commit `f16f7df9a07424fcdf7eba1d3ade981449342aa8`.

- JDK 17, Gradle 8.13 and Android SDK setup passed.
- Gradle wrapper generation and `./gradlew --version` passed.
- `testDebugUnitTest` passed: 10 tests total (9 capability/session/approval domain tests plus 1 foundation identity test), with zero failures/errors.
- `assembleDebug` passed and produced `app-debug.apk` (approximately 11.7 MB).
- Workflow artifact `JAVAS-ULTRA-debug-and-test-reports` contains the APK and XML/HTML test reports.

**Important revision boundary:** this run predates the later audit-event/action-transition models, expanded denial tests and revoked-grant classification fix. It verifies the Android scaffold and the earlier security-domain revision, not the latest branch head. The latest branch must pass a fresh CI run before those later changes are considered verified.

## Remaining verification gate

1. Commit the exact verified wrapper files from artifact `13660595352` to the feature branch, preserving executable mode on `gradlew`.
2. Run CI again from the clean checkout with the committed wrapper and confirm all tests and APK assembly pass.
3. Add instrumentation/device tests after the reproducible wrapper build is established.
4. Perform real-device installation and permission/recovery tests.
5. Only then close the Android foundation verification gate.

## Security constraints

- No Supabase project URL/key or service-role credential is embedded in the app.
- No user memory or personal data is collected by this shell.
- No microphone, camera, location, contacts or background permissions are requested yet.
- Debug APK is for technical verification only; it is not a release build.