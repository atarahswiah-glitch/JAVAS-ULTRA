# JAVAS ULTRA — Creator identity and session foundation

**Implementation status:** client-side session orchestration, credential codec, encrypted Android session vault and app-scoped random device ID have been added on `feature/creator-session-foundation`. No production authentication provider or trusted identity backend is configured in this increment. The code is not yet wired into the Android launcher.

## Authentication flow

1. The Creator supplies an identifier and a short-lived secret to the injected `CreatorAuthenticationGateway`. The secret is passed as a `CharArray` and cleared from the caller's array in a `finally` block.
2. The provider returns an opaque access/refresh-token pair plus principal, issue/expiry times and authentication strength.
3. JAVAS loads the Creator account from the trusted `CreatorAccountGateway`; the principal must match and the account must be active.
4. JAVAS obtains a random app-scoped device ID. The trusted backend must return a matching `VERIFIED` device registration.
5. `CreatorIdentityGate` performs client-side session, principal, device and authentication-proof checks.
6. Only then is the session encoded and encrypted with AES-GCM using an Android Keystore key and stored as ciphertext in private SharedPreferences.
7. On app restore, JAVAS refreshes an expired access token through the provider without increasing the original authentication strength, then rechecks account and device state with the trusted backend.
8. Sign-out clears local ciphertext and asks the provider to revoke both access and refresh credentials. The result reports whether server revocation was confirmed.

## Local storage controls

- Access and refresh tokens are never stored as plaintext preferences.
- Session payloads are versioned, bounded and validated before encoding and after decryption.
- AES-GCM uses a non-exportable Android Keystore key and associated data binding the session format.
- The app manifest disables Android backup; the session ciphertext is not intended to migrate to another device.
- Device ID is a random app-scoped UUID, not an IMEI, serial number or advertising ID.
- Keystore invalidation or ciphertext corruption fails closed and requires reauthentication/recovery.

## Important security boundaries

- The authentication and account gateways are interfaces only. No Supabase/Auth0/Firebase provider has been selected or configured here.
- A locally stored token is not proof that an account remains active. Restore checks the trusted account and device services.
- The client-side identity gate is not authoritative. Every server operation must independently validate the token, account state, device, permissions, scope, approval and audit requirements.
- Device UUID alone is not cryptographic device attestation. Production device enrollment should add a Keystore-backed signing key and server challenge verification.
- This foundation does not yet implement recovery OTP, MFA enrollment, session listing/revocation across devices, capability-grant retrieval, trusted audit writes or a login screen.
- No credential, token, Supabase key, service-role secret or signing material is committed.

## Remaining work before Creator login is production-ready

1. Connect the approved JAVAS authentication provider and account/device APIs using protected configuration; do not embed privileged keys in the APK.
2. Add server-side device challenge verification backed by an Android Keystore signing key.
3. Implement recovery, MFA/step-up, refresh-token rotation failure handling and remote session/device revocation.
4. Connect the session coordinator to app startup, lock/resume behaviour and logout UI.
5. Add Android instrumentation tests for Keystore encryption, process restart, ciphertext corruption, key invalidation, device-ID persistence and logout.
6. Complete threat-model review and trusted backend negative tests.

## Verification status

- JVM codec and coordinator tests have been added but not executed.
- Android Keystore/SharedPreferences behaviour has not been instrumented or tested on a device.
- No Gradle build, CI run, APK install or physical-device test has been performed for this branch.
