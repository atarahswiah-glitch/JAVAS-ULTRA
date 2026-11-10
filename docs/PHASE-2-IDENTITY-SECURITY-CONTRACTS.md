# Phase 2 — Identity, Trust and Security Contracts

**Status:** Design contract for implementation; not yet implemented or security-certified.
**Depends on:** Phase 1 architecture baseline and Creator-approved service choices.

## 1. Purpose and boundaries

Phase 2 establishes one authoritative Creator identity, registered devices, session lifecycle, permission evaluation, protected-action approval and audit events. These contracts apply across Android, future clients, local services and any central API.

The Android client is not an authority. Hiding a button or checking a role locally is never sufficient to authorise an operation. Every server-backed protected operation must be authorised by a trusted server boundary. Offline operations are restricted to explicitly permitted local capabilities and remain pending when server confirmation is required.

## 2. Identity model

### 2.1 Core records

- CreatorAccount: stable internal identifier; authentication-provider subject; account state; creation and recovery metadata. Do not use email or phone as the immutable primary key.
- DeviceRegistration: stable device record; public-key/key-alias reference where supported; device label; platform/version; registration/revocation timestamps; last successful authentication. Avoid raw hardware identifiers unless necessary and consented.
- Session: opaque session identifier; account and device references; issued/expiry times; authentication strength; scopes; revocation state.
- CredentialReference: reference to provider-managed credentials or platform secure storage. Never store plaintext passwords, PINs, recovery codes or bearer tokens in ordinary tables/logs.
- PermissionGrant: principal, capability, resource scope, grantor, reason, created/expiry/revocation times and policy version.
- ApprovalRecord: requested operation, canonical payload digest, risk classification, approving authenticated principal, authentication strength, decision, time and expiry.
- AuditEvent: event ID, actor, device/session, action, target, result, timestamp, correlation ID, policy version and safe metadata. Exclude secrets and unnecessary personal content.

### 2.2 Authentication

- Use an established authentication provider; do not implement password cryptography or token formats from scratch.
- Support account recovery through provider-supported verified factors. Recovery must not silently weaken account security.
- Voice recognition, wake phrase, familiar device or private code words are convenience signals only, never sole proof for sensitive actions.
- Require step-up authentication for credential changes, device enrollment/recovery, permission grants, memory export/deletion, code deployment and other high-impact operations.
- Sessions must expire and be revocable. Lost-device recovery must revoke device sessions and credentials where supported.
- Avoid revealing whether an account exists during unauthenticated recovery flows.

## 3. Authorisation contract

Use deny-by-default, capability-based authorisation. A request is allowed only when all applicable checks pass:

1. Authenticated principal is valid and active.
2. Session is current and not revoked.
3. Device is registered when device binding is required.
4. Requested capability is explicitly granted.
5. Resource scope matches the target.
6. Grant is within its validity window and has not been revoked.
7. Risk policy and required approval are satisfied.
8. Request schema and resource state are valid.
9. Rate limits and abuse controls permit the operation.

A role label is not a permission. Permissions name concrete capabilities and scopes. Do not rely on client-supplied role, account state, approval status or privilege claims. Server-side policy data is authoritative.

### 3.1 Initial capability families

- identity.read_self; identity.update_profile
- device.register; device.revoke; session.revoke
- memory.read; memory.create_candidate; memory.correct; memory.export; memory.delete
- permission.read; permission.grant; permission.revoke
- action.propose; action.execute_low_risk; action.approve_high_impact
- audit.read; architecture.read
- development.propose_change; development.approve_release

These names are an initial vocabulary, not a grant to implement every capability immediately. Resource scopes and policy conditions must be specified before each capability is enabled.

## 4. Protected-action lifecycle

Every action that can change external state follows:

PROPOSED → VALIDATED → AWAITING_APPROVAL (if required) → AUTHORIZED → EXECUTING → SUCCEEDED | FAILED | CANCELLED

- The proposal includes a typed action, target, canonical payload, expected effects, risk class, required permissions, expiry and idempotency key.
- Validation checks schema, permissions, target state, policy version, limits and whether the action is safe to retry.
- Approval is bound to the exact canonical payload digest. Any material payload change invalidates approval.
- Approval is time-limited, single-use where practical, and recorded against a strongly authenticated session.
- Execution rechecks authority immediately before side effects.
- A timeout with unknown remote outcome is UNKNOWN, not SUCCEEDED; reconcile before retrying.
- Reversible operations define a compensating action. Irreversible operations require stronger approval and clear warning.

## 5. Audit and integrity

- Record authentication, recovery, device enrollment/revocation, permission changes, approvals, protected reads/exports, external actions, policy changes and deployment events.
- Use append-only semantics at the application boundary; privileged mutation/deletion is restricted and separately audited.
- Use server timestamps and unique event identifiers. Consider hash chaining or immutable retention only after threat/cost assessment.
- Correlation IDs connect proposal, approval, execution and result without exposing secrets.
- Never log passwords, PINs, OTPs, access tokens, private keys, raw biometric templates or full sensitive memory payloads.
- Audit read access is itself permission-controlled.

## 6. Android local security contract

- Store authentication material using Android-supported secure storage and Keystore-backed keys where available.
- Encrypt sensitive local databases/backups using maintained libraries and managed key lifecycle; do not invent encryption.
- Exclude secrets, private databases and signing material from backup unless an explicit encrypted backup design authorises it.
- Protect exported files and temporary caches; clear sensitive transient content on logout/revocation where feasible.
- Biometrics may unlock a locally protected key after device-level verification, but do not replace server authentication for remote high-impact operations.
- If hardware-backed key storage or biometric capability is unavailable, degrade safely and disclose the limitation.
- Never hardcode service-role keys or privileged backend secrets in the APK.

## 7. API and data boundary

Each server operation must define authenticated principal source, capability and resource scope, input schema, state transition, idempotency/replay handling, side effects, audit event, safe error codes, rate limits and allowed/denied/expired/revoked/replayed/malformed tests.

Database row-level policies are defence in depth, not a substitute for application-level domain validation. Privileged service credentials stay server-side. Client-supplied metadata is untrusted.

## 8. Recovery and emergency controls

- Provide documented account recovery with verified factors and notification of security-sensitive changes.
- Allow the Creator to list and revoke registered devices and active sessions.
- Suspected compromise triggers session revocation, credential rotation and audit review.
- Maintain a safe mode that disables external actions and self-development while preserving recovery and audit.
- Backups must be integrity-checked and restore-tested before being trusted.
- Recovery must not permit an unauthenticated caller to grant themselves Creator authority.

## 9. Required test matrix before Phase 2 exit

- Unauthenticated, expired-session, revoked-session and unregistered-device requests are denied where applicable.
- Missing capability, wrong resource scope and expired/revoked permission are denied.
- Client-forged role/approval fields are ignored.
- Approval for one payload cannot authorise a modified payload.
- Replay/idempotency behaviour is deterministic; concurrent requests cannot bypass one-time approval.
- High-impact actions require step-up authentication and explicit approval.
- Unknown execution outcomes are reconciled, not reported as success.
- Protected state changes emit audit events without recording secrets.
- Device revocation invalidates subsequent use.
- Recovery cannot escalate privileges.
- Local sensitive data is inaccessible after key/session revocation within the documented threat model.

## 10. Implementation order and exit gate

1. Select and provision a dedicated JAVAS identity/backend environment after Creator approval; never reuse the SACCO project.
2. Define database/API schemas and server-side policy enforcement.
3. Implement authentication/session/device registration.
4. Implement capability grants, scoped authorisation and audit.
5. Implement approval-bound action execution and idempotency.
6. Implement recovery, revocation and safe mode.
7. Add automated unit, integration and negative security tests.
8. Validate on a real Android device and document limitations.

Phase 2 is not complete until the contracts are implemented, negative tests pass, audit/recovery behaviour is demonstrated, and security-sensitive flows are reviewed. This document alone is not implementation evidence.