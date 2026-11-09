# Phase 3 — Memory Foundation (Implementation Batch 1)

**Status:** Implemented locally in the Android domain layer; awaiting CI verification.  
**Branch:** `docs/phase-1-architecture-foundation`

## Included capabilities

- Typed memory categories: session, preference, episodic, semantic, project and sensitive.
- Explicit sensitivity labels, verification states and lifecycle states.
- Provenance fields for source type, source reference, observation time, actor/publisher and notes.
- Confidence and timestamp validation, optional expiry and supersession references.
- A local Creator-scoped access preflight for read, update, export and delete operations.
- Highly sensitive memory requires a step-up proof for read/export/delete preflight.
- Creator-private preferences, episodes and project records are not accessible to non-Creator actors.
- General public semantic knowledge can be read by a non-Creator research worker when classified public.

## Security and correctness boundary

This batch is a pure Kotlin domain model and deterministic policy preflight. It is **not** encrypted persistence, authenticated storage, a trusted server policy, or a functioning memory database. The server/storage adapter must independently authenticate the actor, reload current ownership/classification/lifecycle, enforce authorisation, encrypt private data at rest and in transit, and write tamper-resistant audit events. The boolean `isCreator` and `stepUpAuthenticated` inputs are caller-supplied preflight values and must never be trusted as server proof.

No personal conversation content or real Creator memory is seeded into the repository. No cloud project, paid service, or production secret is required by this batch.

## Test coverage added

- Creator can read their own preference.
- Non-Creator cannot read Creator-private memory.
- Creator cannot read another owner's memory.
- Highly sensitive read requires step-up preflight.
- Proposed memory cannot be read as active.
- Invalid confidence is rejected.
- Sensitive category must use the highest sensitivity classification.
- Public semantic knowledge can be read by a non-Creator research worker.

CI must pass before this batch is marked verified. Durable storage, encryption/key management, consent/retention workflows, inspect/correct/export/delete UI, retrieval ranking, contradiction handling and research ingestion remain subsequent work.
