# ADR-0001: Provisional Native Android Build Stack

- **Status:** GitHub Actions validation is active; Codemagic is the Creator's preferred cloud Android build and artifact-delivery service, with a repository workflow configuration now committed. Neither provider's latest run should be treated as a release approval.
- **Date:** 2026-11-09
- **Decision owners:** Creator approval required before production adoption.

## Context

JAVAS ULTRA's first client is a native Android application. The project must avoid dependence on an unreliable phone-only JDK/Gradle environment and should support reproducible cloud builds. Dependency versions must be selected as a compatible set, not independently.

## Proposed baseline

| Component | Candidate |
|---|---|
| Language | Kotlin 2.3.21 |
| Android Gradle Plugin | 8.13.2 |
| Gradle Wrapper | 8.13 |
| JDK for Gradle | 17 |
| UI | Jetpack Compose + Material 3 |
| Compose compiler plugin | Kotlin Compose plugin matching Kotlin 2.3.21 |
| Initial build pipeline | GitHub Actions workflow, unsigned debug build first |
| Version control | GitHub private repository preferred |

This is a compatibility candidate, not yet a verified working build. Kotlin's published compatibility table lists Kotlin Gradle Plugin 2.3.20–2.3.21 as compatible with Gradle 7.6.3–9.3.0 and AGP 8.2.2–9.0.0. Android's AGP table lists Gradle 8.13 as the minimum for AGP 8.13. The selected versions therefore intersect on paper. A clean CI build is still required.

## Build strategy

1. Create the smallest native Android application skeleton after the project package ID and app identity are confirmed.
2. Add the Gradle wrapper and pinned plugin/dependency versions.
3. Use the committed GitHub Actions workflow at `.github/workflows/android-ci.yml` for the first reproducible build.
4. First pipeline produces an unsigned debug APK and test reports only.
5. Add lint/unit tests and dependency/secret scanning.
6. Introduce release signing only after the Creator has approved the release identity and signing-key custody plan.
7. Keep signing keys and passwords in Codemagic secure variables/secure files, never in Git.

## Codemagic integration

The Creator already has a Codemagic account and wants it used for Android cloud builds. The root `codemagic.yaml` defines the unsigned debug workflow: JDK 17, Gradle 8.13 wrapper generation, JVM unit tests, APK assembly and test-report artifacts. The existing Codemagic app must be connected to `atarahswiah-glitch/JAVAS-ULTRA`, select the architecture branch, and accept the repository YAML. No Codemagic build has yet been confirmed from the account. GitHub Actions remains an independent PR verification path.

## Consequences

- The project can be built without relying on the Creator's phone as the primary compiler.\n- Codemagic is the preferred Android cloud build/artifact path; GitHub Actions supplies independent PR verification.
- Cloud builds require GitHub Actions to be enabled for the repository and the workflow to pass. Usage remains subject to GitHub Actions account/repository limits.
- A successful cloud build does not prove physical-device sensor, battery, biometric, offline or notification behaviour.
- The phone remains necessary for device acceptance tests.

## References

- Kotlin/Gradle/AGP compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- Android Gradle Plugin and Gradle compatibility: https://developer.android.com/build/releases/about-agp
- Codemagic native Android build: https://docs.codemagic.io/yaml-quick-start/building-a-native-android-app/
- Codemagic YAML location and setup: https://docs.codemagic.io/yaml-basic-configuration/yaml-getting-started/

## Revisit when

- A pinned dependency is unavailable or incompatible.
- CI build reveals a plugin/toolchain incompatibility.
- The minimum supported Android version or target SDK changes.
- Cost/plan limits make Codemagic unsuitable.
