# Learning-to-memory workflow

**Status:** local domain workflow implemented; trusted production authorization and device verification outstanding.

## Purpose

Connect the existing research-evidence, Creator-review, retention and encrypted-memory components without bypassing their security boundaries.

## Workflow

1. A learning task reaches `READY_FOR_REVIEW` only after the evidence evaluator has assessed the research claims.
2. The Creator review request carries the task, claim IDs, evidence digest, review ID, decision, reviewer principal and step-up-authentication evidence.
3. `LearningMemoryProposalWorkflow` re-evaluates the review request against the supplied consumed-review-ID snapshot and current time. Invalid identity, stale review, digest mismatch or replay is denied before persistence.
4. Approved claims are passed to `LearningMemoryProposalMapper`, which requires exact claim-set agreement, supported claims, independent publishers, valid provenance and no unresolved contradictions.
5. The shared retention policy and proposal factory classify the information. Only eligible corroborated claims become `PROPOSED` semantic records.
6. The workflow inserts proposals through `MemoryRepository.insertProposed`. The encrypted repository enforces owner matching and encrypts the complete record.
7. The result reports accepted record IDs and, on failure, the failed ID and repository failure. Partial persistence is explicit so the caller can retry safely.
8. Memory remains `PROPOSED`. Activation is a separate Creator-authorized operation and is not called by this workflow.

## Retry and partial-failure rules

Proposal IDs are deterministic from task ID and claim ID. An exact same-owner retry of an existing proposal is accepted as an idempotent replay. If the same ID is presented with different content, insertion returns a duplicate-ID failure. A caller can therefore retry the complete proposal batch after a partial failure without creating duplicate records or silently replacing changed claims.

The SQLite implementation checks an existing encrypted record inside a database transaction. A record that cannot be decrypted is treated as a storage failure, not as a successful replay.

## Security boundaries and limitations

- This is local client-side orchestration, not trusted authorization.
- The principal IDs supplied to the coordinator are not proof of authentication. Production must derive them from a trusted authenticated session.
- The consumed-review-ID set is an input snapshot, not durable replay protection. Production must atomically consume the one-time review ID and insert the corresponding proposals on a trusted backend, with tenant/owner checks and audit logging.
- Creator review permits proposal creation only. It does not activate memory, grant general execution permission or authorize high-impact actions.
- No untrusted code is executed by this workflow or inside the Android process.
- Android SQLite transaction and Android Keystore encryption must be verified with instrumentation tests and real devices; JVM policy tests alone do not establish device behaviour.

## Acceptance tests

The JVM suite covers:
- approved review creates only proposed records;
- rejected, consumed or wrong-principal reviews never call persistence;
- partial persistence reports accepted and failed record IDs;
- a persistence failure before the first record is reported distinctly;
- exact same-owner record replay is accepted;
- same-ID changed content and cross-owner replay are rejected.

## Next dependencies

1. Trusted backend identity and atomic review-ID consumption.
2. Instrumentation tests for encrypted SQLite insert, exact retry, collision, interrupted transaction and Keystore failure.
3. Creator review UI and auditable proposal/activation controls.
4. A separate authenticated practice-host service and cryptographic attestation verifier before sandbox evidence can count as demonstrated skill.
