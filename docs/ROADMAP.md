# JAVAS ULTRA — Integrated Execution Roadmap

**Execution rule:** Work continuously through compatible workstreams. Do not stop after every small feature or ask the Creator to choose the next task when the approved sequence already determines it. Pause only for a genuine dependency, a protected decision, unavailable access or device-only test.

## Work classification

### Remote-capable
Repository inspection and documentation; architecture and requirements traceability; code review; branch/PR management; implementation that can be validated in the available environment; CI configuration; database schema/migration design; security policy design; tests and static analysis; release documentation; dependency/licence review.

### Device- or Creator-dependent
OAuth authorisation; device permissions; physical sensor testing; wake-word/microphone behaviour; battery/thermal/performance tests; biometric enrolment; notification delivery on the actual phone; offline/network-transition tests; restore onto a second device; approval of paid services, production credentials, public releases or protected security changes.

## Ten integrated phases

### Phase 1 — Inspection and master architecture
- Inspect the repository, branch history, files, connected services and available build environment.
- Establish architecture, scope exclusions, requirements register, risk register and decision log.
- Verify device and service constraints; select a minimal build/CI path.
- Exit: repository baseline and architecture are documented; unknowns and blockers are explicit.

### Phase 2 — Identity, security and central foundation
Combine identity, Creator authentication, session lifecycle, permissions, policy enforcement, audit events, secure configuration and recovery.
- Define threat model and trust boundaries.
- Implement authentication/authorisation contracts and least-privilege access.
- Add audit and device/session revocation foundations.
- Exit: security tests demonstrate protected operations are denied without correct authority.

### Phase 3 — Memory, knowledge and learning
Combine persistent memory, knowledge records, research provenance, retrieval, correction, export/deletion and learning journal.
- Separate episodic, semantic, preference, project and sensitive memory.
- Add source provenance, confidence, retention and contradiction handling.
- Exit: memory lifecycle tests cover access control, provenance, correction and recovery.

### Phase 4 — Reasoning, strategy and self-knowledge
Combine hierarchical reasoning, task planning, strategy, simulations, architecture registry, capability tracking and uncertainty reporting.
- Plans must include constraints, dependencies, risks and verification.
- Keep planning separate from action authority.
- Exit: representative plans are traceable and cannot bypass permissions.

### Phase 5 — Personality, emotions and communication
Combine personality profile, Creator preferences, functional emotional state, dialogue policy, voice style and visual expression.
- Ensure emotional integrity and anti-manipulation controls.
- Keep engineered emotional state distinct from claims of subjective feeling.
- Exit: state changes produce consistent and safe communication behaviour.

### Phase 6 — Private communication and sensory intelligence
Combine agreed vocabulary, Morse/tap conventions, audio/visual/location adapters and environmental privacy awareness.
- Sensor access remains permission-gated and visible.
- Codes are not encryption or authentication.
- Exit: permission, consent, privacy and uncertainty tests pass.

### Phase 7 — Android application and device integration
Combine native Android shell, identity/session UI, voice interaction, optional wake phrase, notifications, visual presence and supported device actions.
- Implement graceful degradation when permissions/network are unavailable.
- Exit: installable debug build and documented real-device test results.

### Phase 8 — Self-development and autonomous operation
Combine sandboxed code generation, test automation, task scheduling, resource limits, approval gates, signed deployment and rollback.
- No self-modification of trust root or safeguards.
- Exit: simulated change completes the full gated pipeline and rollback is demonstrated.

### Phase 9 — Central core, offline operation and migration
Combine central services, encrypted local cache, sync queue, conflict handling, multi-device continuity, backups and migration.
- Use idempotent sync and explicit pending/confirmed states.
- Exit: offline/online and restore/migration tests pass.

### Phase 10 — Companionship, hardening and release
Combine companion experiences, stories/teaching, usability, security review, performance optimisation, recovery exercises and release operations.
- Exit: release checklist, known limitations, support/maintenance and rollback plan are approved.

## Parallelism and integration rules

- Workstreams may proceed in parallel only when their interfaces and dependencies are stable.
- Shared contracts are defined before parallel implementation.
- Integrate compatible work in a single coherent branch/PR where review remains manageable.
- Keep independent security-sensitive or high-risk changes isolated for review.
- Avoid giant unreviewable commits; group by coherent deliverable, not by arbitrary tiny batches.
- Do not merge directly to the protected/default branch unless the Creator has approved that workflow.
- Preserve existing functionality; include migration and rollback when changing persisted data or public interfaces.

## Reporting format

At each meaningful checkpoint report:
1. Workstream and phase.
2. Changes actually made and exact repository locations.
3. Tests actually run and results.
4. Unverified items and blockers.
5. Creator/device actions needed, grouped into one concise checklist.
6. Next integrated workstream already determined by this roadmap.

Never report a task as complete merely because its design or code draft exists.


## Latest verified delivery checkpoint — 2026-11-09

- The clean-checkout GitHub Actions pipeline now passes JDK 17/Gradle 8.13 setup, wrapper generation and configuration validation, all 117 JVM unit tests, debug APK assembly and artifact upload on commit 497e213104fceb8adaf9641d1dd90fa0515a6da8.
- The debug artifact was downloaded and inspected: APK ZIP integrity is valid, manifest and DEX entries are present, and test XML reports 117/117 passing.
- The APK remains unsigned and has not undergone physical-device installation, permission, offline, thermal or battery testing.
- Public research remains disabled at runtime. The gateway is a preflight contract only; safe transport still needs redirect/DNS/IP validation, bounded HTTP handling, content extraction controls and tests.


## Current capability truth — 2026-11-09

The app's current launcher (`MainActivity.kt`) is a placeholder foundation screen. The following are code foundations, not end-user capabilities: Creator identity/session preflight; capability/approval policy; encrypted local memory repository; encrypted learning journal; bounded learning planner; research evidence and public-target preflight; model/tool adapter contracts. No user-facing memory management, conversation/model connection, safe research transport, background learning worker, tool execution host, production authentication or device integration is active.

### Next vertical slice — Creator identity and usable memory

1. Implement account bootstrap and secure session persistence/revocation; do not invent an unauthenticated shared principal for memory ownership.
2. Connect the Creator identity gate and capability checks to the actual application session boundary.
3. Build memory-management screens and repository operations: propose, inspect, activate, retrieve, correct/supersede, export and delete, with audit-safe provenance and lifecycle handling.
4. Add Android instrumentation tests for Keystore encryption, database persistence, process restart, owner isolation, deletion and key invalidation/recovery behaviour.
5. Only after that, connect conversation context assembly to approved memory retrieval.

### Latest build verification — 2026-11-09

- CI run #100 (`42626623452`) completed successfully on commit `ea1a2f55db31723675fc1620414a98d9d21b786b`.
- All 122 JVM unit tests across 16 suites passed (0 failures, 0 errors, 0 skipped).
- Debug APK assembly and artifact upload passed. The downloaded artifact was inspected: APK archive integrity is valid; Android manifest and DEX entries are present. APK SHA-256: `ef727bde1d8c763bf7a541aa703143e8da0c98373d406a1e63cf8e2bae7a65e4`.
- The APK is unsigned and has not been installed or tested on a physical device. CI tests do not establish production authentication, runtime model connectivity or real device behaviour.


## Creator's learning-environment direction — 2026-11-10

The Creator's clarified product goal is a self-directed learner, not a manually pre-programmed catalogue of answers and skills. JAVAS should use its existing reasoning core to inspect its own capabilities, identify knowledge/skill gaps, discover suitable resources, learn, practise safely, evaluate competence, use authorized tools, selectively retain useful information and improve through controlled changes.

The implementation contract is documented in [Self-Directed Learning and Capability Environment](SELF-DIRECTED-LEARNING-ENVIRONMENT.md). It is cross-cutting across Phases 2–9, not a new parallel product or a replacement roadmap.

Integration priorities:
1. Establish an evidence-backed self-model and capability/resource registry.
2. Complete Creator identity, memory lifecycle and selective-retention controls.
3. Connect research and learning to real permitted resources.
4. Add safe practice environments and skill-evaluation records.
5. Connect planning to scoped, authorized execution and result verification.
6. Add isolated capability improvement with regression checks, approval and rollback.

The next vertical slice should demonstrate one complete learning cycle end-to-end: inspect current capability → identify a gap → discover a resource → learn → practise in a sandbox → pass an evaluation → propose or use an authorized capability → selectively retain useful knowledge → report evidence and limitations. Do not build a broad tool catalogue before this vertical slice works.


## Build-ahead increment — public research transport (not yet verified)

On branch `feature/secure-public-research-transport`:
- Added an OkHttp HTTPS GET transport with custom public-address DNS filtering, manual same-origin redirects, bounded robots.txt checks, response limits, and cancellable network requests.
- Added orchestration connecting injected public-source discovery, page retrieval and injected evidence-claim extraction to the existing learning-provider contract.
- Added JVM tests for public IP classification, robots rules, discovery-domain validation, duplicate candidates, source budgets and coordinator timeouts.
- Added the Android INTERNET permission and documented the transport boundary.

Not yet complete:
- This branch has not been built or tested by CI.
- No real search/discovery adapter or approved model-based claim extractor is configured.
- No trusted server-side egress proxy is implemented.
- The transport is not wired into a user-facing learning screen, scheduler or active worker.
- No Android installation or device testing has been performed.

Next integrated work remains Creator identity/session and memory-management UI, followed by approved discovery/extraction adapters and trusted backend enforcement. Device-only checks remain deferred until the Creator requests testing.


## Build-ahead increment — Creator session foundation (not yet verified)

On branch `feature/creator-session-foundation`:
- Added a versioned, bounded Creator session credential codec.
- Added an AES-GCM Android Keystore-backed session vault storing only ciphertext in private SharedPreferences.
- Added provider/account gateway contracts and session orchestration for sign-in, account/device checks, session restore, refresh-token rotation and sign-out.
- Added a random app-scoped device UUID provider; it is not a hardware identifier or cryptographic device attestation.
- Added JVM tests for credential encoding, identity/device gating, token refresh and local sign-out behaviour.
- Documented missing production provider, server-side device challenge, recovery/MFA, audit, and Android instrumentation dependencies.

Not yet complete:
- No production authentication/account gateway is connected.
- No UI or app-startup integration uses this coordinator yet.
- Device UUID registration is not proof of device possession.
- No tests/build/CI/device validation have been executed for this branch.
- No APK has been installed.

The next planned vertical slice is wiring the Creator session lifecycle into a safe application shell and then building the Creator-controlled memory-management interface. Production activation remains blocked on the trusted identity provider/backend and its security review.


## Build-ahead increment — evidence-based memory retention (not yet verified)

On branch `feature/evidence-based-memory-retention`:
- Replaced the three-signal retention heuristic with a seven-signal weighted score: recurrence, freshness, change likelihood, uniqueness, future usefulness, re-research cost and source confidence.
- Kept evidence gates independent of the score: validity, contradiction/dispute status, publication age, provenance completeness, verification status, independent publisher count and minimum source confidence.
- Explicit retention consent cannot bypass evidence gates. Unknown publication age allows only a source-reference proposal; stale evidence requires fresh research; contradictions/invalid evidence require Creator review.
- Updated the learning mapper to pass source confidence, independent publisher count, provenance and publication age into the shared policy. Missing publication dates remain unknown.
- Added focused JVM tests for score calculation and each evidence gate, including explicit-retention attempts to bypass gates.

Not yet complete:
- Tests have been written but not executed; no Gradle build or CI run has been started for this branch.
- This remains local policy and mapper logic, not trusted backend enforcement or a user-facing memory manager.
- Creator approval for a proposal remains separate from consent to retain or activate durable knowledge.


## Native application navigation shell — 2026-11-25

- Added a Jetpack Compose navigation shell with Chat, Learn, Memory and Profile destinations in `app/src/main/java/com/javasultra/app/MainActivity.kt`.
- Each destination reports the real integration state and explicitly avoids presenting disconnected services as active.
- Added `docs/ANDROID-APPLICATION-SHELL.md` and updated the README project status.
- This is a source implementation only. No Gradle build, JVM test, instrumentation test, CI run, APK installation or device test was performed for this milestone.
- Next: connect the Creator session flow to an approved authentication provider, then implement the protected conversation pipeline and functional memory manager. Do not enable a live provider or production backend without Creator approval.


## Creator identity and protected conversation slice — 2026-11-25

- Added `core/agent/ProtectedConversationOrchestrator.kt`: authenticated Creator preflight, bounded message/context, Creator-private model classification, approved-memory context interface, provider deadline and response validation.
- Added `core/agent/ProtectedConversationOrchestratorTest.kt` covering the core denial and safety paths. Test source has not been executed.
- Model responses now identify the selected provider; tool proposals are rejected by the text conversation path and are never executed.
- Static review corrected Creator session restore reassignment and made best-effort revocation safe when no refresh token exists.
- Added `docs/CREATOR-CONVERSATION-BOUNDARY.md`.
- No live model provider, production authentication, backend adapter or memory context adapter is configured. No build, test, CI, APK or device validation was run.
- Next: complete the real Creator authentication provider contract and secure application session lifecycle, then implement the Creator-approved memory manager and conversation UI integration. Provider credentials/backend activation remain protected decisions.


## Creator memory-to-conversation integration increment — 2026-11-25

- Added `EncryptedRepositoryConversationMemoryProvider.kt` to connect the protected conversation boundary to the encrypted local memory repository.
- The adapter scopes reads to the authenticated Creator principal, uses the repository's READ policy, excludes highly-sensitive records without step-up authentication, and filters disputed, low-confidence, expired, inactive, and unsupported memory kinds before ranking.
- Context is bounded to eight records, 2,000 characters per record, and 8,000 characters overall. Memory remains reference data, not instructions.
- Added JVM test source for owner scoping, lifecycle/verification/confidence filtering, bounded READ access and invalid input.
- Hardened sign-out so unreadable encrypted session state is still cleared locally; remote revocation is reported unconfirmed when the token cannot be recovered. Failed preflight now attempts to revoke both access and refresh tokens.
- Added a regression test for sign-out when the encrypted vault cannot be read.
- Corrected escaped newline corruption in the protected conversation Kotlin source found during static review.

Verification status:
- These changes were committed to `feature/native-companion-navigation-shell`.
- No Gradle build, JVM tests, instrumentation tests, CI run, APK build, installation or device test has been performed in this increment, in keeping with the instruction to defer testing.
- Production authentication, a trusted backend, real model provider credentials, session-to-conversation authorization issuance, and Compose chat/memory UI wiring remain unconnected. The app must not be described as a functioning live companion yet.
- Next integrated work: add a trusted application session boundary that issues short-lived conversation authorization only from verified coordinator results; then connect the actual Chat and Memory screens through injected app dependencies. Do not fabricate provider credentials or activate backend services.
