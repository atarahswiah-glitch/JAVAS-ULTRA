# Encrypted conversation history

## Implemented in this increment
- Adds a separate SQLite-backed conversation history store.
- Encrypts message payloads with AES-256-GCM using a dedicated, non-exportable Android Keystore key alias.
- Binds ciphertext to message ID, owner principal and conversation ID through authenticated additional data.
- Keeps only opaque SHA-256 owner/thread indexes, message ID and creation timestamp outside ciphertext.
- Enforces owner-scoped append, read and delete operations.
- Makes same-ID exact retries idempotent and rejects conflicting reuse.
- Bounds message size, read batch size and codec payload size.
- Uses secure-delete SQLite configuration and a versioned binary codec.
- Adds unit tests for codec round-trip, invalid input, owner isolation, request limits and corrupt codec data.

## Important integration boundary
The store is deliberately not called by the Compose screen yet. The app does not yet have a verified production Creator identity/session binding, so wiring persistence to an untrusted or placeholder identity would create false security. Once the trusted Creator authentication layer supplies a stable principal ID, the conversation UI can receive a scoped history repository and load/append/delete only that principal's threads.

## Not included
- No server backup or cross-device sync.
- No model provider or network transmission.
- No conversation summarization or memory extraction.
- No auto-retention schedule. The Creator must have explicit history controls before history is considered production-ready.
- No physical-device or emulator instrumentation run in this increment.

## Security notes
- The Android Keystore key is device-local and non-exportable; losing/uninstalling app data or invalidating the key may make local history unrecoverable.
- SQLite secure-delete is a best-effort local storage control, not a guarantee against all forensic recovery on rooted/compromised devices.
- The backend must independently authenticate and authorize any future sync/export endpoint.
