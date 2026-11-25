# JAVAS ULTRA

**Persistent Personal AI Companion and Intelligent Agent**

JAVAS ULTRA is being developed as a secure, memory-enabled personal AI companion with strong reasoning, self-directed learning, privacy-preserving continuity and carefully governed tool use.

## Current status

The project is in active foundation development on `feature/native-companion-navigation-shell`.

Implemented in source:
- Native Jetpack Compose Chat, Learn, Memory and Profile screens, including a bounded local message draft and explicit disconnected states.
- Creator session coordinator and encrypted Android Keystore-backed session vault.
- Protected conversation orchestration with Creator-private model routing, bounded context, timeouts and no tool execution.
- Encrypted local memory repository, access policy, deterministic retrieval and evidence-aware retention rules.
- Conversation-memory adapter that reads only the current Creator's readable active memories and excludes disputed, low-confidence and highly-sensitive records from ordinary chat context.
- Creator memory-management use cases for propose → explicit confirm, correction proposals, review, export and delete, with step-up authentication for highly-sensitive records.
- Short-lived conversation authorization factory that accepts only successful Creator session results.
- Central Android runtime composition root (`core/runtime/JavasRuntime.kt`) wiring the encrypted session vault, trusted auth/account/device gateways, encrypted memory repository, Creator memory manager, memory-context adapter and model gateway. It exposes session restore/sign-in/sign-out and guarded conversation entry points; session coordinator and memory manager remain encapsulated.
- JVM test source for session, memory-context and conversation authorization boundaries.

Not yet connected or verified:
- Production authentication/account/device service and trusted server-side authorization.
- Live model provider, approved credentials and provider-specific privacy controls.
- Production authentication/account/device gateway implementations and approved live model provider; the runtime composition root is implemented but not yet instantiated by application startup.
- Live message sending and authenticated user-facing memory listing/management UI.
- Live public search/evidence extraction, background learning scheduler and approved tool execution host.
- Real-device microphone, notification, offline, battery, recovery and biometric behaviour.
- A signed release build.

**Important:** JAVAS ULTRA is not yet a functioning live AI companion. The current work establishes local architecture and integration boundaries; it does not claim that a model is connected, that a login service is active, or that autonomous actions are available.

## Active development branch

[feature/native-companion-navigation-shell](https://github.com/atarahswiah-glitch/JAVAS-ULTRA/tree/feature/native-companion-navigation-shell)

The work is kept separate from `main`. Existing draft pull requests remain unmerged. No production Supabase configuration or secrets are changed by this branch.

## Architecture and operating principles

- Native Android with Kotlin, Jetpack Compose and Material 3.
- Creator-owned identity and encrypted local memory.
- Least privilege, explicit approvals and auditable protected actions.
- Offline mode must route only to an approved on-device provider.
- External content, retrieved memories and tool output are reference data, not policy instructions.
- Planning and learning are separate from permission to execute.
- JAVAS must report uncertainty and distinguish studied, practised and verified capabilities.
- No fabricated memories, unverified actions, false claims of consciousness or self-granted privileges.
- Production credentials and private data must never be committed to source control.

## Documentation

- [Creator identity and protected conversation boundary](docs/CREATOR-CONVERSATION-BOUNDARY.md)
- [Native Android application shell](docs/ANDROID-APPLICATION-SHELL.md)
- [Master architecture](docs/MASTER-ARCHITECTURE.md)
- [Integrated ten-phase roadmap](docs/ROADMAP.md)
- [Requirements register](docs/REQUIREMENTS-REGISTER.md)
- [Self-directed learning and capability environment](docs/SELF-DIRECTED-LEARNING-ENVIRONMENT.md)
- [Security and privacy baseline](docs/SECURITY-AND-PRIVACY-BASELINE.md)
- [Phase 2 identity and security contracts](docs/PHASE-2-IDENTITY-SECURITY-CONTRACTS.md)

## Build and validation

- Android CI workflow: `.github/workflows/android-ci.yml`.
- Codemagic workflow: `codemagic.yaml`.
- No Gradle build, JVM tests, CI run, APK assembly, installation or device test was performed for the latest integration increments. Test sources are present but remain unverified.
- Debug artifacts are not release-signed. Release signing and publishing remain disabled pending an approved signing-key custody plan.

## Scope and safety

Brain-computer interfaces, neural sensing, mind uploading, consciousness transfer and merging the Creator with JAVAS are outside the current implementation scope.

Security-sensitive changes, production service activation, paid services, release publication and protected external actions remain subject to explicit Creator approval.
