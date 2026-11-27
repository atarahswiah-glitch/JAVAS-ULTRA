-- Trusted-device approval: an existing VERIFIED device must sign a fresh challenge.
-- Branch-local only; do not apply to hosted Supabase without Creator approval.

alter table public.javas_device_challenges
  drop constraint if exists javas_device_challenges_purpose_check;
alter table public.javas_device_challenges
  add column if not exists target_device_id uuid;
alter table public.javas_device_challenges
  add constraint javas_device_challenges_purpose_check
  check (
    (purpose = 'ENROLL_DEVICE' and target_device_id is null)
    or
    (purpose = 'APPROVE_DEVICE' and target_device_id is not null)
  );

create index if not exists javas_device_challenges_approval_target_idx
  on public.javas_device_challenges (creator_user_id, target_device_id, created_at desc)
  where purpose = 'APPROVE_DEVICE';

create or replace function public.issue_javas_device_approval_challenge(
  p_challenge_id uuid,
  p_creator_user_id uuid,
  p_approver_device_id uuid,
  p_target_device_id uuid,
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
  if p_challenge_id is null or p_creator_user_id is null
     or p_approver_device_id is null or p_target_device_id is null
     or p_approver_device_id = p_target_device_id
     or (length(p_nonce_sha256_base64) <> 44 or right(p_nonce_sha256_base64, 1) <> '=' or p_nonce_sha256_base64 ~ '[^A-Za-z0-9+/=]') then
    raise exception 'Invalid approval challenge parameters' using errcode = '22023';
  end if;

  if p_expires_at < v_now + interval '15 seconds'
     or p_expires_at > v_now + interval '120 seconds' then
    raise exception 'Challenge expiry outside permitted window' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(
      p_creator_user_id::text || ':APPROVE:' || p_approver_device_id::text || ':' || p_target_device_id::text, 0
    )
  );

  if not exists (
    select 1 from public.javas_creator_devices approver
    where approver.creator_user_id = p_creator_user_id
      and approver.device_id = p_approver_device_id
      and approver.state = 'VERIFIED'
  ) or not exists (
    select 1 from public.javas_creator_devices target
    where target.creator_user_id = p_creator_user_id
      and target.device_id = p_target_device_id
      and target.state = 'PENDING_APPROVAL'
  ) then
    raise exception 'Approver or pending device state is invalid' using errcode = '42501';
  end if;

  select count(*) into v_recent_count
  from public.javas_device_challenges
  where creator_user_id = p_creator_user_id
    and device_id = p_approver_device_id
    and purpose = 'APPROVE_DEVICE'
    and created_at > v_now - interval '10 minutes';

  select count(*) into v_open_count
  from public.javas_device_challenges
  where creator_user_id = p_creator_user_id
    and device_id = p_approver_device_id
    and purpose = 'APPROVE_DEVICE'
    and consumed_at is null and expires_at > v_now;

  if v_recent_count >= 5 or v_open_count >= 3 then
    raise exception 'Approval challenge rate limit exceeded' using errcode = 'P0001';
  end if;

  insert into public.javas_device_challenges (
    id, creator_user_id, device_id, target_device_id, purpose,
    nonce_sha256_base64, expires_at
  ) values (
    p_challenge_id, p_creator_user_id, p_approver_device_id, p_target_device_id,
    'APPROVE_DEVICE', p_nonce_sha256_base64, p_expires_at
  );
end;
$$;

create or replace function public.consume_javas_device_approval_challenge(
  p_challenge_id uuid,
  p_creator_user_id uuid,
  p_approver_device_id uuid,
  p_target_device_id uuid,
  p_nonce_sha256_base64 text
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
  if p_challenge_id is null or p_creator_user_id is null
     or p_approver_device_id is null or p_target_device_id is null
     or (length(p_nonce_sha256_base64) <> 44 or right(p_nonce_sha256_base64, 1) <> '=' or p_nonce_sha256_base64 ~ '[^A-Za-z0-9+/=]') then
    raise exception 'Invalid approval proof parameters' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(
      p_creator_user_id::text || ':APPROVE:' || p_approver_device_id::text || ':' || p_target_device_id::text, 0
    )
  );

  update public.javas_device_challenges challenge
  set consumed_at = v_now
  where challenge.id = p_challenge_id
    and challenge.creator_user_id = p_creator_user_id
    and challenge.device_id = p_approver_device_id
    and challenge.target_device_id = p_target_device_id
    and challenge.purpose = 'APPROVE_DEVICE'
    and challenge.nonce_sha256_base64 = p_nonce_sha256_base64
    and challenge.consumed_at is null
    and challenge.expires_at > v_now
    and exists (
      select 1 from public.javas_creator_devices approver
      where approver.creator_user_id = p_creator_user_id
        and approver.device_id = p_approver_device_id
        and approver.state = 'VERIFIED'
    )
    and exists (
      select 1 from public.javas_creator_devices target
      where target.creator_user_id = p_creator_user_id
        and target.device_id = p_target_device_id
        and target.state = 'PENDING_APPROVAL'
    )
  returning challenge.id into v_consumed_id;

  if v_consumed_id is null then
    return;
  end if;

  update public.javas_creator_devices target
  set state = 'VERIFIED', approved_at = v_now
  where target.creator_user_id = p_creator_user_id
    and target.device_id = p_target_device_id
    and target.state = 'PENDING_APPROVAL';

  if not found then
    raise exception 'Pending device changed during approval' using errcode = '40001';
  end if;

  return query select p_target_device_id, 'VERIFIED'::text;
end;
$$;

revoke all on function public.issue_javas_device_approval_challenge(uuid, uuid, uuid, uuid, text, timestamptz)
  from public, anon, authenticated;
revoke all on function public.consume_javas_device_approval_challenge(uuid, uuid, uuid, uuid, text)
  from public, anon, authenticated;
grant execute on function public.issue_javas_device_approval_challenge(uuid, uuid, uuid, uuid, text, timestamptz)
  to service_role;
grant execute on function public.consume_javas_device_approval_challenge(uuid, uuid, uuid, uuid, text)
  to service_role;
