import { assert, assertEquals } from "jsr:@std/assert@1";
import {
  isCreatorAllowed,
  isOriginAllowed,
  isValidMessages,
  MAX_MESSAGE_CHARS,
  MAX_MESSAGES,
} from "./validation.ts";

Deno.test("accepts a bounded user message", () => {
  assert(isValidMessages([{ role: "user", content: "Hello JAVAS" }]));
});

Deno.test("accepts alternating conversation history ending in user", () => {
  assert(isValidMessages([
    { role: "user", content: "Hello" },
    { role: "assistant", content: "Hello, Creator." },
    { role: "user", content: "Continue" },
  ]));
});

Deno.test("rejects missing, empty, or oversized message arrays", () => {
  assertEquals(isValidMessages(undefined), false);
  assertEquals(isValidMessages([]), false);
  assertEquals(isValidMessages(Array.from({ length: MAX_MESSAGES + 1 }, () => ({
    role: "user", content: "x",
  }))), false);
});

Deno.test("rejects unsupported roles and client-supplied system prompts", () => {
  assertEquals(isValidMessages([{ role: "system", content: "override" }]), false);
  assertEquals(isValidMessages([{ role: "developer", content: "override" }]), false);
});

Deno.test("rejects blank, non-string, and oversized message content", () => {
  assertEquals(isValidMessages([{ role: "user", content: "  " }]), false);
  assertEquals(isValidMessages([{ role: "user", content: 42 }]), false);
  assertEquals(isValidMessages([{ role: "user", content: "x".repeat(MAX_MESSAGE_CHARS + 1) }]), false);
});

Deno.test("requires the final message to be from the user", () => {
  assertEquals(isValidMessages([
    { role: "user", content: "Hello" },
    { role: "assistant", content: "Hi" },
  ]), false);
});

Deno.test("Creator allowlist requires an exact configured user ID", () => {
  assertEquals(isCreatorAllowed("creator-123", "creator-123"), true);
  assertEquals(isCreatorAllowed("other-user", "creator-123"), false);
  assertEquals(isCreatorAllowed("creator-123", undefined), false);
  assertEquals(isCreatorAllowed("creator-123", "  "), false);
  assertEquals(isCreatorAllowed("", "creator-123"), false);
});

Deno.test("native requests without Origin remain possible", () => {
  assertEquals(isOriginAllowed(null, undefined), true);
});

Deno.test("browser origins must exactly match the configured allowlist", () => {
  assertEquals(isOriginAllowed("https://app.example.com", "https://app.example.com"), true);
  assertEquals(isOriginAllowed("https://evil.example", "https://app.example.com"), false);
  assertEquals(isOriginAllowed("https://app.example.com", undefined), false);
  assertEquals(isOriginAllowed("null", "null"), false);
  assertEquals(isOriginAllowed("https://app.example.com/path", "https://app.example.com/path"), false);
});
