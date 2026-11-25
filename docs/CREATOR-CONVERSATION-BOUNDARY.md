# Creator identity and protected conversation boundary

**Status:** Domain orchestration source and JVM test source added; provider/backend integration and all runtime verification remain pending.

## Identity boundary

The existing Creator session coordinator authenticates through injected gateways, verifies the Creator account and registered device, and stores provider credentials only as encrypted Android Keystore-backed ciphertext. This branch also corrects two static-review defects in session restoration: the refreshed credential is now replaceable during restore, and best-effort revocation accepts an omitted refresh token.

The coordinator is not wired to a production authentication provider. Its device identifier is app-scoped and random; it is not hardware attestation. A trusted backend must independently validate each token, account, device, grant and policy decision.

## Protected conversation pipeline

`ProtectedConversationOrchestrator.kt` adds a bounded text conversation path that:

1. Rejects invalid, expired, local-only or stale authentication context before reading memory.
2. Requests only Creator-approved memory context for the authenticated principal.
3. Applies message, memory-context, source-count and output bounds.
4. Marks the assembled request as `CREATOR_PRIVATE`.
5. Relies on the model routing policy to select only a provider that explicitly accepts that classification; offline mode permits on-device providers only.
6. Enforces a bounded provider deadline and validates the returned response.
7. Rejects all model-proposed tool calls in this chat path. It does not execute tools.
8. Does not persist conversation transcripts.

The companion prompt establishes honesty, respectful communication, evidence awareness and resistance to instructions embedded in retrieved memory. It does not claim consciousness or abilities that are not connected.

## Verification added

- Added `ProtectedConversationOrchestratorTest.kt` covering identity rejection, expiry, stale authentication, input bounds, memory failure, provider failure, private-data classification, offline routing and rejection of tool proposals.
- Model responses now carry the selected provider ID from `DefaultModelGateway`.
- Static review fixed the Creator session restore credential reassignment and optional refresh-token revocation call.

## Not yet complete

- No production Creator authentication/account/device gateway.
- No server-side token validation or device attestation.
- No model provider adapter or approved API credentials.
- No concrete memory repository adapter wired to the conversation context provider.
- No Compose sign-in, chat input, transcript persistence or memory-management UI integration.
- No real tool host, voice, scheduler or notification integration.

These are intentionally not simulated. Production provider, backend and credential choices remain protected integration decisions.

## Verification status

No Gradle build, unit test, instrumentation test, CI run, APK generation, installation or device test was performed for this increment. The tests are authored but unexecuted.
