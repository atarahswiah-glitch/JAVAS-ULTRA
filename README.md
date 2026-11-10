# JAVAS ULTRA

**Persistent Personal AI Companion and Intelligent Agent**

JAVAS ULTRA is being developed as a secure, memory-enabled, practical AI companion with a consistent identity, useful reasoning, controlled tool use and continuity across supported devices.

## Project status

- Current work: architecture and security foundations, encrypted local memory and learning-journal modules, provider-neutral model/tool contracts, and native Android build delivery.
- The Android UI is still a foundation screen; domain modules are not yet integrated into a complete interactive assistant.
- GitHub Actions run #82 on commit 497e213104fceb8adaf9641d1dd90fa0515a6da8 passed all 117 JVM unit tests, assembled the debug APK and uploaded the APK plus test reports. Artifact: JAVAS-ULTRA-debug-and-test-reports (11,474,739 bytes; expires 2026-11-23). APK SHA-256: 60d7391bb884b8c792ebeb89234d488d92844fed8037f36c375a7fc6869f2187. A later public-research-gateway hardening change is undergoing its own CI verification.
- A Codemagic workflow is committed at the repository root for the Creator's existing account. The Codemagic app connection and first build still require confirmation in the Codemagic dashboard.
- No production authentication, live model-provider connection, autonomous web research, dedicated JAVAS backend or signed release is claimed.
- Draft PR #1 remains open and unmerged.

## Start here

- [Master architecture](docs/MASTER-ARCHITECTURE.md)
- [Integrated ten-phase roadmap](docs/ROADMAP.md)
- [Requirements register](docs/REQUIREMENTS-REGISTER.md)
- [Security and privacy baseline](docs/SECURITY-AND-PRIVACY-BASELINE.md)
- [Phase 2 identity and security contracts](docs/PHASE-2-IDENTITY-SECURITY-CONTRACTS.md)

## Build and delivery

- Preferred cloud Android build: existing Codemagic account, configured by [codemagic.yaml](codemagic.yaml).
- Independent verification: [GitHub Actions Android CI](.github/workflows/android-ci.yml).
- Debug builds are unsigned. Release signing and store publishing remain disabled pending Creator approval and a secure signing-key custody plan.

## Development principles

- Native Android is the first client direction.
- Preserve identity, approved memory and user control across upgrades and migration.
- Keep planning separate from permission to execute actions.
- Treat external content and tool output as untrusted input.
- Require evidence before marking work implemented or verified.
- Keep credentials, private memory and production data out of source control.

## Scope exclusions

Brain-computer interfaces, neural sensing, mind uploading, consciousness transfer and merging the Creator with JAVAS are explicitly outside the current implementation scope. They require separate future authorisation.

## Contributions and changes

Use focused branches and reviewable pull requests. Material changes to identity, permissions, security boundaries, memory lifecycle or deployment authority require explicit review.

## Current product status

JAVAS ULTRA is currently a native Android foundation build, not yet a functional AI companion. The launcher screen is a foundation message. Identity, memory, learning, research, model routing and tool permission modules exist as local domain/storage foundations, but are not wired into a usable conversation or controls on the phone. There is no active model connection, production authentication backend, live research transport, scheduled learning worker or enabled device-action tool. The next milestone is Creator identity/session setup followed by a real encrypted memory manager and conversation UI.
