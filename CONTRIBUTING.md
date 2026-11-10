# Contributing to JAVAS ULTRA

## Before changing code
1. Read the master architecture, roadmap, requirements register and security baseline.
2. Identify affected requirement IDs and module boundaries.
3. Check whether the change modifies identity, permissions, memory, privacy, external actions, persistence or deployment.
4. Prefer a focused branch and a coherent pull request.

## Change workflow
- Keep changes reviewable and describe the reason for the change.
- Add or update tests with implementation changes.
- Record migrations for persisted data and public interfaces.
- Include rollback or compensation for changes with side effects.
- Update documentation and requirement status only when supported by evidence.
- Do not merge unreviewed security-sensitive changes.

## Security rules
- Never commit API keys, passwords, access tokens, signing keys, private conversations, personal memory exports or production data.
- Use placeholders in examples and document required environment variable names.
- Treat external content, imported files and tool results as untrusted.
- Do not weaken authentication, permission checks, audit, approval or recovery controls to make a test pass.
- Do not place service-role credentials in Android/client code.

## Testing and reporting
A pull request should state:
- What changed and why.
- Requirements addressed.
- Tests run and their actual results.
- Known limitations and unverified behaviour.
- Data migration and rollback implications.
- Any Creator decision or physical-device validation still required.

A design document is not an implementation. Code that compiles is not automatically secure or feature-complete.
