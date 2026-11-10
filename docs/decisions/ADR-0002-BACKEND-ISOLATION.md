# ADR-0002: Dedicated JAVAS Backend and Data Isolation

- **Status:** Accepted as a security boundary; provisioning deferred.
- **Date:** 2026-11-09

## Context

The connected Supabase account currently exposes a project named `SACCO-Management-Platform-Dev`. That project belongs to the user's separate SACCO platform. JAVAS has a different product identity, data model, permissions and security boundary.

## Decision

JAVAS must use a separately provisioned Supabase project (or another explicitly approved isolated backend) with its own:
- Auth configuration and user identity model.
- Database and migrations.
- Storage buckets and access policies.
- Edge Functions/secrets.
- Backup and recovery configuration.
- Environment-specific keys and service credentials.

The SACCO project must not be reused, modified, queried for JAVAS data, or used as a staging environment for JAVAS.

## Provisioning controls

- The Creator selects the Supabase organisation.
- Obtain and present the exact quoted cost before creating a resource if a charge applies.
- Do not create a paid project or enable paid services without explicit approval.
- Choose region based on available regions, latency, data protection, provider capability and cost; do not assume the closest region is available.
- Keep project URL and publishable client key in appropriate app configuration; never ship service-role/secret keys in Android.
- Use separate development and production environments when practical.
- Enable RLS on exposed schemas and design policies for actual ownership/permission boundaries.
- Store privileged authorization data in trusted server-side claims/tables, not user-editable profile metadata.

## Consequences

- No accidental coupling between the SACCO platform and JAVAS.
- Additional project/organisation administration and potentially additional cost.
- Backend implementation cannot start until the Creator selects an organisation and the isolated project is provisioned.

## Revisit when

- The Creator explicitly selects a different backend provider or approves a different isolation model.
