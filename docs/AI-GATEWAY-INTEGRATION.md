# JAVAS ULTRA — Authenticated AI Gateway

**Status:** Source implementation added on a stacked draft branch. Not deployed and not yet connected to a live Creator session.

## Flow

Android conversation UI → authenticated Supabase Edge Function → Anthropic Messages API → streamed SSE text → Android conversation UI.

The Android application sends a Supabase user access token in `Authorization: Bearer …` and the project publishable key in `apikey`. The Anthropic API key exists only as a Supabase Edge Function secret.

## Edge Function

Path: `supabase/functions/javas-gateway/index.ts`

Required Supabase Edge Function secrets:

- `ANTHROPIC_API_KEY` — Anthropic API key; never put this in Android, Git, build configuration or logs.
- `ANTHROPIC_MODEL` — optional; defaults to `claude-sonnet-5-5`.
- `JAVAS_CREATOR_USER_ID` — required Supabase Auth user UUID for the single authorized Creator; without it the function returns 503, and other authenticated users receive 403.
- `SUPABASE_URL` and `SUPABASE_ANON_KEY` — supplied by the Supabase Edge Function environment for the existing legacy client pattern.

Keep platform JWT verification enabled. The handler additionally calls `auth.getUser(accessToken)`, rejects unauthenticated callers, and checks the verified UUID against the server-only `JAVAS_CREATOR_USER_ID` allowlist. Authentication alone does not grant Creator access.

The request schema accepts only user/assistant messages, limits the number and size of messages, requires the final message to be from the user, and does not accept client-supplied system prompts or memories. The system prompt is server-owned.

## Android client

Path: `app/src/main/java/com/javasultra/app/core/ai/JavasGatewayClient.kt`

The client requires:
- The deployed Edge Function HTTPS URL.
- The project's publishable client key.
- A `JavasAccessTokenProvider` that returns the current, non-expired Supabase Auth access token.

The client uses `HttpURLConnection` and parses Anthropic `content_block_delta` SSE events without adding a third-party networking dependency. Calls must run off the main thread; `ConversationPanel` now does this on a background executor.

The screen exposes an injectable `ConversationEngine`. The default remains unavailable until the real Auth session provider and project configuration are supplied. This is deliberate: the app must never fabricate a Creator session or silently send unauthenticated requests.

## Memory and security boundary

The original proposal passed `memories` from Android. This implementation intentionally does not trust or accept client-provided memory. Server-side memory retrieval is a separate next step, after owner-scoped tables, migrations, RLS policies and negative authorization tests exist. Until then, the model receives the bounded conversation messages only.

This endpoint is conversational only. It does not grant Creator authority, approve actions, or expose tools. Durable rate limiting, usage budgets, audit events and abuse monitoring must be added before production exposure. An in-memory rate limiter is not sufficient for a distributed Edge Function.

## Verification

- Deno tests cover request validation.
- JVM tests cover request JSON encoding and SSE text-delta parsing.
- Android CI must pass on the exact PR head.
- A real streaming test requires a deployed function, configured secrets, a valid Supabase Auth user session and an Anthropic API key. None of those secrets are committed here.
- No Supabase project has been modified or deployed by this source-only change.
