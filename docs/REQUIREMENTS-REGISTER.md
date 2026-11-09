# JAVAS ULTRA — Requirements Register

This is the top-level traceability register. Detailed acceptance tests will be added alongside each implementation module. Status values: **Planned**, **In progress**, **Implemented**, **Verified**, **Blocked**. At this baseline, no application capability is claimed as implemented.

| ID | Requirement | Phase | Minimum acceptance evidence | Initial status |
|---|---|---:|---|---|
| ID-01 | Stable JAVAS identity and Creator relationship across sessions | 2, 5, 9 | Identity/session continuity tests | Planned |
| ID-02 | Creator authentication and secure account recovery | 2 | Auth, recovery and revocation tests | Planned |
| ID-03 | Role/scope-based permissions and least privilege | 2 | Positive and negative authorisation tests | Planned |
| ID-04 | Voice/wake phrase is not sufficient for sensitive authentication | 2, 7 | Step-up authentication tests | Planned |
| SEC-01 | Untrusted content cannot override system policy or grant permissions | 2, 4, 8 | Prompt-injection/tool-boundary tests | Planned |
| SEC-02 | Secrets excluded from source, logs and client bundles | 2, 7, 8 | Secret scanning and release inspection | Planned |
| SEC-03 | Protected actions are audited with actor, scope, target and outcome | 2 | Audit completeness and tamper-resistance tests | Planned |
| SEC-04 | Secure device/session revocation and recovery | 2, 9 | Lost-device/recovery exercise | Planned |
| MEM-01 | Separate session, preference, episodic, semantic, project and sensitive memory | 3 | Data model and retrieval tests | Planned |
| MEM-02 | Durable memory includes provenance, confidence and timestamps | 3 | Provenance validation tests | Planned |
| MEM-03 | Creator can inspect, correct, export and delete eligible memory | 3 | Lifecycle and access-control tests | Planned |
| MEM-04 | Learning verifies important claims and records uncertainty | 3 | Source evaluation and contradiction tests | Planned |
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
| AND-01 | First client is a native Android application | 7 | Reproducible Android build and install test | Planned |
| AND-02 | Voice, wake phrase, notifications and visual presence degrade gracefully | 7 | Device matrix and permission tests | Planned |
| ACT-01 | Tool adapters declare scopes, side effects and rollback behaviour | 4, 8 | Adapter contract tests | Planned |
| ACT-02 | High-impact actions require explicit approval | 2, 8 | Approval-gate tests | Planned |
| DEV-01 | Generated code runs in an isolated workspace and test pipeline | 8 | Sandbox and CI evidence | Planned |
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

## Status policy

- **Planned:** requirement accepted but implementation not started.
- **In progress:** implementation underway with linked branch/PR.
- **Implemented:** code/configuration exists, but acceptance evidence is incomplete.
- **Verified:** required tests and review evidence passed.
- **Blocked:** dependency, access, policy or technical constraint prevents progress.

Do not upgrade status based on design notes alone.


## Phase 2 contract reference

The initial implementation contract is documented in [Phase 2 — Identity, Trust and Security Contracts](PHASE-2-IDENTITY-SECURITY-CONTRACTS.md). It specifies identity/device/session records, capability-based authorisation, approval-bound action lifecycle, audit requirements, recovery controls and a negative security test matrix.

**Status remains Planned for all application requirements.** The contract is design documentation only; it does not change any requirement to Implemented or Verified.