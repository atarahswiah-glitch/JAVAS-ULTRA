# Phase 3 — Memory, Knowledge and Learning Foundation

**Status:** Encrypted memory persistence, privacy-aware retrieval, learning policy, event-driven journal transitions and an encrypted append-only learning-journal store implemented on the draft branch; latest CI verification pending. Scheduled worker execution and device-level security testing remain outstanding.  
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


## Batch 4: learning journal and scheduled discovery policy

- Added a learning journal state model with a controlled lifecycle from discovery through queueing, research, evidence review, knowledge proposal and completion.
- Added event validation for actor, entry binding, timestamps and required source/claim references.
- Terminal completed/cancelled entries cannot be reopened through ordinary transitions.
- Added a schedule policy for recurring autonomous learning, including pause windows, minimum/maximum interval and daily task limits.
- Added tests for valid lifecycle, terminal-state restrictions, event validation and scheduled execution eligibility.

This defines the scheduler and audit-journal contracts. It does not yet install a background worker, persist journal events or execute website research automatically. Those require the subsequent scheduler, durable journal repository and reviewed public-web transport.


## Batch 5: event-driven learning journal transitions

- Added a pure `LearningJournalPolicy.apply` operation that validates an event and returns an updated immutable journal entry or a typed denial.
- Event types now map to explicit lifecycle transitions; invalid event/state combinations are rejected.
- Source and claim references are accumulated on the entry as evidence events are applied.
- Failure detail is retained as a bounded failure code field; callers must not place secrets or private research content in safe metadata.
- Added tests for sequential event application, evidence-reference retention, invalid transitions and required claim identifiers.

The policy remains storage-agnostic. It does not yet append events to a durable journal, guarantee event-ID idempotency across process restarts, or run a scheduler. Those remain separate implementation tasks.


## Batch 6: encrypted append-only learning journal

- Added a dedicated SQLite journal store with encrypted entry snapshots and encrypted event payloads using a separate Android Keystore AES-256-GCM alias.
- Entry snapshots and event history are written in one SQLite transaction so an event cannot be committed without its corresponding state update.
- Event IDs are unique. Replaying the same event ID and normalized payload returns the current entry; reusing an event ID with different content is rejected.
- Authenticated additional data binds each encrypted payload to its kind and opaque identifier.
- Added a versioned, bounded binary codec for journal entries and events, including source/claim references and safe metadata.
- Added JVM codec tests for round trips, unsupported versions, trailing data and safe-detail sanitisation.
- Tightened lifecycle application so state-changing events cannot be accepted as duplicate no-op transitions; evidence observation events remain non-transitioning where appropriate.

**Verification boundary:** the codec and policy have JVM unit tests. The SQLite transaction, Android Keystore integration, process-restart replay behaviour and database migration path still require Android instrumentation/device tests. The journal database currently refuses unknown schema upgrades rather than risking destructive migration. No scheduled worker or automatic public-web fetcher is claimed by this batch.


## Batch 7: memory boundary and export hardening

- Non-public memory is now owner-scoped even when the requesting actor is not marked as the Creator; only explicitly PUBLIC records can be shared with other principals.
- Added an explicit repository export operation that requires `MemoryAction.EXPORT`; high-sensitivity export remains subject to step-up policy.
- Enabled SQLite `secure_delete` for the local encrypted-memory database to reduce retention of deleted ciphertext in freed database pages.
- Added tests for non-public owner isolation, public research access, sensitive export step-up and delete ownership.
- The model gateway route policy now checks estimated input units against provider context limits before selecting a provider.
- Autonomous-learning planning now rejects duplicate/future signals, invalid web allowlists, oversized topics and exhausted daily budgets.

**Verification boundary:** these are local policy and repository changes. Export packaging, user-facing correction/deletion flows, tamper-resistant audit, Android Keystore/device tests, forensic erasure guarantees and trusted-server enforcement remain outstanding.
