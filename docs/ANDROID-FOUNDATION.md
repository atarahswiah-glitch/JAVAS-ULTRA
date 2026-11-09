# Native Android Foundation

**Status:** Initial source skeleton and first capability-security domain module authored; the initial cloud build passed on an earlier branch commit. Later security changes still require a fresh full CI run.

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

The GitHub Actions workflow installs JDK 17, Gradle 8.13 and Android SDK platform/build tools, generates a Gradle wrapper in the clean CI workspace, runs unit tests, assembles a debug APK and uploads the APK/reports as workflow artefacts.

The wrapper is generated inside CI because the GitHub text-file API cannot safely create the required binary wrapper JAR. This gives the CI job a reproducible wrapper invocation without committing an unverified binary. A verified wrapper distribution must be added to the repository before local clone builds are considered complete.

## First verification gate — evidence

CI run `42587189930` completed successfully on commit `f16f7df9a07424fcdf7eba1d3ade981449342aa8`.

- JDK 17, Gradle 8.13 and Android SDK setup passed.
- Gradle wrapper generation and `./gradlew --version` passed.
- `testDebugUnitTest` passed: 10 tests total (9 capability/session/approval domain tests plus 1 foundation identity test), with zero failures/errors.
- `assembleDebug` passed and produced `app-debug.apk` (approximately 11.7 MB).
- Workflow artifact `JAVAS-ULTRA-debug-and-test-reports` contains the APK and XML/HTML test reports.

**Important revision boundary:** this run predates the later audit-event/action-transition models, expanded denial tests and revoked-grant classification fix. It verifies the Android scaffold and the earlier security-domain revision, not the latest branch head. The latest branch must pass a fresh CI run before those later changes are considered verified.

## Remaining verification gate

1. Trigger a fresh CI run on the latest feature-branch head.
2. Confirm all current JVM tests pass and the latest debug APK assembles.
3. Add instrumentation/device tests after the latest branch has a clean build.
4. Perform real-device installation and permission/recovery tests.
5. Only then claim the current Android/security foundation is fully build-verified.

## Security constraints

- No Supabase project URL/key or service-role credential is embedded in the app.
- No user memory or personal data is collected by this shell.
- No microphone, camera, location, contacts or background permissions are requested yet.
- Debug APK is for technical verification only; it is not a release build.