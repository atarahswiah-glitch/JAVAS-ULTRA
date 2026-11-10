# Learning-to-memory proposal boundary

## Purpose

This document records the local Android domain boundary between Creator review and the encrypted memory repository. It is a proposal pipeline only, not a production identity or server authorization system.

## Implemented flow

1. The learning evidence evaluator checks that a task is in `EVALUATING`, claims are structurally valid, sources are fresh, model output is not used as evidence, contradictions are absent, and the configured minimum number of independent publishers is met.
2. The Creator review policy checks the task's `READY_FOR_REVIEW` state, Creator principal match, step-up authentication flag, review replay identifier, SHA-256 evidence digest match, non-empty approved claim set, and review freshness.
3. `LearningMemoryProposalMapper` accepts only an approval outcome for the same task/review and the exact approved claim set. It rejects duplicate source identifiers, unsupported claims, model-output sources, insufficient independent publisher provenance, mismatched Creator identity, and digest mismatch.
4. Each accepted claim becomes a semantic, Creator-private `MemoryRecord` with lifecycle `PROPOSED`. The record stores the learning task, review ID, evidence digest, supporting source IDs and publisher summary in its provenance.
5. A caller may pass each record to `MemoryRepository.insertProposed`, which separately validates record shape and owner equality. This mapper itself does not persist records.

## Explicit non-capabilities

- No proposal is automatically activated.
- No learning task is automatically marked complete.
- A review result does not grant general memory access or authorize unrelated actions.
- The mapper does not authenticate the Creator; its principal arguments are local preflight inputs.
- Local replay checking is not durable or transactional server-side replay protection.
- Evidence publisher strings are not proof of independent ownership or source authenticity.
- The evidence digest is carried as review provenance; this layer does not itself recompute a canonical digest over a serialized evidence package.
- Android Keystore, encrypted database behavior, device integrity and repository transactions still require device-level tests.

## Production requirements before enabling autonomous learning

- Derive Creator and reviewer identities from a trusted authenticated session, never caller-supplied UI values.
- Recompute the canonical evidence digest on the trusted boundary.
- Atomically consume the review ID and create proposed-memory records in one trusted transaction.
- Validate source URLs, redirects, DNS/IP ranges, response sizes, retrieval timestamps and publisher independence in a hardened research transport.
- Keep proposal creation separate from activation; require a distinct Creator-authorized activation operation and step-up authentication where policy requires it.
- Add migration/version tests for the new `RESEARCH_EVIDENCE` provenance enum value.
- Verify encryption, decryption failure classification, deletion behavior and persistence on physical Android devices.

## Verification

The JVM test suite includes positive proposal mapping and rejection cases for rejected reviews, mismatched claim sets, non-Creator ownership, unsupported claims, duplicate source IDs and model-output evidence. CI must pass on the current branch tip before this package is considered verified.
