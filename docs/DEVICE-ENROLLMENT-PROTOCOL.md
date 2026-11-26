# Device enrollment protocol

## Android client foundation

`DeviceKeyManager` creates a per-Creator installation identity:
- A random installation UUID is scoped to the Creator account locally.
- An EC P-256 signing key is generated in Android Keystore; the private key is non-exportable through the app API.
- The public key is encoded for registration.
- A server-issued challenge of 32–512 bytes can be signed with SHA-256/ECDSA.
- The client reports whether Android identifies the key as hardware-backed.

The local UUID and key do not make a device trusted. A software-backed key is not equivalent to a hardware-backed key. The client must not display a trusted status until the server confirms enrollment.

## Required server protocol (not yet implemented)

1. An authenticated Creator requests an enrollment challenge.
2. Server creates a cryptographically random nonce, stores only its digest, binds it to the authenticated Supabase user ID and proposed device record, and gives it a short expiry (for example 2 minutes).
3. Client submits device UUID, public key, algorithm, platform/build metadata and a signature over the exact nonce.
4. Server verifies signature using the submitted public key, checks challenge expiry and single-use state, checks account ownership, and atomically consumes the challenge.
5. Device remains `PENDING_VERIFICATION` until Creator performs explicit step-up approval. Only then may server mark it `VERIFIED`.
6. Every protected request checks server-side device state and session revocation. Client-supplied `verified=true` is ignored.
7. Creator can revoke a device. Revocation must invalidate associated sessions and deny future protected requests.

## Attestation and limits

ECDSA proof-of-possession alone proves that the requester can use the matching private key; it does not prove that the key is hardware-backed or that the app binary is genuine. Stronger device assurance requires Android Key Attestation verification (certificate chain, challenge binding, security level, verified boot state, application identity) on the server, with explicit compatibility handling for devices that do not support it. Until implemented, report the hardware-backed signal as diagnostic only and do not grant higher privileges from it.

## Required negative tests

- Invalid signature, wrong nonce, expired nonce, reused nonce and nonce bound to another Creator are denied.
- Public-key replacement after challenge issuance is denied.
- Pending and revoked devices cannot access protected endpoints.
- Revocation invalidates existing sessions.
- A copied device UUID without the private key is denied.
- Software-backed key status does not silently become hardware-attested trust.
- Concurrent challenge redemption can succeed only once.

## Deployment boundary

The Android key manager and instrumentation tests are client-side groundwork only. No device registration endpoint, database table, attestation verifier, trust decision or live Supabase deployment is implemented by this change.
