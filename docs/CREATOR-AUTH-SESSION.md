# Creator authentication session — implementation notes

The Android auth client is `SupabaseAuthSessionClient`.

- Uses Supabase Auth password grant and refresh-token grant over HTTPS.
- Password is used only for the login request and is not stored.
- Access and refresh tokens are stored together encrypted using AES-256-GCM with an Android Keystore key.
- Access tokens are refreshed before expiry; transient network failure does not discard an otherwise unexpired token.
- Local sign-out clears the encrypted session even when the network is unavailable.
- Implements `JavasAccessTokenProvider`, so it can be injected into `JavasGatewayConversationEngine`.

Configuration is intentionally not hard-coded. The Android build must supply the JAVAS Supabase project URL and publishable key from local Gradle properties or CI secrets. Never supply a Supabase secret/service-role key to Android.

The Compose screen now includes email/password sign-in and first-run account creation. Registration requires a password of at least 12 characters and confirmation in the UI. Supabase email confirmation is respected: if signup returns no session, the user is told to confirm their email before signing in. The client never treats account registration as proof of Creator authority. MainActivity creates the auth session provider and gateway client only when both build configuration values are present. Otherwise the UI remains safely unconfigured.

Set these Gradle properties in your user-level `~/.gradle/gradle.properties` (do not commit the file or put secrets in source):
```properties
JAVAS_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
JAVAS_SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
The publishable key is intended for client use; never use a Supabase secret/service-role key here. No project URL/key, account credentials, or backend secrets were added to the repository. The separate development Supabase project has an incident archive table, but this account-registration UI has not changed its Auth settings or created any user accounts.

**Authorization requirement:** successful Supabase authentication proves the account is valid; it does not by itself prove the account is the JAVAS Creator. The Edge Function must additionally compare the verified user ID with a server-side Creator allowlist before enabling private companion access.

## First-run account creation — feature branch

- Android calls Supabase Auth `/signup` over HTTPS.
- The password must be at least 12 characters and is not persisted; the client clears the submitted character array after constructing the request body.
- If email confirmation is enabled, no local session is stored until the user confirms and signs in.
- If Supabase returns a session immediately, the encrypted session store is used as for sign-in.
- This creates an ordinary authenticated account only. It does not grant Creator status, populate a Creator allowlist, or enable the incident-history endpoint.
- A separate one-time, server-side Creator bootstrap/claim process is still required before this user can be recognized as the Creator.
