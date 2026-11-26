begin;

select plan(5);

set local role service_role;

select public.issue_javas_device_enrollment_challenge(
  '11111111-1111-4111-8111-111111111111',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
  'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
  pg_catalog.clock_timestamp() + interval '60 seconds'
);

select is(
  (select count(*) from public.javas_device_challenges
   where id = '11111111-1111-4111-8111-111111111111'),
  1::bigint,
  'challenge issuance stores one digest-backed challenge'
);

select is(
  (select count(*) from public.consume_javas_device_enrollment_challenge(
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
    repeat('A', 100),
    'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB='
  )),
  1::bigint,
  'valid challenge is consumed and creates exactly one pending device'
);

select is(
  (select state from public.javas_creator_devices
   where creator_user_id = '22222222-2222-4222-8222-222222222222'
     and device_id = '33333333-3333-4333-8333-333333333333'),
  'PENDING_APPROVAL',
  'proof never auto-approves a device'
);

select is(
  (select count(*) from public.consume_javas_device_enrollment_challenge(
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
    repeat('A', 100),
    'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB='
  )),
  0::bigint,
  'replay of consumed challenge returns no device'
);

reset role;

select ok(
  not has_table_privilege('anon', 'public.javas_creator_devices', 'select')
  and not has_table_privilege('authenticated', 'public.javas_creator_devices', 'select')
  and not has_table_privilege('anon', 'public.javas_device_challenges', 'select')
  and not has_table_privilege('authenticated', 'public.javas_device_challenges', 'select'),
  'client roles cannot read device trust or challenge records'
);

select * from finish();
rollback;
