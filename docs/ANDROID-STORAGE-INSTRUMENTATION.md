# Android encrypted-storage instrumentation tests

The androidTest suite exercises the Android runtime boundary that JVM unit tests cannot validate:

- EncryptedMemoryRepository creates AES-GCM ciphertext through Android Keystore, stores proposed records encrypted, and treats exact retries idempotently.
- A conflicting existing record in a multi-record batch must roll back earlier inserts in that same SQLite transaction.
- EncryptedLearningJournalStore persists append-only event history and encrypted snapshots; replay of the same event ID/payload is idempotent and a changed payload is rejected.
- An injected failure while encrypting the updated journal snapshot must roll back the already-inserted event and leave the prior snapshot unchanged.

CI will compile these tests with assembleDebugAndroidTest. Compilation is not a claim that the tests have executed on an emulator or physical device. Runtime execution remains a separate acceptance step using:

    ./gradlew connectedDebugAndroidTest

A connected Android device/emulator with API 26 or later is required. The tests use unique Android Keystore aliases and delete their dedicated test databases before and after each run. They must never be pointed at a production data directory.

## Acceptance evidence

A runtime pass must show all four instrumentation tests passing and include the Android API/device image used. A failed Keystore operation must fail closed; do not replace the production encryptor with plaintext or an in-memory fallback to make tests pass.
