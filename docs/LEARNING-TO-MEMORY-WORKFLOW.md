# Learning-to-memory workflow

**Status:** local review-to-proposal orchestration and atomic encrypted batch persistence implemented; trusted production authorization and device verification remain outstanding.

## Workflow

1. A learning task reaches `READY_FOR_REVIEW` after research evidence has been evaluated.
2. The Creator review request includes task and claim IDs, evidence digest, review ID, decision, reviewer principal, step-up preflight and review timestamp.
3. `LearningMemoryProposalWorkflow` evaluates the request against the supplied consumed-review-ID snapshot and current time. Invalid identity, stale review, digest mismatch, replay or rejection stops before persistence.
4. `LearningMemoryProposalMapper` requires exact agreement between requested, reviewed and supplied claim IDs; supported claims; independent publishers; valid provenance; and no unresolved contradictions or model-output evidence.
5. The shared retention policy and proposal factory prepare eligible corroborated findings as `PROPOSED` semantic memory records.
6. The workflow sends the complete proposal set to `MemoryRepository.insertProposedBatch`.
7. The encrypted repository validates a bounded batch (1–100 records), one matching owner, unique IDs and `PROPOSED` lifecycle. It encrypts all records before opening a single SQLite transaction.
8. The batch is inserted atomically. Exact same-owner/same-content records already present are accepted as idempotent retries. A same-ID changed-content record or owner mismatch rejects the batch.
9. If an interrupted earlier attempt left some exact records and some missing records, the next transaction skips the exact records and inserts the missing ones. Any conflicting record aborts and rolls back all new inserts.
10. After the memory batch commits, the workflow appends a stable `MEMORY_PROPOSAL_PERSISTED` event to the learning journal, with an expected task ID checked inside the journal transaction. A mismatched journal entry is rejected. This is an observation-only receipt: it keeps the task in `KNOWLEDGE_PROPOSED` and does not complete the task.\n11. If memory commits but journal append fails, the workflow returns `PersistedJournalPending` with the exact event payload and stable event ID. Retrying the same review repeats the idempotent memory batch and appends the same journal event.\n12. The result reports persistence success, journal-pending success, or the specific review, mapping or repository denial. Memory remains `PROPOSED`; activation is a separate Creator-authorized action.

## Security boundaries

- This is client-side policy and orchestration, not trusted authorization.
- Principal IDs must be derived from a trusted authenticated session in production.
- The consumed-review-ID set is only a local snapshot. Production must atomically consume the one-time review ID, enforce owner/tenant authorization, insert proposals and write audit evidence on a trusted backend.
- Creator approval permits proposal creation only. It does not activate memory or authorize high-impact actions.
- No untrusted code is executed by this workflow or inside the Android process.
- Android SQLite transactions and Android Keystore encryption require instrumentation and real-device tests. JVM tests do not establish platform storage correctness.
- Learning-journal and memory storage remain separate stores; cross-store atomic completion is not claimed. The recoverable two-step sequence reports the important partial-success state and retries with deterministic IDs rather than hiding it.

## Acceptance tests

JVM tests cover:
- approved review persists one Creator-owned batch of proposed records;
- rejected, consumed or wrong-reviewer requests do not call persistence;
- Creator-owner mismatch is rejected;
- memory storage failure is surfaced without appending a journal receipt;\n- journal failure after memory success is reported as durable memory with a pending stable receipt;\n- retry after journal failure reuses the exact same event ID and payload;
- batch size, unique IDs, owner consistency and proposed lifecycle are validated;
- exact same-owner retries are accepted, while changed-content and cross-owner replays are rejected.

## Next dependencies

1. Trusted backend identity and atomic review-ID consumption.
2. Android instrumentation tests for encrypted batch insert, exact retry, mixed existing/new retry, collision rollback, interrupted transaction, journal receipt idempotency and Keystore failure.
3. Creator review UI and auditable proposal/activation controls.
4. A separate authenticated practice-host service and cryptographic attestation verifier before sandbox evidence can count as demonstrated skill.


## Durable encrypted outbox — implementation increment

The memory SQLite database now has a version-2 migration that creates an owner-scoped `memory_outbox` table without replacing existing memory records. Proposal records and the journal receipt envelope can be encrypted and inserted in the **same SQLite transaction** through `MemoryOutboxStore.insertProposedBatchWithOutbox`.

- Outbox payloads are AES-GCM encrypted with associated data binding both the outbox ID and owner principal.
- The owner lookup column stores a SHA-256 owner hash, not the clear principal ID.
- Batch validation, proposal idempotency and outbox collision checks happen in the same transaction; a conflicting proposal rolls back both new records and the outbox item.
- Exact same-ID/same-owner/same-payload replay is idempotent. Conflicting payloads and cross-owner access are rejected.
- `LearningJournalReceiptEnvelopeCodec` stores a versioned, bounded envelope containing the expected task ID and canonical journal event.
- `LearningMemoryProposalOutboxDispatcher` replays pending receipts, appends to the journal, then acknowledges the outbox. A crash after append but before acknowledgement is safe because the journal event ID is stable and replay-protected.
- The workflow selects this durable path when configured with the outbox-aware repository writer and dispatcher. The older writer-only path remains as a compatibility fallback and is not crash-recovery equivalent.

JVM coverage now exercises envelope validation, owner mismatch, delivery, journal failure with pending retention, and append-success/ack-failure replay. Android instrumentation coverage exercises encrypted outbox payloads, owner isolation, exact replay, acknowledgement, and transaction rollback. These instrumentation tests are compiled by CI; device execution is still required.

This remains local device storage. It does not provide cross-device recovery, trusted backend review consumption, or server-side audit atomicity.
