# Creator authentication session — implementation notes

The Android auth client is `SupabaseAuthSessionClient`.

- Uses Supabase Auth password grant and refresh-token grant over HTTPS.
- Password is used only for the login request and is not stored.
- Access and refresh tokens are stored together encrypted using AES-256-GCM with an Android Keystore key.
- Access tokens are refreshed before expiry; transient network failure does not discard an otherwise unexpired token.
- Local sign-out clears the encrypted session even when the network is unavailable.
- Implements `JavasAccessTokenProvider`, so it can be injected into `JavasGatewayConversationEngine`.

Configuration is intentionally not hard-coded. The Android build must supply the JAVAS Supabase project URL and publishable key from local Gradle properties or CI secrets. Never supply a Supabase secret/service-role key to Android.

**Not yet active:** the login UI and MainActivity wiring are separate integration work. No project URL/key, account credentials, or backend settings were added. The Supabase project has not been contacted or modified.

**Authorization requirement:** successful Supabase authentication proves the account is valid; it does not by itself prove the account is the JAVAS Creator. The Edge Function must additionally compare the verified user ID with a server-side Creator allowlist before enabling private companion access.