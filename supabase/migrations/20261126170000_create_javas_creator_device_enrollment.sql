-- Creator device-enrolment persistence.
-- This file is branch-local and has NOT been applied to the hosted Supabase project.

create table public.javas_creator_devices (
  id uuid primary key default gen_random_uuid(),
  creator_user_id uuid not null,
  device_id uuid not null,
  public_key_spki_base64 varchar(1024) not null,
  public_key_sha256_base64 varchar(64) not null,
  key_algorithm text not null default 'ECDSA_P256'
    check (key_algorithm = 'ECDSA_P256'),
  state text not null default 'PENDING_APPROVAL'
    check (state in ('PENDING_APPROVAL', 'VERIFIED', 'REVOKED')),
  created_at timestamptz not null default now(),
  approved_at timestamptz,
  revoked_at timestamptz,
  unique (creator_user_id, device_id),
  unique (creator_user_id, public_key_sha256_base64),
  check ((state = 'VERIFIED') = (approved_at is not null)),
  check ((state = 'REVOKED') = (revoked_at is not null))
);

comment on table public.javas_creator_devices is
  'Server-owned Creator device registry. A newly proved device remains PENDING_APPROVAL until separately approved.';
comment on column public.javas_creator_devices.public_key_spki_base64 is
  'Public key only. The corresponding private key must remain inside Android Keystore.';

create table public.javas_device_challenges (
  id uuid primary key,
  creator_user_id uuid not null,
  device_id uuid not null,
  purpose text not null check (purpose = 'ENROLL_DEVICE'),
  nonce_sha256_base64 varchar(64) not null,
  expires_at timestamptz not null,
  consumed_at timestamptz,
  created_at timestamptz not null default now(),
  check (expires_at > created_at)
);

comment on table public.javas_device_challenges is
  'Short-lived, one-use device proof challenges. Only a SHA-256 digest of the nonce is persisted.';

create index javas_device_challenges_creator_device_created_idx
  on public.javas_device_challenges (creator_user_id, device_id, created_at desc);
create index javas_device_challenges_expiry_idx
  on public.javas_device_challenges (expires_at)
  where consumed_at is null;

alter table public.javas_creator_devices enable row level security;
alter table public.javas_device_challenges enable row level security;

revoke all privileges on table public.javas_creator_devices from anon, authenticated;
revoke all privileges on table public.javas_device_challenges from anon, authenticated;
grant select, insert, update on table public.javas_creator_devices to service_role;
grant select, insert, update on table public.javas_device_challenges to service_role;

-- Serialize challenge issuance for each Creator/device pair and enforce rate limits
-- inside the database transaction rather than relying on a race-prone Edge check.
create or replace function public.issue_javas_device_enrollment_challenge(
  p_challenge_id uuid,
  p_creator_user_id uuid,
  p_device_id uuid,
  p_nonce_sha256_base64 text,
  p_expires_at timestamptz
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_now timestamptz := pg_catalog.clock_timestamp();
  v_recent_count integer;
  v_open_count integer;
begin
  if p_challenge_id is null or p_creator_user_id is null or p_device_id is null
     or p_nonce_sha256_base64 !~ '^[A-Za-z0-9+/]{43}=$' then
    raise exception 'Invalid challenge parameters' using errcode = '22023';
  end if;

  if p_expires_at < v_now + interval '15 seconds'
     or p_expires_at > v_now + interval '120 seconds' then
    raise exception 'Challenge expiry outside permitted window' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(p_creator_user_id::text || ':' || p_device_id::text, 0)
  );

  select count(*) into v_recent_count
  from public.javas_device_challenges
  where creator_user_id = p_creator_user_id
    and device_id = p_device_id
    and created_at > v_now - interval '10 minutes';

  select count(*) into v_open_count
  from public.javas_device_challenges
  where creator_user_id = p_creator_user_id
    and device_id = p_device_id
    and consumed_at is null
    and expires_at > v_now;

  if v_recent_count >= 5 or v_open_count >= 3 then
    raise exception 'Challenge rate limit exceeded' using errcode = 'P0001';
  end if;

  insert into public.javas_device_challenges (
    id, creator_user_id, device_id, purpose, nonce_sha256_base64, expires_at
  ) values (
    p_challenge_id, p_creator_user_id, p_device_id, 'ENROLL_DEVICE',
    p_nonce_sha256_base64, p_expires_at
  );
end;
$$;

-- Consume the challenge and create its PENDING_APPROVAL device row in one transaction.
-- A replay or concurrent second caller receives zero rows.
create or replace function public.consume_javas_device_enrollment_challenge(
  p_challenge_id uuid,
  p_creator_user_id uuid,
  p_device_id uuid,
  p_nonce_sha256_base64 text,
  p_public_key_spki_base64 text,
  p_public_key_sha256_base64 text
)
returns table (device_id uuid, device_state text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_now timestamptz := pg_catalog.clock_timestamp();
  v_consumed_id uuid;
begin
  if p_challenge_id is null or p_creator_user_id is null or p_device_id is null
     or p_nonce_sha256_base64 !~ '^[A-Za-z0-9+/]{43}=$'
     or p_public_key_sha256_base64 !~ '^[A-Za-z0-9+/]{43}=$'
     or p_public_key_spki_base64 is null
     or length(p_public_key_spki_base64) < 80
     or length(p_public_key_spki_base64) > 1024 then
    raise exception 'Invalid proof parameters' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(p_creator_user_id::text || ':' || p_device_id::text, 0)
  );

  update public.javas_device_challenges as challenge
  set consumed_at = v_now
  where challenge.id = p_challenge_id
    and challenge.creator_user_id = p_creator_user_id
    and challenge.device_id = p_device_id
    and challenge.purpose = 'ENROLL_DEVICE'
    and challenge.nonce_sha256_base64 = p_nonce_sha256_base64
    and challenge.consumed_at is null
    and challenge.expires_at > v_now
  returning challenge.id into v_consumed_id;

  if v_consumed_id is null then
    return;
  end if;

  insert into public.javas_creator_devices (
    creator_user_id, device_id, public_key_spki_base64, public_key_sha256_base64
  ) values (
    p_creator_user_id, p_device_id, p_public_key_spki_base64, p_public_key_sha256_base64
  );

  return query select p_device_id, 'PENDING_APPROVAL'::text;
end;
$$;

revoke all on function public.issue_javas_device_enrollment_challenge(uuid, uuid, uuid, text, timestamptz)
  from public, anon, authenticated;
revoke all on function public.consume_javas_device_enrollment_challenge(uuid, uuid, uuid, text, text, text)
  from public, anon, authenticated;
grant execute on function public.issue_javas_device_enrollment_challenge(uuid, uuid, uuid, text, timestamptz)
  to service_role;
grant execute on function public.consume_javas_device_enrollment_challenge(uuid, uuid, uuid, text, text, text)
  to service_role;
