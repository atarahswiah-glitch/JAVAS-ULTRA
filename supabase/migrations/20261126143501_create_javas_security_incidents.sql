create table public.javas_security_incidents (
  id uuid primary key default gen_random_uuid(),
  event_id uuid not null unique,
  schema_version smallint not null check (schema_version = 1),
  occurred_at timestamptz not null,
  service text not null check (service = 'javas-gateway'),
  incident_type text not null check (
    incident_type in (
      'ORIGIN_REJECTED',
      'AUTH_MISSING',
      'AUTH_INVALID',
      'CREATOR_CONFIGURATION_MISSING',
      'CREATOR_ACCESS_DENIED',
      'REQUEST_TOO_LARGE',
      'REQUEST_INVALID'
    )
  ),
  severity text not null check (severity in ('WARNING', 'HIGH', 'CRITICAL')),
  attempted_action varchar(160) not null,
  outcome text not null check (outcome = 'BLOCKED'),
  response_status smallint not null check (response_status between 100 and 599),
  authenticated_user_id uuid,
  apparent_client_ip varchar(64),
  ip_source text not null check (ip_source in ('cf-connecting-ip', 'unavailable')),
  ip_confidence text not null check (
    ip_confidence in ('edge-observed-unverified', 'unavailable')
  ),
  user_agent varchar(256),
  user_agent_confidence text not null check (
    user_agent_confidence in ('client-supplied-unverified', 'unavailable')
  ),
  edge_trace_id varchar(128),
  device_identity text not null default 'not-verified'
    check (device_identity = 'not-verified'),
  device_note varchar(320) not null,
  recorded_at timestamptz not null default now()
);

comment on table public.javas_security_incidents is
  'Privacy-minimised, server-recorded evidence of blocked JAVAS gateway requests. Not proof of human identity.';

comment on column public.javas_security_incidents.authenticated_user_id is
  'Supabase account UUID only when the gateway verified the access token; not proof of the human operating the device.';

comment on column public.javas_security_incidents.apparent_client_ip is
  'Edge-observed candidate only; explicitly unverified and not a human identity or precise-location claim.';

create index javas_security_incidents_occurred_at_idx
  on public.javas_security_incidents (occurred_at desc, id desc);

create index javas_security_incidents_severity_occurred_at_idx
  on public.javas_security_incidents (severity, occurred_at desc);

create index javas_security_incidents_type_occurred_at_idx
  on public.javas_security_incidents (incident_type, occurred_at desc);

alter table public.javas_security_incidents enable row level security;

revoke all privileges on table public.javas_security_incidents
  from anon, authenticated, service_role;

grant select, insert on table public.javas_security_incidents to service_role;
