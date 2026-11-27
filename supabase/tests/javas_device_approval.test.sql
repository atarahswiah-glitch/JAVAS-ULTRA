begin;
create extension if not exists pgtap with schema extensions;
select plan(5);
set local role service_role;

insert into public.javas_creator_devices (
  creator_user_id, device_id, public_key_spki_base64, public_key_sha256_base64,
  state, approved_at
) values (
  '22222222-2222-4222-8222-222222222222',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  repeat('A', 100),
  'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=',
  'VERIFIED',
  pg_catalog.clock_timestamp()
), (
  '22222222-2222-4222-8222-222222222222',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  repeat('C', 100),
  'DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=',
  'PENDING_APPROVAL',
  null
);

select public.issue_javas_device_approval_challenge(
  'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  '22222222-2222-4222-8222-222222222222',
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=',
  pg_catalog.clock_timestamp() + interval '60 seconds'
);

select is(
  (select count(*) from public.javas_device_challenges
   where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
     and purpose = 'APPROVE_DEVICE'
     and device_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
     and target_device_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
  1::bigint,
  'approval challenge binds trusted approver and pending target'
);

select is(
  (select count(*) from public.consume_javas_device_approval_challenge(
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    '22222222-2222-4222-8222-222222222222',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE='
  )),
  0::bigint,
  'pending device cannot approve itself or substitute as approver'
);

select is(
  (select count(*) from public.consume_javas_device_approval_challenge(
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    '22222222-2222-4222-8222-222222222222',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE='
  )),
  1::bigint,
  'verified trusted device can approve the bound pending device'
);

select is(
  (select state from public.javas_creator_devices
   where creator_user_id = '22222222-2222-4222-8222-222222222222'
     and device_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
  'VERIFIED',
  'approval atomically changes pending device to verified'
);

select is(
  (select count(*) from public.consume_javas_device_approval_challenge(
    'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    '22222222-2222-4222-8222-222222222222',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE='
  )),
  0::bigint,
  'approval challenge cannot be replayed'
);

reset role;
select * from finish();
rollback;
