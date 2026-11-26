# JAVAS Survival Protocol — Security Guardian

**Status:** Initial, side-effect-free Kotlin policy and JVM tests. This is not a deployed intrusion-detection or response system.

## Objective

JAVAS must be able to take proportionate emergency defensive action while the Creator is unavailable. Its priorities are to protect the Creator's data, preserve trustworthy evidence, prevent unauthorised actions and remain recoverable.

The survival sequence is:

1. **Detect** suspicious access, identity changes, unexpected privilege requests, memory access anomalies and integrity/monitoring tampering.
2. **Defend** by rejecting the current unauthorised request and requiring fresh authentication where appropriate.
3. **Disconnect** affected sessions and credentials through a trusted server-side enforcement point.
4. **Shelter** by disabling sensitive capabilities and restricting the affected environment.
5. **Preserve** encrypted security evidence and queue an alert for the Creator.
6. **Recover** only through fresh authentication, verified Creator identity, an approved device, successful security checks, incident review and explicit Creator approval.

## Response levels

| Signal level | Expected response |
| --- | --- |
| Informational | Record the event; do not interrupt normal operation by itself. |
| Suspicious | Block the current request, require fresh authentication and queue an alert. |
| High | Revoke the affected session, disable sensitive capabilities, preserve encrypted evidence and enter restricted mode. |
| Critical | Enter Secure Shelter, isolate affected sessions, preserve evidence and require Creator-led recovery. |
| Integrity or security-monitoring tamper | Treat as critical even if the event's supplied severity is lower. |

A signal is evidence to assess, not proof of a person's identity or intent. Detection should distinguish confirmed policy violations from anomalies and false positives. Do not label a person an attacker based on an unexplained anomaly.

## Secure Shelter

Secure Shelter is a restricted state, not a self-replicating escape mechanism. The system may:
- reject new sensitive operations;
- disconnect affected sessions using trusted server-side controls;
- retain only the minimum necessary local functionality;
- protect existing encrypted memory and security records;
- queue a Creator alert for later delivery when connectivity returns;
- direct the Creator to a separately secured recovery path.

The system must not silently copy itself to unknown phones or computers, transfer secrets to an unverified host, retaliate against an external device, or destroy evidence.

A separate recovery environment must be provisioned and secured in advance. A client running on a fully compromised operating system cannot reliably attest to its own integrity or guarantee that local evidence is untampered.

## Creator Recovery Centre

When the Creator returns, the recovery interface should show:
- a chronological incident timeline and confidence/verification status;
- which sessions were blocked or revoked;
- which capabilities remain disabled;
- whether encrypted memory and recovery material remain available;
- which devices are still trusted;
- the exact checks required before restoration.

Normal operation must not resume merely because an alert clears, a timer expires or the AI decides that the threat has passed. Require fresh authentication, server-verified Creator identity, an approved device, successful integrity/security checks, incident review and explicit Creator approval. Provide a carefully designed account-recovery route so a false positive or lost phone cannot permanently lock out the Creator; that route must not bypass identity verification.

## Authority separation

The conversational AI may request a defensive action but cannot:
- disable or rewrite the independent monitoring and policy enforcement;
- grant itself Creator status or approve a new trusted device;
- remove its own audit trail;
- permanently change trust roots or recovery authority;
- approve its own return from Secure Shelter.

The enforcement boundary must live outside the model's mutable prompt/context, ideally in a separately authenticated service and platform-protected client code. The model is not the root of trust.

Simon is the designated Creator in product policy. Actual privileged operations must still use the server-configured Creator principal ID verified from the authenticated session; a display name is not proof of authority.

## Offline behaviour and alerts

If the Creator is unavailable or the network is down, local policy may still block operations that the client can enforce and preserve a bounded, tamper-evident local event queue. It cannot revoke a server-side token while offline or guarantee alert delivery. Queue alerts and deliver them after a trusted connection returns. Never claim an alert was delivered when it was only queued.

## Implementation boundaries in this change

SecurityGuardianPolicy is a deterministic decision policy. It returns proposed defensive actions and a protection mode; it does not execute those actions. The following remain future work:
- connect detection signals to authenticated Android and gateway telemetry;
- implement server-side session/device revocation and sensitive-operation enforcement;
- implement a durable, privacy-minimised, tamper-evident audit queue;
- implement notification delivery and delivery receipts;
- build the Secure Shelter and Creator Recovery Centre UI;
- provision a separately secured recovery service and test lost-device recovery;
- test false positives, offline operation, compromised-client scenarios and multi-device recovery on real devices.

No Supabase schema, Edge Function, project setting, secret or deployment is changed by this protocol. Do not claim production protection until the enforcement services, monitoring, tests and operational recovery path are implemented and verified.


## Network and device incident evidence

The gateway also defines structured records for blocked access attempts. Records may include an edge-observed apparent IP address, bounded client-supplied user-agent, edge trace ID, authenticated account ID when token verification succeeded, attempted action category and blocked result. IP and user-agent evidence are explicitly unverified and must not be used to guess a person's identity or exact location.

No passwords, PINs, bearer/refresh tokens, API keys, cookies, private keys, recovery answers or request bodies are captured. Device identity remains unverified until a server-side enrolled-device signature and fresh challenge are validated. See [GATEWAY-SECURITY-INCIDENT-EVIDENCE.md](GATEWAY-SECURITY-INCIDENT-EVIDENCE.md).

At this stage records are written only to hosted Edge Function logs, not a durable incident database. Their retention is plan-dependent and may be short. A future database-backed incident store requires a reviewed migration, server-only writes, restrictive read access, retention limits and audit controls. No database migration or deployment is part of this PR.
