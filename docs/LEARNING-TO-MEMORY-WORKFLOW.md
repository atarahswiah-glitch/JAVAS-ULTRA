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
10. The result reports persistence success or the specific review, mapping or repository denial. Memory remains `PROPOSED`; activation is a separate Creator-authorized action.

## Security boundaries

- This is client-side policy and orchestration, not trusted authorization.
- Principal IDs must be derived from a trusted authenticated session in production.
- The consumed-review-ID set is only a local snapshot. Production must atomically consume the one-time review ID, enforce owner/tenant authorization, insert proposals and write audit evidence on a trusted backend.
- Creator approval permits proposal creation only. It does not activate memory or authorize high-impact actions.
- No untrusted code is executed by this workflow or inside the Android process.
- Android SQLite transactions and Android Keystore encryption require instrumentation and real-device tests. JVM tests do not establish platform storage correctness.
- Learning-journal and memory storage remain separate stores; cross-store atomic completion is not claimed.

## Acceptance tests

JVM tests cover:
- approved review persists one Creator-owned batch of proposed records;
- rejected, consumed or wrong-reviewer requests do not call persistence;
- Creator-owner mismatch is rejected;
- storage failure is surfaced without claiming success;
- batch size, unique IDs, owner consistency and proposed lifecycle are validated;
- exact same-owner retries are accepted, while changed-content and cross-owner replays are rejected.

## Next dependencies

1. Trusted backend identity and atomic review-ID consumption.
2. Android instrumentation tests for encrypted batch insert, exact retry, mixed existing/new retry, collision rollback, interrupted transaction and Keystore failure.
3. Creator review UI and auditable proposal/activation controls.
4. A separate authenticated practice-host service and cryptographic attestation verifier before sandbox evidence can count as demonstrated skill.
