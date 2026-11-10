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
