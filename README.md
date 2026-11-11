# JAVAS ULTRA

**Persistent Personal AI Companion and Intelligent Agent**

JAVAS ULTRA is being developed as a secure, memory-enabled, practical AI companion with a consistent identity, useful reasoning, controlled tool use and continuity across supported devices.

## Project status

- Current work: architecture and security foundations, encrypted local memory and learning-journal modules, provider-neutral model/tool contracts, and native Android build delivery.
- The Android UI is still a foundation screen; domain modules are not yet integrated into a complete interactive assistant.
- The latest checked GitHub Actions runs for draft PR #5, #6 and #7 completed successfully. JVM tests passed on the code-bearing branches, Android instrumentation test APKs compiled, and debug APKs were assembled. Instrumentation tests have not yet been executed on a physical device or emulator.
- A dedicated JAVAS-only Supabase project has been provisioned and verified healthy. It is currently empty: no public tables, migrations or Edge Functions. The Android app is not connected to it; no production Auth, RLS policies or backend APIs are implemented.
- A Codemagic workflow is committed at the repository root for the Creator's existing account. The Codemagic app connection and first build still require confirmation in the Codemagic dashboard.
- No production authentication, live model-provider connection, autonomous web research, production backend integration or signed release is claimed.
- Draft PRs remain unmerged.

## Start here

- [Master architecture](docs/MASTER-ARCHITECTURE.md)
- [Integrated implementation blueprint](docs/INTEGRATED-IMPLEMENTATION-BLUEPRINT.md)
- [Integrated ten-phase roadmap](docs/ROADMAP.md)
- [Requirements register](docs/REQUIREMENTS-REGISTER.md)
- [Self-directed learning and capability environment](docs/SELF-DIRECTED-LEARNING-ENVIRONMENT.md)
- [Security and privacy baseline](docs/SECURITY-AND-PRIVACY-BASELINE.md)
- [Phase 2 identity and security contracts](docs/PHASE-2-IDENTITY-SECURITY-CONTRACTS.md)
- [Dedicated JAVAS backend decision](docs/decisions/ADR-0002-BACKEND-ISOLATION.md)

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

JAVAS ULTRA is currently a native Android foundation build, not yet a functional AI companion. The launcher has a compact companion globe and local voice/image preference references, but no live conversation runtime. Identity, memory, learning, research, model routing and tool permission modules exist as local domain/storage foundations, but are not wired into a usable conversation or controls on the phone. There is no active model connection, production authentication backend, live research transport, scheduled learning worker, or enabled device-action tool. The next milestone is to implement and verify the dedicated backend's identity/session boundary, then connect it to the Android app and build a usable memory/conversation experience.
