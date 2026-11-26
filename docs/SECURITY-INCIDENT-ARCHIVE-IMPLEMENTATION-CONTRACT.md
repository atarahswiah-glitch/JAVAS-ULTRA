# Durable security incident archive — implementation contract

Status: **design contract only**. This document does not create a database table, migration, Edge Function, live feed, or retention job.

## Purpose

Preserve a privacy-minimised record of security-boundary events so the Creator can review blocked attempts after transient platform logs expire. The archive is evidence of a recorded request and server decision; it is not proof of a person's identity, physical location, motive, or successful compromise.

## Trust boundary

1. Only the gateway/server may create an incident record after it has independently classified the event.
2. The server derives the authenticated account ID from a Supabase-verified access token. Never accept an owner ID, Creator ID, severity, trust label, or event outcome from an Android/client request.
3. The database must deny direct client INSERT, UPDATE, and DELETE. Ordinary signed-in clients must not be able to forge or erase incidents.
4. Creator reads must be authenticated and checked against the configured Creator account on the server. RLS is defence in depth, not a replacement for the server check.
5. No service-role key or privileged database credential may be shipped in the Android app or exposed to the conversational model.
6. The archive must not be queryable by other tenants, ordinary users, or another Creator account.

## Record contract

Persist only the allowlisted fields already defined by `security_telemetry.ts`:

- schema version, unique event ID, UTC occurrence time, fixed service name;
- bounded incident type, severity, attempted-action category, blocked outcome and HTTP response status;
- verified Supabase user ID, or null if authentication was not verified;
- apparent client IP only when obtained from the trusted deployment edge, labelled edge-observed/unverified;
- bounded user-agent labelled client-supplied/unverified;
- edge trace ID, if present;
- device identity status, remaining `not-verified` until a registered public key signs a fresh one-time challenge that the server verifies.

Do not add request bodies, prompts, conversation content, passwords, PINs, tokens, cookies, API keys, recovery answers, private keys, raw headers, or arbitrary client metadata. Do not enrich an IP into a person's name, address, or inferred identity.

## Immutability and integrity

- Event IDs must be unique and inserts idempotent.
- The normal application path is append-only. No update/delete endpoint is exposed.
- Corrections are represented by a new linked event, never silent rewriting.
- Record the server-generated timestamp and server-derived classification.
- Bound field lengths and reject malformed enum values at the server boundary.
- Apply per-account and per-edge rate limits so an attacker cannot exhaust storage by flooding rejected requests.
- A failed archive write must not turn a blocked request into an allowed request. Emit a minimal operational error without logging secrets.

## Creator read interface

The read API must:
- require a fresh, verified Creator session;
- return newest-first, cursor-paginated records with a strict page-size ceiling;
- expose only the allowlisted record fields;
- support filtering by time range, severity and incident type;
- audit successful and denied archive access;
- return no records to ordinary users or unverified devices where device verification is required by policy.

The Android Security & Recovery Centre must distinguish **recorded evidence**, **unverified clues**, and **not yet available** capabilities. It must never invent incidents or imply that an IP/user-agent identifies a human.

## Retention and deletion

Retention duration must be explicit, documented, and approved before deployment. A scheduled server-side purge must remove expired records and be tested against the policy. Any legal hold or preservation requirement must be explicit and audited. Do not promise permanent storage. Platform log retention and the durable archive are separate systems.

## Required implementation and verification gates

Before calling this feature live:

1. Confirm the intended development Supabase project is active and that the Creator has approved any resulting cost.
2. Generate the database migration through the approved Supabase CLI workflow; review the generated SQL rather than hand-writing an ad hoc migration.
3. Add table constraints, indexes, deny-by-default RLS policies, and a server-only insert path.
4. Add an authenticated, Creator-only, read-only Edge Function with pagination and access auditing.
5. Add rate limits, retention scheduling, and observable failure handling.
6. Add Deno tests for forged owner IDs, ordinary-user reads, cross-account access, direct client writes, duplicate event IDs, malformed records, pagination limits, retention expiry, and archive-write failure.
7. Connect the Android screen to the real endpoint and show empty/error/loading states without fabricating data.
8. Run CI, then test with a real approved Creator account and an ordinary account in the development environment. Do not use real incident claims as test fixtures on a live account.
9. Only after those checks may the UI label the archive as connected or live.

## Current implementation boundary — updated 2026-11-26

- The development Supabase project is ACTIVE_HEALTHY.
- The `public.javas_security_incidents` table and indexes have been applied to the dedicated development project.
- RLS is enabled. Direct SELECT/INSERT by `anon` and `authenticated` is denied. The `service_role` has SELECT and INSERT only; UPDATE and DELETE are not granted.
- The migration was generated by the Supabase CLI in GitHub Actions, tested against a clean local Supabase stack, and committed as `supabase/migrations/20261126143701_create_javas_security_incidents.sql`. Its version matches the remote migration history entry.
- Gateway-side row mapping and persistence code has been added to the feature branch. Its latest Deno/Android CI verification is pending; it has not been deployed to the hosted Edge Function.
- A separate Creator-only, read-only incident-history Edge Function scaffold has been added. It verifies the bearer token with Supabase Auth, checks the exact server-configured Creator user ID, caps page size at 50, validates pagination cursors, and returns no-store responses. It is not deployed; it fails closed until the Creator ID and server key are configured.
- No real incident records have been inserted as test data. The development archive currently has zero rows.
- The development project's Auth user count is currently zero. The exact Creator account UUID therefore cannot yet be configured or verified; the read endpoint must remain undeployed/fail-closed until the Creator account is registered and its UUID is explicitly confirmed.
- Android live incident feed, retention worker, session revocation, notifications, device challenge verification and actual Secure Shelter enforcement remain unimplemented.
- The security advisor reports an informational `RLS Enabled No Policy` finding. This is intentional at this stage: no client role receives table privileges, and no RLS policy grants client access. A future Creator read API must continue using a server-verified Creator check and must not add broad client SELECT policies.
