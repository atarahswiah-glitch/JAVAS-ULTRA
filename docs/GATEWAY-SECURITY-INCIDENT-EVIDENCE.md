# Gateway security incident evidence

The gateway emits structured `JAVAS_SECURITY_INCIDENT` events for blocked requests at the security boundary. Each record is intentionally limited to evidence needed to review an attempted action.

## Captured fields

- UTC timestamp and random event ID.
- Fixed gateway service name and a bounded incident category.
- Severity, attempted action category, blocked outcome and HTTP status.
- Authenticated Supabase user ID only when token verification succeeded.
- Apparent client IP from `cf-connecting-ip`, when present, explicitly labelled **edge-observed and unverified**.
- Bounded user-agent string, explicitly labelled **client-supplied and unverified**.
- Cloudflare trace identifier when present.
- Device identity status, which remains **not verified** until the Android device's enrolled public key and a fresh signed challenge are verified by the server.

## Never collect as incident evidence

Never log request bodies, passwords, PINs, bearer tokens, refresh tokens, API keys, cookies, private keys, recovery answers, or raw authorization headers. Do not log all request headers. Do not send incident metadata to the conversational model or the external AI provider.

## Interpreting identity evidence

An IP address identifies a network egress point, not necessarily a person or a unique device. VPNs, proxies, carrier NAT, shared Wi-Fi and compromised devices can make it misleading. A user-agent is supplied by the client and can be forged. Neither is proof of identity or intent.

The first `X-Forwarded-For` value is not used because it can contain client-supplied data. The implementation uses only `cf-connecting-ip` as an edge-observed candidate and still labels it unverified. The actual trust properties of headers must be revalidated for the deployed hosting path.

The authenticated Supabase user ID is an account identifier, not a password or proof that a particular human was physically operating the device. A device may only be identified as enrolled after the server verifies a registered public key's signature over a fresh one-time challenge. The current device-key PR provides client-side signing groundwork; server enrollment and verification are still pending.

## Retention and availability

The current implementation writes structured events to Edge Function logs only. It does not create durable incident history. Supabase log retention depends on the plan (currently documented as 1 day on Free, 7 days on Pro, 28 days on Team and 90 days on Enterprise). Do not promise long-term evidence retention from platform logs.

A future durable incident store must be implemented through a reviewed Supabase migration and server-only insert path, with RLS denying ordinary clients any ability to insert, update or delete evidence. Creator access should be read-only through a narrowly scoped authenticated interface. Apply a defined retention period, rate limits, access auditing and privacy controls. No database table or deployment is created by this change.

## Creator-facing report

The Creator Recovery Centre should render the event category in plain language, show the observed IP and user-agent with their uncertainty labels, show the account ID only when verified, list the exact blocked action and response, and state which functions remain restricted. It must not guess a person's name, location or motives from an IP address.
