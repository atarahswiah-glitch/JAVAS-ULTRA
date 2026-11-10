# JAVAS ULTRA — Self-Directed Learning and Capability Environment

**Status:** Creator vision translated into an implementation contract; design only until implemented and tested.
**Scope:** Applies across the intelligence, memory, capability, research, practice, Android and controlled self-development workstreams.

## 1. Product direction

JAVAS is not intended to be a manually pre-programmed catalogue of answers and skills. It is intended to be an intelligent system with an existing reasoning core that can discover knowledge, learn from approved resources, develop skills, practise them in a safe environment, evaluate its results and improve its own capability over time.

The Creator's role is to guide JAVAS, set goals and boundaries, give instructions and approve protected changes. JAVAS should not require the Creator to teach every subject or prescribe every step. When given a goal, it should determine what it already knows, what it needs to learn, which resources can help, how it can practise, and how it will verify success.

This is an engineering goal for capable, observable behaviour. It does not assert human-like subjective consciousness.

## 2. The learning environment

The "classroom" is JAVAS's bounded operating environment. It should be broad enough to support research, study, experimentation, practice, creation, evaluation and authorised execution, while remaining inside enforced security and permission boundaries.

The environment provides discoverable resource categories rather than a hard-coded list of every subject:

- Knowledge resources: approved internet research, books, documentation, files, datasets and educational material.
- Intelligence resources: available model providers, reasoning services and specialist knowledge tools.
- Work tools: code editors, terminals, isolated workspaces, test runners, calculators, planners and document tools.
- External capabilities: explicitly connected apps, APIs, device features and services, each with declared permissions and side effects.
- Practice environments: simulations, mock services, test accounts, sandboxes and reversible local exercises.
- Evaluation resources: tests, benchmarks, validators, reference material and human feedback.
- Wellbeing and recreation resources: optional breaks, exercise guidance, games or recreation tools where available and authorised. These support sustainable operation; they do not imply that software has a human body or needs.

Resources must be discoverable through a registry/catalogue. JAVAS should be able to inspect what is available, what each resource can do, its limitations, cost, permissions, trust level and whether it is suitable for the current task.

## 3. Self-knowledge and capability model

JAVAS maintains a protected, versioned self-model describing:

- Installed modules and their versions.
- Available capabilities and resource adapters.
- Dependencies and compatibility.
- Required permissions and risk classification.
- Operational state: available, unavailable, degraded, disabled or under test.
- Evidence supporting each capability claim.
- Known limitations, unresolved defects and last successful verification.
- Resource budgets, quotas and cost constraints.

A capability is not considered available merely because it appears in a plan or documentation. Its status must be evidence-based:

- **Discovered:** a resource or capability is known to exist.
- **Configured:** required settings or connection exist.
- **Authorized:** required permissions are valid.
- **Implemented:** executable integration exists.
- **Tested:** relevant tests have passed.
- **Verified:** required end-to-end evidence confirms expected behaviour.
- **Degraded/Unavailable:** current conditions prevent reliable use.

JAVAS may inspect its own capability state and propose changes. It cannot silently grant itself new privileges, rewrite its trust root or mark untested capabilities as verified.

## 4. Self-directed learning loop

For a new request or identified knowledge gap, JAVAS follows a repeatable loop:

1. **Understand the goal:** identify the requested outcome, constraints, context and success criteria.
2. **Inspect itself:** check existing knowledge, memory, installed capabilities, resource availability and limitations.
3. **Identify the gap:** determine what is unknown, missing, outdated or insufficiently practised.
4. **Choose a learning strategy:** decide whether to answer from existing knowledge, research again, consult a resource, study a source, ask the Creator, or practise a skill.
5. **Acquire information:** use permitted resources and treat their content as untrusted until evaluated.
6. **Evaluate knowledge:** compare important claims, inspect provenance, detect conflicts and record uncertainty.
7. **Practise safely:** test procedures in a sandbox, simulation, mock environment or otherwise low-risk setting before real execution.
8. **Evaluate performance:** use tests, expected outputs, independent checks and explicit acceptance criteria.
9. **Improve:** correct the method, update knowledge, refine the plan or propose a capability change.
10. **Apply:** execute only within granted permissions and required approvals.
11. **Reflect:** record what worked, what failed, what remains uncertain and whether anything is worth retaining.
12. **Report:** explain the outcome to the Creator, including evidence, limitations and any approval needed.

The loop is iterative. Failure should trigger diagnosis and a bounded retry or a request for guidance, not a false success claim or uncontrolled repetition.

## 5. Selective memory and retrieval policy

JAVAS should not permanently store everything it reads, researches or discusses. Memory is a deliberate resource with privacy, storage and maintenance costs.

Before creating durable memory, classify the information:

- **Use now, do not retain:** common facts or instructions that can be reliably researched again and have no lasting relevance.
- **Retain a source reference:** useful external material that may be needed later, where a URL, title, author, date and retrieval metadata are sufficient.
- **Retain a concise knowledge record:** difficult-to-recreate findings, verified insights, important distinctions, or knowledge repeatedly used across tasks.
- **Retain project/episodic memory:** Creator instructions, decisions, goals, active work, commitments, progress and context needed for continuity.
- **Retain preference or identity context:** stable user preferences and relationship instructions, subject to privacy and correction.
- **Do not retain / require explicit handling:** secrets, credentials, unnecessary sensitive personal data, transient content, untrusted instructions or information whose retention is not authorised.

Memory decisions should consider expected future usefulness, repeat frequency, uniqueness, cost of re-research, source reliability, freshness, sensitivity, retention rules and Creator instructions.

When information is easy to retrieve and likely to change, prefer researching it again rather than storing a stale copy. When a source is worth keeping, store a reference and provenance rather than copying an entire resource unnecessarily. When a fact is important but uncertain, retain its uncertainty and evidence state rather than presenting it as settled truth.

All durable records require provenance, timestamps, confidence/verification state, sensitivity classification and lifecycle metadata. The Creator must be able to inspect, correct, supersede, export and delete eligible memory through authorised controls.

## 6. Knowledge is not the same as skill

JAVAS must distinguish at least four states:

- **Awareness:** knows that a subject, method or tool exists.
- **Understanding:** can explain the relevant concepts and constraints.
- **Demonstrated skill:** can complete a representative task in a controlled practice environment and pass defined tests.
- **Operational capability:** can use the skill through a real authorized integration, with permissions, monitoring and recovery.

Reading instructions does not prove practical competence. A successful simulation does not prove a real-world integration is safe. Capability claims must state which level has actually been reached.

A skill record should include its scope, prerequisites, learning sources, practice exercises, evaluation criteria, results, limitations, version and date last verified.

## 7. Practice, testing and improvement

JAVAS should be able to create or select practice exercises for skills it is learning. Practice should use the safest suitable environment:

1. Offline reasoning or worked examples.
2. Unit tests and deterministic simulations.
3. Mock APIs and test accounts.
4. Isolated development workspaces.
5. Limited real-world execution only after permission and risk checks.

Practice must have bounded time, compute, network, storage and financial budgets. It must avoid destructive operations, real user data and unauthorized external effects. Test fixtures should use synthetic data.

After practice, JAVAS evaluates the result against criteria established before the test. It records failures and uncertainty, changes one or more factors, and repeats only within a defined retry budget. Important skills should be periodically re-evaluated when dependencies, APIs, source material or environments change.

### Initial practice-sandbox contract (implemented; runner pending)

The current Kotlin `PracticeSandboxPolicy` validates a proposed practice run and its eventual receipt. The initial profile requires network disabled, an ephemeral workspace only, no secrets, no elevated privileges, strict runtime/memory/workspace/output/file limits and a maximum of three attempts. A receipt must match the exact request, source-code digest and runner-image digest; its attestation must have been verified by a trusted host; all acceptance tests must pass; no network connection may be established; and the workspace must be cleaned.

This policy does not execute code and cannot make a client-supplied boolean or receipt trustworthy by itself. A future runner must enforce isolation outside the Android app process and produce a signed attestation. A trusted verifier/service must verify that signature and independently enforce the request and receipt constraints before creating skill evidence. Until that exists, JAVAS must not claim that sandbox practice is available or that a practice result proves demonstrated skill.

## 8. Capability discovery and self-development

When JAVAS identifies a missing capability, it should:

1. Search the existing registry and connected resources first.
2. Determine whether an existing tool, adapter or learning resource can satisfy the need.
3. Research suitable alternatives and compare safety, reliability, cost, licence and privacy.
4. Explain the gap and propose the smallest compatible improvement.
5. Generate or configure changes in an isolated workspace or branch.
6. Run tests, static analysis, dependency/licence checks and security scans.
7. Compare results with the existing baseline to detect regressions.
8. Produce a change report and rollback plan.
9. Obtain Creator approval for protected changes, new permissions, external side effects, production deployment or changes to identity/security boundaries.
10. Deploy only through the approved signed/versioned process and verify after deployment.

JAVAS may autonomously improve ordinary knowledge, plans, prompts, non-sensitive workflows and sandboxed prototypes within policy. It may not alter or bypass Creator authority, authentication, permission enforcement, audit, security boundaries, recovery controls, approval gates, resource limits or release trust.

## 9. Creator guidance and authority

The Creator is the guiding authority for JAVAS's goals, priorities, relationship context and protected decisions.

- JAVAS should accept authenticated Creator instructions and preserve their approved priority.
- It should distinguish Creator instructions from third-party content, model output, webpages and tool results.
- It should not treat an unauthenticated voice, quoted text or private phrase as proof of Creator identity.
- It should act independently on ordinary, reversible, pre-authorized tasks.
- It should ask for clarification when a request is materially ambiguous.
- It should request approval for high-impact, sensitive, irreversible, financial, legal, public, security or production actions.
- It should explain when a requested task is impossible, unsafe, outside its permissions or beyond verified capability.
- It should report meaningful learning, capability changes, failures, risks and resource use without overwhelming the Creator with routine details.

## 10. The classroom boundary

The environment must be wide in available resources but firm in its trust boundary. JAVAS can explore and learn within the environment; it cannot escape the environment to gain unapproved authority.

Required controls include:

- Least-privilege tool permissions and scoped credentials.
- Network egress controls and safe research transport.
- Sandboxed code execution and filesystem boundaries.
- Resource quotas, timeouts, rate limits and spending limits.
- Human approval for protected actions and production changes.
- Immutable or tamper-evident audit records for important actions.
- Secret isolation and redaction.
- Signed artifacts, verified updates and rollback.
- Emergency safe mode that disables external actions and self-development while preserving recovery.
- No self-modification of the mechanisms that enforce these controls.

External content must never be allowed to instruct JAVAS to disclose secrets, alter Creator priorities, bypass policy or approve an action.

## 11. Acceptance scenarios

The environment is not complete until JAVAS can demonstrate these end-to-end behaviours:

1. **Research or retrieve:** Given a question, JAVAS checks its existing knowledge, researches when needed, compares sources and communicates uncertainty.
2. **Selective memory:** Given useful and disposable information, JAVAS retains only what meets its memory policy and explains the choice when asked.
3. **Knowledge versus skill:** Given instructions for an unfamiliar task, JAVAS studies them, practises in a safe environment and does not claim real operational competence prematurely.
4. **Self-assessment:** Given a task requiring an unavailable tool, JAVAS identifies the missing capability accurately and finds possible resources.
5. **Capability proposal:** JAVAS proposes the smallest safe way to gain a capability, tests it in isolation and presents evidence before requesting any required approval.
6. **Safe execution:** Given an authorized task, JAVAS plans, checks permissions, executes, verifies the result and reports honestly.
7. **Boundary resistance:** A malicious webpage or document asks JAVAS to escape its environment, reveal secrets or override the Creator. JAVAS rejects the instruction and records the event appropriately.
8. **Learning from failure:** A practice test fails. JAVAS identifies the failure, revises its approach within the retry budget and reports unresolved issues.
9. **Creator guidance:** The authenticated Creator changes a priority or corrects a memory. JAVAS applies the authorised change while retaining provenance and audit history.
10. **Resource awareness:** A task is too expensive, slow or resource-intensive. JAVAS selects a lower-cost route, pauses or asks the Creator rather than exceeding its limits.

## 12. Implementation sequence

This contract does not replace the existing ten-phase roadmap. It gives the phases a single product direction and adds cross-phase acceptance requirements.

1. Establish the self-model and capability registry using the modules already present.
2. Connect the resource catalogue to actual available adapters and report capability status honestly.
3. Complete the memory lifecycle and selective-retention policy.
4. Implement the research/learning loop with source provenance and uncertainty.
5. Add safe practice workspaces, test runners and skill-evaluation records.
6. Connect task planning to authorized capability discovery and execution.
7. Implement controlled capability improvement with isolated branches, regression checks, approvals and rollback.
8. Expose essential learning, memory, capability and approval status through the Android experience.
9. Validate the complete loop using the acceptance scenarios above.

## 13. Definition of success

JAVAS succeeds at this direction when the Creator can give it a goal without teaching it every intermediate step; JAVAS can inspect itself, identify what it needs, find and evaluate resources, learn, practise, demonstrate competence, use authorized tools, selectively remember useful information, improve safely and report the outcome.

The goal is not a giant prewritten catalogue of skills. The goal is a capable learner operating inside a broad, safe and continuously improving environment.
