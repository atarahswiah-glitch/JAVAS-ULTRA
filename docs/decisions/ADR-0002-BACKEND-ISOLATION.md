# ADR-0002: Dedicated JAVAS Backend and Data Isolation

- **Status:** Dedicated JAVAS Supabase project provisioned; application integration and schema implementation deferred.
- **Decision date:** 2026-11-09
- **Provisioning verification:** 2026-11-11

## Context

JAVAS has a different product identity, data model, permissions and security boundary from the user's separate SACCO/Circle platform. Reusing the SACCO Supabase project is prohibited.

## Decision

JAVAS uses its own separately provisioned Supabase project:

- **Organisation:** `Mulungiproject`
- **Project:** `JAVAS-ULTRA`
- **Project reference:** `cfmjkwhvjlxyjdjbdtoi`
- **Region:** Mumbai (`ap-south-1`)
- **Database:** PostgreSQL 17 (verified server version 17.11.0.018)
- **Quoted project cost at provisioning:** USD $0/month

This project is separate from all SACCO/Circle projects. It is the designated JAVAS backend target; this decision does not mean the Android application is connected to it.

## Verified initial state

Read-only inspection confirmed:

- Project status: `ACTIVE_HEALTHY`.
- No tables in the exposed `public` schema.
- No database migrations recorded.
- No Edge Functions deployed.
- Security, performance and health advisor checks returned no findings at the time of inspection.

These are baseline observations only. An empty database and clean advisor output do not establish that application security, authentication, RLS, backup, or recovery has been implemented.

No database schema, migration, Edge Function, Auth configuration, secret, or production data was changed during provisioning verification.

## Required isolation and implementation controls

The JAVAS project must retain its own:
- Auth configuration and Creator identity model.
- Database schema and versioned migrations.
- Storage buckets and access policies.
- Edge Functions and server-side secrets.
- Backup and recovery configuration.
- Environment-specific client keys and service credentials.

Further controls:

- Never reuse, modify, query for JAVAS data, or stage JAVAS work in the SACCO project.
- Enable and test RLS on every exposed table; deny access by default and test both allowed and denied ownership/scope cases.
- Keep authoritative Creator, device, session, capability, approval and audit data server-controlled. Never authorize from user-editable metadata.
- Use only the publishable client key in Android. Never embed service-role or secret keys in the APK.
- Put privileged operations behind server-side validation, replay protection and audit recording.
- Use migrations and verify every applied change. Do not make ad-hoc production schema changes.
- Recheck current Supabase documentation and security guidance before implementation.

## Consequences

- JAVAS and SACCO/Circle data and credentials remain isolated.
- The dedicated project exists and is healthy, but it is still an empty backend foundation.
- Android authentication, session management, database access, RLS policies, storage, Edge Functions, sync and audit are not yet connected or implemented.
- Any change that could incur a charge or enable paid services requires a separate cost review and approval.

## Revisit when

- The Creator explicitly selects a different backend provider or approves a different isolation model.
