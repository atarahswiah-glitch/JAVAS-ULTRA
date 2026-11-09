# Native Android Foundation

**Status:** Initial source skeleton authored; cloud build has not yet run.

## Current technical baseline

- Native Kotlin Android application with Jetpack Compose and Material 3.
- Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.3.21 and matching Compose compiler plugin.
- JDK 17 for the Gradle process and Java/Kotlin bytecode target.
- Compile/target SDK 36; minimum SDK 26 (Android 8.0).
- Provisional application ID and namespace: `com.javasultra.app`.
- Initial app shell deliberately contains no authentication, backend credentials, private memory, sensor permissions or external-action capability.

## Package identity warning

`com.javasultra.app` is provisional for the first build and must be confirmed before any public store release or distribution that needs a stable upgrade identity. Changing application ID after distribution creates a different Android app and does not upgrade existing installations.

## Build pipeline

The GitHub Actions workflow installs JDK 17, Gradle 8.13 and Android SDK platform/build tools, generates a Gradle wrapper in the clean CI workspace, runs unit tests, assembles a debug APK and uploads the APK/reports as workflow artefacts.

The wrapper is generated inside CI because the GitHub text-file API cannot safely create the required binary wrapper JAR. This gives the CI job a reproducible wrapper invocation without committing an unverified binary. A verified wrapper distribution must be added to the repository before local clone builds are considered complete.

## First verification gate

1. Run the workflow on the feature branch.
2. Fix actual dependency or SDK failures based on CI logs.
3. Inspect the generated APK and test reports.
4. Add instrumentation/device tests after a clean compile is established.
5. Only then claim the Android foundation is build-verified.

## Security constraints

- No Supabase project URL/key or service-role credential is embedded in the app.
- No user memory or personal data is collected by this shell.
- No microphone, camera, location, contacts or background permissions are requested yet.
- Debug APK is for technical verification only; it is not a release build.