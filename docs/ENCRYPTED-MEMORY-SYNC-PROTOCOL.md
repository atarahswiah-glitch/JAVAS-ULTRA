# Encrypted memory sync — protocol and implementation boundary

**Status:** Client-side envelope contract and JVM validation tests added. No sync transport, key wrapping, server endpoint, database schema, RLS policy or deployment is implemented by this document.

## Security objective

Allow Simon's memory to be synchronised only between devices that have been explicitly approved for the same authenticated Creator account, without giving the database, gateway or AI provider plaintext memory or provenance.

The existing Android Keystore AES-GCM key is installation-bound. It must not be copied to the server or treated as an account-wide recovery key.

## Required cryptographic design

1. Each memory record is encrypted on-device with a fresh random 256-bit data-encryption key (DEK) using AES-256-GCM and a fresh 96-bit nonce.
2. Authenticated additional data is constructed canonically from the verified Creator principal, opaque record ID, envelope format version and key version. The owner principal is taken from the authenticated session, never from a client-supplied owner field.
3. The DEK is wrapped separately for each currently approved device. The wrapping operation must use a dedicated device key-agreement/encryption key, not the signing-only key created by the current `DeviceKeyManager`. A server-verified device public key is required.
4. The server stores only the ciphertext, nonce, wrapped DEK and opaque identifiers/version metadata. It must not receive plaintext memory, provenance, a raw DEK or a plaintext export.
5. On an approved device, the local Keystore private key unwraps the DEK; AES-GCM authentication must succeed before the memory codec is allowed to decode content.
6. AAD mismatch, unknown format, unknown key version, invalid tag, missing wrapping key or revoked device fails closed. Never fall back to plaintext or weaker encryption.

The precise key-agreement and key-wrapping primitive must be selected and reviewed before implementation. Do not improvise a custom cryptographic construction. Prefer a standard, reviewed hybrid-encryption construction compatible with Android Keystore and the supported Android versions.

## Server-side requirements

- Derive the account owner from a verified Supabase JWT; reject any request that attempts to choose another owner.
- Require a server-side device row in the approved state and an active, non-revoked session for every sync read/write.
- Bind each wrapped DEK to an exact approved device-key ID. A device cannot request a key wrap for a public key it just supplied without passing the separate enrollment and Creator-approval process.
- Use strict request/body limits, per-user and per-device rate limits, bounded concurrency, idempotency keys and conflict detection.
- Keep memory rows private with RLS. A policy must scope every row to the authenticated principal; authenticated role membership alone is not sufficient.
- Do not expose service-role credentials to Android or browser clients. Do not use user-editable JWT metadata as an authority source.
- Audit enrollment, approval, sync, key rotation and revocation events without logging plaintext, tokens, raw keys or memory contents.
- Store only opaque envelope data in the sync table. Use server-assigned timestamps/revisions and atomic writes.

## Device revocation and recovery

- Revocation immediately blocks future sync operations and invalidates or rejects existing sessions on protected operations.
- Revocation cannot erase plaintext or keys already downloaded by a compromised device. Treat that as a fundamental limit, not a promise.
- After revocation, rotate the account memory key version and wrap new DEKs only for the remaining approved devices.
- A replacement device receives access only after fresh authentication, fresh device enrollment, explicit Creator approval and a secure key-recovery procedure.
- Do not create a server-side master key that can decrypt every memory record. Recovery must be designed so the service provider cannot silently read the Creator's memory.
- If no approved device can recover the account key, explain that some encrypted data may be unrecoverable; do not silently reset encryption and pretend old data was restored.

## Current code in this branch

`MemorySyncEnvelope` is a bounded wire-contract model containing an opaque record ID, format/key versions, wrapping-key reference, wrapped DEK, nonce and ciphertext. It deliberately has no plaintext, provenance or caller-supplied owner field. JVM tests cover malformed versions, key references, nonce size and payload bounds.

This model does **not** perform cryptography or networking. The existing `DeviceKeyManager` currently creates a signing key only. A separate encryption/key-agreement key and reviewed wrapping implementation are still required.

## Deployment boundary

No Supabase migration is included: the project is inactive and no schema change is authorised or deployed. When implementation is approved, create migration files using the Supabase CLI workflow, review RLS and grants, run security advisors and integration tests, and only then consider deployment. Do not mark cloud sync complete until server-side tests and real-device multi-device recovery tests pass.
