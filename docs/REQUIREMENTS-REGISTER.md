# JAVAS ULTRA — Requirements Register

This is the top-level traceability register. Detailed acceptance tests will be added alongside each implementation module. Status values: **Planned**, **In progress**, **Implemented**, **Verified**, **Blocked**. At this baseline, no application capability is claimed as implemented.

| ID | Requirement | Phase | Minimum acceptance evidence | Initial status |
|---|---|---:|---|---|
| ID-01 | Stable JAVAS identity and Creator relationship across sessions | 2, 5, 9 | Identity/session continuity tests | In progress |
| ID-02 | Creator authentication and secure account recovery | 2 | Auth, recovery and revocation tests | Planned |
| ID-03 | Role/scope-based permissions and least privilege | 2 | Positive and negative authorisation tests | In progress |
| ID-04 | Voice/wake phrase is not sufficient for sensitive authentication | 2, 7 | Step-up authentication tests | In progress |
| SEC-01 | Untrusted content cannot override system policy or grant permissions | 2, 4, 8 | Prompt-injection/tool-boundary tests | Planned |
| SEC-02 | Secrets excluded from source, logs and client bundles | 2, 7, 8 | Secret scanning and release inspection | Planned |
| SEC-03 | Protected actions are audited with actor, scope, target and outcome | 2 | Audit completeness and tamper-resistance tests | Planned |
| SEC-04 | Secure device/session revocation and recovery | 2, 9 | Lost-device/recovery exercise | In progress |
| MEM-01 | Separate session, preference, episodic, semantic, project and sensitive memory | 3 | Data model and retrieval tests | In progress |
| MEM-02 | Durable memory includes provenance, confidence and timestamps | 3 | Provenance validation tests | In progress |
| MEM-03 | Creator can inspect, correct, export and delete eligible memory | 3 | Lifecycle and access-control tests | Planned |
| MEM-04 | Learning verifies important claims and records uncertainty | 3 | Source evaluation and contradiction tests | In progress |
| INT-01 | Reasoning distinguishes facts, assumptions and unknowns | 4 | Scenario-based evaluation | Planned |
| INT-02 | Plans include dependencies, risks, stop conditions and verification | 4 | Planner acceptance tests | Planned |
| INT-03 | Planning cannot independently authorise external actions | 2, 4 | Permission-boundary tests | Planned |
| INT-04 | Strategy/simulation outputs disclose assumptions and uncertainty | 4 | Scenario reproducibility tests | Planned |
| PER-01 | Functional emotional states consistently guide tone and expression | 5 | State transition and UI/voice tests | Planned |
| PER-02 | Emotional expression cannot manipulate, shame or isolate the Creator | 5 | Safety and dialogue evaluation | Planned |
| COM-01 | Agreed private vocabulary and communication conventions are preserved | 6 | Encoding/decoding tests | Planned |
| COM-02 | Morse/tap codes are not represented as cryptographic protection | 6 | Documentation and UX review | Planned |
| SEN-01 | Camera, microphone, location and sensors are permission-gated | 6, 7 | Permission denial/grant tests | Planned |
| SEN-02 | Sensor use and privacy uncertainty are communicated honestly | 6, 7 | Device and UX tests | Planned |
| AND-01 | First client is a native Android application | 7 | Reproducible Android build and install test | In progress |
| AND-02 | Voice, wake phrase, notifications and visual presence degrade gracefully | 7 | Device matrix and permission tests | Planned |
| ACT-01 | Tool adapters declare scopes, side effects and rollback behaviour | 4, 8 | Adapter contract tests | Planned |
| ACT-02 | High-impact actions require explicit approval | 2, 8 | Approval-gate tests | In progress |
| DEV-01 | Generated code runs in an isolated workspace and test pipeline | 8 | Sandbox and CI evidence | In progress |
| DEV-02 | Protected changes require approval, signed deployment and rollback | 8 | Release/rollback exercise | Planned |
| OFF-01 | Selected local identity and memory remain available offline | 9 | Airplane-mode acceptance test | Planned |
| OFF-02 | Sync is authenticated, idempotent and conflict-aware | 9 | Duplicate/reorder/conflict tests | Planned |
| OFF-03 | Pending remote actions are never shown as confirmed | 9 | Offline queue/UI tests | Planned |
| MIG-01 | Encrypted backups are integrity-checked and restore-tested | 9, 10 | Successful restore evidence | Planned |
| MIG-02 | Migration preserves approved identity, memory and audit continuity | 9 | Version migration test | Planned |
| EXP-01 | Companion experiences include personalised stories and spoken teaching | 10 | Usability and content review | Planned |
| OPS-01 | Monitoring, cost controls, error handling and maintenance are documented | 8, 10 | Operational readiness review | Planned |
| GOV-01 | Neural/BCI, mind-uploading and identity-merging ideas remain excluded from current build | All | Scope review on each phase | Verified as scope exclusion |
| GOV-02 | No feature is marked implemented/verified without evidence | All | PR and release checklist | Verified as process rule |


| LRN-01 | Discover and research knowledge gaps across open-ended subject domains, not a hard-coded subject catalogue | 3 | Research-task evaluation across unrelated domains | Planned |
| LRN-02 | Capture source provenance, evidence type, timestamp, confidence and claim-level support | 3 | Provenance and source-quality tests | In progress |
| LRN-03 | Corroborate claims, detect contradictions and revise outdated knowledge transparently | 3 | Contradiction, update and retraction tests | Planned |
| LRN-04 | Learn from permission-gated environmental observations and label their origin and uncertainty | 3, 6 | Sensor/source provenance and permission tests | Planned |
| LRN-05 | Maintain a Creator-protected self-model of modules, versions, dependencies, capabilities and limitations | 2, 4, 8 | Architecture-registry access and accuracy tests | In progress |
| LRN-06 | Distinguish knowledge updates from executable self-modification | 3, 8 | Policy-boundary tests | In progress |
| LRN-07 | Keep internal architecture and private Creator information restricted to authorised access | 2, 8 | Access-control and disclosure tests | Planned |
| LRN-08 | Study neuroscience, cognition and consciousness while distinguishing evidence from hypotheses and claims of subjective experience | 3, 4 | Scientific-source and uncertainty evaluation | Planned |
| CRE-01 | Maintain Creator goals, preferences and approved priorities without allowing untrusted sources to rewrite them | 2, 3, 4 | Priority integrity and injection tests | Planned |
| DEV-03 | Identify capability gaps, propose improvements, test in isolation and retain rollback evidence | 8 | End-to-end safe-improvement exercise | In progress |

## Status policy

- **Planned:** requirement accepted but implementation not started.
- **In progress:** implementation underway with linked branch/PR.
- **Implemented:** code/configuration exists, but acceptance evidence is incomplete.
- **Verified:** required tests and review evidence passed.
- **Blocked:** dependency, access, policy or technical constraint prevents progress.

Do not upgrade status based on design notes alone.


## Phase 2 implementation progress

- Added a pure Kotlin Creator identity gate that checks account state, session principal/device binding, session validity/revocation, verified device state, fresh authentication proof and multi-factor step-up for sensitive operations.
- The identity gate explicitly rejects local biometric unlock as sufficient proof for remote high-impact actions.
- Added 10 focused identity-gate test cases. These new tests are awaiting the next full Gradle CI result.
- Added a pure Kotlin capability-authorisation policy model with explicit session, device, capability and resource-scope checks.
- Added payload-bound, expiring, single-use approval matching logic.
- Added JVM tests for allow, unauthenticated access, unregistered devices, principal mismatch, scope mismatch, session expiry/revocation and approval binding/reuse.
- A local Kotlin compiler smoke check passed for the domain source and 12 representative assertions; it also exposed and led to correction of a revoked-grant classification bug.
- CI run `42588010169` passed 20 tests on commit `464b8fc6c0806374915990af45bd05c0c3f1ee4c` (15 capability-authorisation, 4 risk-policy and 1 foundation test). The new Creator identity gate adds 10 more test cases and is awaiting CI on the updated branch.
- These are local domain components and tests only. They are not server enforcement, authentication, secure credential storage or production approval execution. They remain unverified until the latest branch passes CI.

## Phase 2 contract reference

The initial implementation contract is documented in [Phase 2 — Identity, Trust and Security Contracts](PHASE-2-IDENTITY-SECURITY-CONTRACTS.md). It specifies identity/device/session records, capability-based authorisation, approval-bound action lifecycle, audit requirements, recovery controls and a negative security test matrix.

**Status remains evidence-based.** Local identity and authorisation code is In progress pending CI and trusted-server implementation. The contract is not production authentication or server enforcement.

## Combined Phase 3 and Phase 8 implementation progress

- Added typed memory records for session, preference, episodic, semantic, project and sensitive memory, including provenance, confidence, verification, sensitivity, lifecycle, expiry and supersession metadata.
- Added Creator-scoped memory access preflight and focused negative/positive tests. This is not yet encrypted durable persistence or trusted server-side enforcement.
- Added a domain-general research evidence model with source provenance, claim-level supporting/contradicting evidence, confidence bands and contradiction validation.
- Added bounded learning-task policy inputs to support open-ended subject research without hard-coding an encyclopaedia.
- Added a capability registry model for module status, version, dependencies, permissions, risk and known limitations.
- Added an isolated upgrade-workspace proposal policy. Feature changes require successful tests, static analysis, dependency review, secret scan, verified backup, Creator approval and rollback plan. Identity, permissions, trust root, audit and recovery are explicitly protected from self-modification.
- Added focused unit tests for research evidence, learning task bounds, capability/upgrade policy and memory privacy.
- All new modules remain local domain contracts until the current CI run verifies compilation and tests. No persistent learning service, research connector, sandbox runner, deployment engine, sensor access or cloud backend is yet enabled.
