# JAVAS ULTRA

**Persistent Personal AI Companion and Intelligent Agent**

JAVAS ULTRA is being developed as a secure, memory-enabled, practical AI companion with a consistent identity, useful reasoning, controlled tool use and continuity across supported devices.

## Project status

- Current work: Phase 1 architecture/repository baseline, Phase 2 identity/security contracts drafted, and the first native Android shell plus CI pipeline under build verification.
- Current implementation status: architecture/security documentation plus an initial Android source skeleton; build verification is pending.
- No successfully built or installable Android APK, authentication implementation or production backend is claimed yet.
- Draft PR #1 remains open and unmerged.

## Start here

- [Master architecture](docs/MASTER-ARCHITECTURE.md)
- [Integrated ten-phase roadmap](docs/ROADMAP.md)
- [Requirements register](docs/REQUIREMENTS-REGISTER.md)
- [Security and privacy baseline](docs/SECURITY-AND-PRIVACY-BASELINE.md)
- [Phase 2 identity and security contracts](docs/PHASE-2-IDENTITY-SECURITY-CONTRACTS.md)

## Development principles

- Native Android is the first client direction.
- Preserve identity, approved memory and user control across upgrades and migration.
- Keep planning separate from permission to execute actions.
- Treat external content and tool output as untrusted input.
- Require evidence before marking work implemented or verified.
- Keep credentials, private memory and production data out of source control.

## Scope exclusions

Brain-computer interfaces, neural sensing, mind uploading, consciousness transfer and merging the Creator with JAVAS are explicitly outside the current implementation scope. They require separate future authorisation.

## Contributions and changes

Use focused branches and reviewable pull requests. Material changes to identity, permissions, security boundaries, memory lifecycle or deployment authority require explicit review.