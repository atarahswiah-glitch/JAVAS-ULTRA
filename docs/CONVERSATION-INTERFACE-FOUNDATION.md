# Conversation interface foundation

This increment connects a native Compose conversation panel to the companion globe view.

## Implemented
- A multi-line message composer and bounded conversation transcript.
- A provider-neutral `ConversationEngine` interface for a future approved model adapter.
- Submission policy rejects blank input and normalizes prompts.
- The default engine is explicitly unavailable. When the Creator presses Send, the UI records a local status line stating that the message was not transmitted; it does not invent an AI response.
- Unit tests cover blank input, provider absence, normalized prompt delivery, and empty provider output.

## Not implemented by this increment
- No model API, API key, backend, network request, streaming response, tool routing, or durable conversation history is connected.
- No microphone capture, speech-to-text, text-to-speech, or wake-word service is enabled.
- No message is sent outside the device by the default engine.

## Integration requirements
A production adapter must be configured through a trusted backend or secure provider gateway. Secrets must not be embedded in the APK or stored in plaintext preferences. It must enforce Creator identity, privacy classification, request limits, cancellation, timeout, and audit policy before any network call. Conversation history must use the approved encrypted memory/storage layer and remain subject to retention controls.
