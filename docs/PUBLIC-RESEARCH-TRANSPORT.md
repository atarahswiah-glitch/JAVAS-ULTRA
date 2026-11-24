# JAVAS ULTRA — Public research transport

**Implementation status:** a bounded Android HTTPS transport and learning-provider orchestration have been added on the `feature/secure-public-research-transport` branch. This work has not yet been built or verified by CI and is not wired into the launcher or a production learning worker.

## Request path

`LearningCycleCoordinator`
→ `PublicResearchLearningProvider`
→ `PublicResearchDiscovery` (injected)
→ `DefaultPublicResearchService`
→ `OkHttpPublicResearchAdapter`
→ `EvidenceClaimExtractor` (injected)
→ `LearningEvidenceEvaluator`
→ Creator review handoff

The discovery adapter supplies candidate public pages. The transport retrieves and extracts bounded page text. The claim extractor must cite retrieved source IDs. The coordinator then validates evidence and creates a digest. Neither the discovery adapter nor the claim extractor is silently configured to a vendor or model provider in this change.

## Transport controls

- HTTPS only, port 443, GET only, no URL user-info, no authentication headers and no cookie jar.
- DNS lookup rejects private, loopback, link-local, multicast, reserved and non-global addresses. A DNS answer containing any unsafe address is rejected.
- Automatic redirects are disabled. At most three redirects are followed manually, and only when the target remains on the exact original HTTPS origin.
- The adapter fetches `/robots.txt` before each page request. A 404/410 means no robots file; unavailable or malformed transport fails closed. Matching `Disallow` rules deny the request. This parser is intentionally conservative and does not implement every robots.txt extension.
- Connect/read/write/call timeouts are bounded. Network requests use OkHttp's cancellable asynchronous call API.
- Robots content is limited to 128 KB. Page responses are limited to 1 MB, and extracted text is limited to 1 MB.
- HTML extraction removes scripts, styles, navigation, forms, embedded frames and other non-content elements. Plain text is supported. PDF, JavaScript execution, authentication, CAPTCHA/paywall bypass and write methods are not supported.
- Retrieved content remains untrusted. It cannot change system instructions, permissions, Creator approval, memory activation or tool policy.

## Remaining production dependencies

1. Implement a real `PublicResearchDiscovery` adapter using a Creator-approved public search source or curated source catalog.
2. Implement an `EvidenceClaimExtractor` backed by an approved model route, with strict structured-output parsing and claim-to-source validation.
3. Move internet egress to a trusted server-side proxy before treating this client transport as a production SSRF boundary. Client DNS validation is defence in depth, not a substitute for server-side egress controls.
4. Add integration tests for DNS, redirects, robots rules, TLS failures, timeouts, oversized/chunked responses, content extraction and cancellation.
5. Connect the provider to the learning settings UI and a bounded foreground/background execution controller only after Creator identity, consent and scheduling controls exist.

## Privacy and consent

Research is read-only and public-only. No Creator-private memory, local files, sensor data, account credentials or conversation history may be sent to discovery or extraction adapters unless a separate explicit policy and approval authorises that data flow. The current provider rejects candidates marked as containing personal data or requiring authentication.

## Verification status

- Source files and JVM policy tests have been added.
- No Gradle build, unit test, CI run, APK installation or device test has been performed for this branch in this work increment.
- The Android INTERNET permission is declared, but the research transport is not activated by the launcher or background worker.
