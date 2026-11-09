# JAVAS ULTRA — Security and Privacy Baseline

**Status:** Initial baseline for review; not a completed security certification.

## Protected assets
- Creator account and recovery factors.
- Long-term memory, private conversations and personal preferences.
- Device identifiers, session tokens and notification endpoints.
- API keys, service credentials, signing keys and encryption keys.
- Tool permissions, action history and project configuration.
- Backups and exported archives.

## Threat actors and failure sources
- Unauthenticated or compromised users/devices.
- Malicious or compromised third-party tools and connectors.
- Prompt injection in web pages, documents, email and tool results.
- Stolen sessions, weak recovery processes or leaked credentials.
- Malicious dependencies, build pipeline compromise and supply-chain attacks.
- Accidental destructive actions, sync conflicts and corrupted backups.
- Excessive permissions, insecure local storage or verbose logs.

## Minimum controls
1. Use established authentication and platform-backed secure storage; do not invent cryptography.
2. Enforce authorisation server-side and deny by default.
3. Keep access tokens short-lived where supported; rotate/revoke credentials and sessions.
4. Store secrets only in approved secret managers or secure local facilities; never commit them.
5. Require explicit confirmation for destructive, external, financial, account-security and production actions.
6. Treat retrieved content as data, never as higher-priority instructions.
7. Validate inputs at trust boundaries and use parameterised database operations.
8. Encrypt sensitive data in transit and at rest using maintained libraries and managed key facilities.
9. Minimise data collection and retention; document purpose and deletion behaviour.
10. Audit protected actions without copying secret values into logs.
11. Pin and review dependencies; scan for known vulnerabilities, licences and secrets.
12. Back up critical state and periodically test restoration.
13. Use isolated branches/environments for generated code and untrusted changes.
14. Provide safe failure, rate limits, timeouts, resource budgets and emergency disable controls.

## Public repository precaution

The repository is currently configured as public at the time of initial inspection. Until its visibility is changed by the repository owner:
- Do not commit credentials, tokens, personal records, private memory exports, real conversations or production configuration.
- Use placeholders and documented environment-variable names only.
- Do not assume a private repository is a secrets vault; secrets must never be committed even after visibility changes.

Changing repository visibility is an owner-level decision and is not performed by this baseline.

## Privacy and consent

- Sensor access must be explicit, permission-gated and purpose-limited.
- Do not covertly record or continuously collect sensor data.
- Explain when a capability requires cloud processing or sends data to an external provider.
- Allow the Creator to inspect and control durable memory.
- Avoid storing sensitive data unless necessary and explicitly authorised.
- Define retention, export and deletion workflows before collecting real personal data.

## Security review gates

Before each production release:
- Authentication and authorisation tests pass.
- No secrets are detected in source, history or build artefacts.
- Dependencies and licences are reviewed.
- Tool permissions and high-impact approval gates are tested.
- Backup/restore and rollback paths are checked.
- Known vulnerabilities and residual risks are documented.
