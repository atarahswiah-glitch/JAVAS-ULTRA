# Creator identity, runtime identity and gateway security

## Creator authority

- JAVAS has one Creator principal: the Supabase Auth user ID stored in the server-only `JAVAS_CREATOR_USER_ID` setting.
- The client cannot set its own Creator ID, role, approval, or trusted-device state.
- The gateway validates the bearer token with Supabase Auth and compares the verified user ID with the configured Creator ID.
- A display name such as Simon is presentation data only. It never grants authority. `JAVAS_CREATOR_DISPLAY_NAME` may be configured server-side after the Creator account is provisioned.
- If the Creator ID is absent, the gateway fails closed. Do not infer the Creator from the first account, email text, device label, voice, or prompt.

## Runtime identity privacy

- The product presents itself as JAVAS.
- The runtime must not volunteer internal model/provider names or development-tool names as its identity.
- The runtime must not claim that an AI provider or development tool is the Creator, nor invent a human developer.
- If asked who governs JAVAS, it may identify only the server-verified Creator display name. If that value is not configured, it must say the Creator display name is not configured.
- Development history, commit attribution, security audit records, and required legal disclosures must remain accurate. Runtime identity privacy is not permission to falsify authorship or erase security records.

## Gateway protections implemented in this branch

- JWT validation through Supabase Auth `getUser(accessToken)`.
- Exact server-side Creator user ID allowlist; deny by default when not configured.
- Strict request method and message-role validation. Clients cannot submit system/developer prompts.
- Request body size and message count/length limits.
- Browser CORS origins are restricted to exact values in `JAVAS_ALLOWED_ORIGINS`. Native clients without an Origin header remain supported. CORS is not authentication and does not replace bearer-token validation.
- No provider error bodies or secrets are returned to clients.
- Provider key remains server-side.
- Responses use `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

## Persistent memory status

The Android app already has a local encrypted memory foundation:
- AES-256-GCM using a non-exportable Android Keystore key.
- Authenticated additional data binds ciphertext to opaque record IDs.
- SQLite stores encrypted payloads; memory content and provenance are encrypted together.
- Owner checks, lifecycle checks, expiry checks, sensitive-memory step-up preflight, retention decisions and encrypted learning-journal components exist.

This is not yet cloud-synced persistent memory. Device-bound keys do not provide multi-device recovery. Server-authorised sync, Creator consent UI, durable audit, retention/erasure workflows, key rotation/recovery and device trust registration remain required before production use.

## Security work still required

No system can be guaranteed unhackable. Before production, implement and verify:
1. Server-backed device enrollment using a fresh, one-time challenge signed by an Android Keystore device key; keep pending devices untrusted until server verification and Creator approval.
2. Server-side rate limits and abuse controls (per Creator, device, IP where available) with bounded concurrency and spend limits.
3. Session/device revocation checks on every protected server operation.
4. Private memory sync endpoints and database policies; never trust client-supplied owner IDs or Creator flags.
5. Append-only security audit events without credentials, tokens, memory plaintext or biometric data.
6. Recovery, key rotation, backup/restore and lost-device procedures.
7. Automated negative tests, dependency/supply-chain review, secret scanning, static analysis, and physical-device testing.

## Configuration required before browser use

Set `JAVAS_ALLOWED_ORIGINS` to a comma-separated list of exact trusted HTTPS origins. Leaving it unset blocks browser-origin requests but does not block native app requests that omit Origin. Set `JAVAS_CREATOR_USER_ID` and, optionally, `JAVAS_CREATOR_DISPLAY_NAME=Simon` only after the correct Creator account has been verified.

## Deployment boundary

This branch contains code and documentation only. It has not been deployed to Supabase. The JAVAS-ULTRA Supabase project was previously observed inactive; live endpoint, RLS, rate-limit and device tests remain pending. Do not mark this security phase complete until the live project and physical devices have passed the required tests.
