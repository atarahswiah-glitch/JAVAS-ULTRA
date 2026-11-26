begin;

create extension if not exists pgtap with schema extensions;
select plan(8);

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
    '99999999-9999-4999-8999-999999999999',
    '33333333-3333-4333-8333-333333333333',
    'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
    repeat('A', 100),
    'BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB='
  )),
  0::bigint,
  'challenge cannot be consumed for a different Creator'
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

select public.issue_javas_device_enrollment_challenge(
  '44444444-4444-4444-8444-444444444444',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
  'CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=',
  pg_catalog.clock_timestamp() + interval '30 seconds'
);

update public.javas_device_challenges
set created_at = pg_catalog.clock_timestamp() - interval '2 seconds',
    expires_at = pg_catalog.clock_timestamp() - interval '1 second'
where id = '44444444-4444-4444-8444-444444444444';

select is(
  (select count(*) from public.consume_javas_device_enrollment_challenge(
    '44444444-4444-4444-8444-444444444444',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    'CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=',
    repeat('A', 100),
    'DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD='
  )),
  0::bigint,
  'expired challenge cannot be consumed'
);

select public.issue_javas_device_enrollment_challenge(
  '55555555-5555-4555-8555-555555555555',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
  'EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=',
  pg_catalog.clock_timestamp() + interval '60 seconds'
);

update public.javas_device_challenges
set consumed_at = pg_catalog.clock_timestamp()
where id = '55555555-5555-4555-8555-555555555555';
select public.issue_javas_device_enrollment_challenge(
  '66666666-6666-4666-8666-666666666666',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
  'FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF=',
  pg_catalog.clock_timestamp() + interval '60 seconds'
);

update public.javas_device_challenges
set consumed_at = pg_catalog.clock_timestamp()
where id = '66666666-6666-4666-8666-666666666666';
select public.issue_javas_device_enrollment_challenge(
  '77777777-7777-4777-8777-777777777777',
  '22222222-2222-4222-8222-222222222222',
  '33333333-3333-4333-8333-333333333333',
  'GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG=',
  pg_catalog.clock_timestamp() + interval '60 seconds'
);

update public.javas_device_challenges
set consumed_at = pg_catalog.clock_timestamp()
where id = '77777777-7777-4777-8777-777777777777';

select throws_ok(
  $$ select public.issue_javas_device_enrollment_challenge(
    '88888888-8888-4888-8888-888888888888',
    '22222222-2222-4222-8222-222222222222',
    '33333333-3333-4333-8333-333333333333',
    'HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH=',
    pg_catalog.clock_timestamp() + interval '60 seconds'
  ) $$,
  'P0001',
  'Challenge rate limit exceeded',
  'database rate limit rejects the sixth challenge in ten minutes'
);

reset role;

select ok(
  not has_table_privilege('anon', 'public.javas_creator_devices', 'select')
  and not has_table_privilege('authenticated', 'public.javas_creator_devices', 'select')
  and not has_table_privilege('anon', 'public.javas_device_challenges', 'select')
  and not has_table_privilege('authenticated', 'public.javas_device_challenges', 'select')
  and not has_function_privilege(
    'anon',
    'public.issue_javas_device_enrollment_challenge(uuid,uuid,uuid,text,timestamp with time zone)',
    'execute'
  )
  and not has_function_privilege(
    'authenticated',
    'public.consume_javas_device_enrollment_challenge(uuid,uuid,uuid,text,text,text)',
    'execute'
  ),
  'client roles cannot read records or invoke privileged challenge RPCs'
);

select * from finish();
rollback;
