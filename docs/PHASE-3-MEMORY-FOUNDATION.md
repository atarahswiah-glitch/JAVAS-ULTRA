# Phase 3 — Memory Foundation (Implementation Batches 1–2)

**Status:** Domain policy and local encrypted repository implemented; awaiting CI verification and device-level security testing.  
**Branch:** `docs/phase-1-architecture-foundation`

## Batch 1: memory domain and access policy

- Typed memory categories: session, preference, episodic, semantic, project and sensitive.
- Explicit sensitivity labels, verification states and lifecycle states.
- Provenance fields for source type, source reference, observation time, actor/publisher and notes.
- Confidence and timestamp validation, optional expiry and supersession references.
- Creator-scoped access preflight for read, update, export and delete operations.
- Highly sensitive memory requires step-up proof for read/export/delete preflight.
- Creator-private preferences, episodes and project records are not accessible to non-Creator actors.
- General public semantic knowledge can be read by a non-Creator research worker when classified public.

## Batch 2: encrypted local persistence

- Versioned binary codec for complete memory records, including provenance, sensitivity, lifecycle, confidence and tags.
- Android Keystore AES-256-GCM encryption with a non-exportable key.
- Authenticated additional data binds each encrypted record to its opaque record ID.
- SQLite stores the ciphertext and lookup ID; owner, content, source references and classification remain inside the encrypted payload.
- Repository supports proposed-memory insertion, Creator activation, policy-checked reads, filtered listing and policy-checked deletion.
- Codec tests cover full round-trip, unsupported versions, truncated payloads and invalid records.

## Security and correctness boundaries

The repository is local-device storage, not cloud sync or a trusted server policy. The server must independently authenticate actors, reload current ownership/classification/lifecycle, enforce access, encrypt data in transit and at rest, and write tamper-resistant audit events. Caller-supplied `isCreator` and `stepUpAuthenticated` values remain local preflight inputs and must never be trusted as server proof.

The Android Keystore key is device-bound. This implementation does not yet provide multi-device key recovery, cloud backup, key rotation, secure cross-device sync or account-level recovery. If the Keystore key is invalidated or lost, encrypted records may become unrecoverable; a tested recovery design is required before production use. The database schema is versioned and intentionally refuses destructive upgrades until explicit migrations exist.

No real Creator memories or personal conversation content are seeded into the repository. No cloud project, paid service or production secret is required.

## Remaining memory work

- Retrieval ranking and query matching with sensitivity-aware filtering.
- Consent, retention, expiry, correction, export and verified erasure workflows.
- Audit events for memory lifecycle changes.
- Robust key invalidation/recovery and key rotation strategy.
- Encrypted sync protocol and trusted-server authorisation.
- UI for inspecting, correcting and deleting remembered information.
- Device-level security testing and CI verification.

Do not mark this batch Verified until CI and required Android-device tests pass.


## Batch 3: privacy-aware retrieval

- Added deterministic local keyword retrieval over authorised memory records.
- Access/lifecycle checks and expiry filtering occur before relevance scoring.
- Ranking considers query-term coverage, exact phrase match, confidence and verification status; disputed claims rank below corroborated/Creator-confirmed records.
- Search results include matched terms and are bounded to a maximum of 100 results.
- Tests cover relevance, private-memory isolation, Creator access, expiry and disputed-evidence ranking.

This is a deterministic lexical baseline, not vector/semantic retrieval. Embeddings, hybrid retrieval, multilingual tokenisation and relevance evaluation remain future work.
