# Creator Device Trust and Session Enforcement — Phase 2

**Status:** implementation contract only. This branch is stacked on PR #19 and is not merged or deployed. No hosted Supabase changes are authorised by this work.

## Goal

Turn the Security Guardian's proposed actions into enforceable controls. Creator identity, trusted-device state, session validity and recovery authority must be decided by a server-side enforcement boundary, never by the conversational model or client-supplied flags.

## Non-negotiable authority rules

- There is exactly one Creator principal, configured server-side as `JAVAS_CREATOR_USER_ID`.
- A valid Supabase login proves control of an account, not Creator authority.
- A device public key proves possession of its private key only after a fresh server challenge is verified. It does not by itself prove a genuine device, uncompromised OS, or human identity.
- Ignore client-supplied `creator`, `trusted`, `approved`, `hardwareBacked`, role and risk-score fields.
- Ordinary account registration must never claim or inherit Creator status.
- The model can request containment but cannot approve a device, revoke its own audit trail, change trust roots, or restore Secure Shelter.
- No service-role or secret key may be shipped in the Android app.

## Device enrolment protocol

1. Require a valid Supabase access token. Resolve the user with Supabase Auth on the server and compare the verified user UUID to the configured Creator UUID.
2. Require recent step-up authentication for enrolment. Prefer server-verified MFA assurance (AAL2); a local biometric unlock is not remote MFA.
3. Android creates an EC P-256 signing key in Android Keystore and submits the public key, algorithm, installation UUID and minimal platform metadata. The private key never leaves Keystore.
4. Server creates a cryptographically random, single-use nonce (minimum 32 bytes), stores only its cryptographic digest, binds it to Creator UUID, proposed device record, purpose and short expiry (about two minutes), and returns the challenge.
5. Android signs a canonical, versioned challenge payload binding nonce, challenge ID, Creator UUID, device UUID, purpose and expiry. Server verifies the signature against the submitted public key, validates every binding and expiry, then atomically consumes the challenge.
6. A successfully proved key remains **PENDING_APPROVAL**. It is not trusted until the Creator explicitly approves it from an already trusted device with fresh step-up authentication.
7. If Android Key Attestation is added, verify the certificate chain, challenge binding, security level, verified boot state and app identity on the server. A client-reported hardware-backed boolean is informational only.
8. Every protected request checks current device status and session status on the server. Revoked or pending devices are denied even if they hold a cryptographically valid old token.

## Server-owned records

Use a private schema or otherwise non-exposed tables with RLS enabled and no direct client grants.

- **creator_devices:** Creator UUID, device UUID, public key, key thumbprint, algorithm, attestation result and evidence reference, state (PENDING_APPROVAL / VERIFIED / REVOKED), created/approved/revoked timestamps, and bounded device label.
- **device_challenges:** challenge ID, Creator UUID, device UUID, nonce digest, purpose, expiry, consumed timestamp and attempt counter. Enforce one-time consumption atomically; never store the raw nonce after issuing it.
- **creator_sessions:** Supabase Auth session UUID, Creator UUID, device UUID, creation/last-seen/expiry/revocation timestamps and revocation reason code. Do not store bearer or refresh tokens.
- **security_incidents:** use the existing append-only archive contract from PR #19. Add only references/IDs and allowlisted metadata; never passwords, tokens, raw request bodies, private keys or recovery answers.

The service role is used only inside Edge Functions. Revoke client access. Avoid public SECURITY DEFINER functions. If a privileged SQL routine is required for atomic challenge consumption, place it in a non-exposed schema, restrict EXECUTE explicitly, validate all arguments and run Supabase security advisors.

## Session and request enforcement

- Resolve the authenticated Supabase user on every gateway request; never trust a decoded, unverified JWT.
- Obtain the session identifier from a verified claim or trusted Auth context and check it against the server-owned session/device records. If the session identifier cannot be verified, fail closed for protected actions.
- Bind session to Creator UUID and one verified device UUID. Enforce expiry, revocation and account state.
- Require fresh step-up authentication for device approval, device revocation, Creator recovery, trust-root changes and restoration from Secure Shelter.
- Apply the same guard to every privileged Edge Function and sensitive operation. A check only in the chat gateway is insufficient if another function can mutate protected data.
- A revoked session must be rejected by JAVAS endpoints immediately after the revocation transaction commits. Do not claim this invalidates every already-issued Supabase JWT at every Supabase service; JWT revocation and third-party service enforcement have separate limits.
- For Supabase Auth session revocation, use the currently documented Auth admin/session APIs from a server-only context and verify their exact semantics before implementation. Keep the JAVAS-side session denylist as the immediate enforcement gate.

## Revocation and containment

- Creator can revoke one device or all devices except the currently verified recovery device.
- Revocation atomically marks the device and its associated JAVAS sessions revoked, records an incident, and prevents new challenges for that device.
- High-risk signals revoke only the affected session/device unless the evidence justifies wider containment. Do not lock out every device on a low-confidence anomaly.
- Secure Shelter disables sensitive capabilities at the server enforcement boundary, not only by hiding buttons in Compose.
- Preserve encrypted memory and incident evidence. Do not self-copy to unknown devices, exfiltrate secrets, retaliate, wipe evidence or attempt to attack a suspected source.
- Offline: Android may locally pause sensitive actions and queue signed evidence. It cannot revoke a remote session or promise delivery while disconnected. Show "queued" separately from "delivered".

## Creator recovery and last-device loss

- Recovery must not depend solely on a device that may be lost or compromised.
- Provision a separately secured recovery path before enabling automatic lockout. Require strong account verification plus an independent recovery factor; do not treat email possession, display name, voice recognition or knowledge of personal facts alone as sufficient.
- Recovery can place the account in RECOVERY_PENDING, revoke old device/session bindings and require a newly enrolled device plus fresh Creator approval.
- A lost-last-device recovery must be possible without letting an ordinary signed-in account self-promote to Creator. It needs an auditable, one-time, server-controlled recovery ceremony and explicit human approval.
- Restoration requires fresh Creator authentication, verified identity, approved replacement device, successful security checks, incident review and explicit Creator approval. No timer or AI judgement can restore normal mode.

## API contract to implement

All endpoints require HTTPS, verified Supabase Auth, bounded JSON bodies, rate limits, idempotency and no-store responses.

- `POST /javas-device-enrollment/challenge` — issue a short-lived challenge for the verified Creator.
- `POST /javas-device-enrollment/prove` — verify signature and atomically consume challenge; create PENDING_APPROVAL device.
- `POST /javas-device-enrollment/approve` — approve a pending device from an already verified device with fresh step-up authentication.
- `POST /javas-device-enrollment/revoke` — revoke a device and its JAVAS sessions.
- `POST /javas-session/register` — bind verified Auth session UUID to an approved device.
- `POST /javas-session/revoke` — revoke one session or all sessions under explicit Creator-authorised policy.
- `GET /javas-security-incidents` — retain PR #19's Creator-only, bounded, read-only history semantics.

Do not expose public device lookup, challenge listing, or bulk incident export endpoints.

## Tests required before calling this operational

- wrong Creator, ordinary account, missing Creator configuration and expired/consumed challenge all deny;
- signature mismatch, altered payload, wrong device binding, wrong purpose and replay all deny;
- two concurrent prove requests can consume a challenge only once;
- pending and revoked devices cannot register sessions or call protected operations;
- revoked/expired/mismatched sessions are denied by every protected Edge Function;
- client-supplied trust/role/Creator flags have no effect;
- rate limits and bounded attempts resist challenge and login abuse;
- MFA step-up is server-verified; local biometric alone does not satisfy it;
- last-device loss recovery cannot be initiated or approved by an ordinary account;
- Secure Shelter survives app restart and network reconnection, and cannot be cleared by a timer or prompt injection;
- offline containment reports queued evidence honestly and does not claim remote revocation;
- database tests prove client roles cannot read/write private trust records and service-role access is narrowly scoped;
- Android instrumentation verifies Keystore key generation, signature correctness, key deletion and hardware-backed status reporting on real devices.

## Delivery order

1. Implement and test the private schema and challenge-consumption transaction locally.
2. Implement server-side challenge/prove/approve/revoke Edge Functions and tests.
3. Add Android Keystore challenge signing and explicit trusted-device status UI.
4. Add session binding and a shared enforcement guard to every sensitive Edge Function.
5. Connect Guardian actions to containment, Creator alerts and the incident archive.
6. Implement Secure Shelter persistence and Creator recovery ceremony.
7. Run local Supabase migration/function tests, Android unit/instrumentation tests, adversarial replay tests and a real-device acceptance test.
8. Only after review and explicit Creator approval: configure hosted Creator identity/secrets, apply migrations and deploy functions. These hosted actions are out of scope for this branch.

## Branch implementation update — database foundation

The branch now contains a draft migration at
`supabase/migrations/20261126170000_create_javas_creator_device_enrollment.sql`
and pgTAP coverage at `supabase/tests/javas_device_enrollment.test.sql`.

The migration defines:
- server-owned Creator device records with PENDING_APPROVAL / VERIFIED / REVOKED states;
- short-lived device challenges that persist only a SHA-256 nonce digest;
- a database-serialized challenge-issuance function with per-device rate limits;
- an atomic consume-and-create function, so a successful proof creates a PENDING_APPROVAL device in the same transaction and a replay returns no row;
- RLS, revoked client-role table privileges and service_role-only execution of the narrowly scoped SQL functions.

**Important status:** the migration and pgTAP test are committed to this feature branch only. They have not been run against a local Supabase database, applied to the hosted project, or deployed. The branch also now contains a draft `javas-device-enrollment` Edge Function with `challenge` and `prove` actions. It verifies the bearer token with Supabase Auth, checks the server-configured Creator ID and a recent AAL2 step-up, rate-limits challenge issuance through the database routine, verifies the P-256 proof, and calls the atomic consume-and-create routine. A successful proof creates only a PENDING_APPROVAL device. The endpoint is not deployed, and its latest Deno/Android CI is still pending; local Supabase migration/pgTAP execution is also outstanding. Explicit approval from an already trusted device, session binding/revocation and Android client wiring remain outstanding. A cryptographic signature alone does not yet make a device trusted.

## Current verified boundary

PR #19 provides a deterministic Kotlin policy, a readiness screen, gateway blocked-request telemetry, an append-only incident archive migration and a Creator-only incident-history scaffold. This branch now adds the cryptographic proof helper and a draft database-backed challenge schema/transaction with pgTAP coverage. The latest changes require the dedicated isolated Supabase CI job and fresh Android/Deno CI to pass. The Edge Function, trusted-device approval, session binding/revocation, server-enforced Secure Shelter, alerts and recovery are not yet operational. This document does not claim otherwise.


## Branch implementation update — trusted-device approval transaction

A follow-on migration now adds a distinct `APPROVE_DEVICE` challenge purpose and a target-device binding. The approval challenge records the already-verified approver device as the signing device and the pending installation as the target. The database refuses to issue approval challenges unless the approver is currently VERIFIED and the target is currently PENDING_APPROVAL; it also rejects self-approval.

The consume routine rechecks both states, binds Creator/approver/target/purpose/nonce/expiry, consumes the challenge once and changes the target to VERIFIED with an approval timestamp in the same transaction. Replay and mismatched approver/target requests return no approval. A new pgTAP file covers binding, self-approval denial, successful transition and replay denial. CI now runs the complete database test suite rather than only the enrolment test.

**Verification status:** the enrolment migration and its eight pgTAP checks passed in isolated CI run #369; Android, Deno and APK checks passed too. The new approval migration and its additional pgTAP tests have been committed but are awaiting their first CI run. The approval Edge Function and Android Keystore signing flow are not implemented yet. No hosted migration, deployment or merge has occurred.


## Branch implementation update — trusted-device approval Edge Function

The device-enrollment Edge Function now includes two additional actions:

- `approval-challenge`: requires a recent server-verified AAL2 Creator session, a distinct approver device ID and pending target device ID. The database routine verifies that the approver is VERIFIED and the target is PENDING_APPROVAL before issuing the challenge.
- `approve`: loads the approver public key from the server-owned device registry, verifies a P-256 signature over a canonical challenge binding Creator, approver, target, purpose, expiry and nonce, then calls the atomic one-use database consume routine. The database rechecks both device states and changes the target to VERIFIED in the same transaction.

The approval proof does not accept a client-supplied public key. A replay, changed target, changed approver, expired challenge, revoked approver or already-approved target is rejected. The endpoint also inherits the streaming request-body byte limit.

Cryptographic unit tests now cover challenge binding, valid approval, substituted Creator/approver/target, expiry, self-approval and challenge lifetime. CI is configured to type-check the endpoint and run these tests. **These additions are committed on the PR #21 feature branch only. They have not yet been verified by a fresh CI run, deployed, or tested with Android Keystore on a physical device.** The Android signing/UI flow and server-owned session registration/revocation remain outstanding.
