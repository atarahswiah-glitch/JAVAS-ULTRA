import {
  hasRecentStepUp,
  isUuid,
  readVerifiedJwtClaims,
} from "./auth.ts";

const NOW = 1_795_713_600;

Deno.test("accepts recent aal2 with a recent second-factor method", () => {
  if (!hasRecentStepUp({
    aal: "aal2",
    auth_time: NOW - 30,
    amr: [{ method: "totp", timestamp: NOW - 10 }],
  }, NOW)) throw new Error("Fresh MFA was rejected");
});

Deno.test("rejects aal1 even when an amr entry looks recent", () => {
  if (hasRecentStepUp({
    aal: "aal1",
    auth_time: NOW - 10,
    amr: [{ method: "totp", timestamp: NOW - 5 }],
  }, NOW)) throw new Error("AAL1 was accepted");
});

Deno.test("rejects stale, future and first-factor-only authentication", () => {
  const stale = hasRecentStepUp({
    aal: "aal2",
    auth_time: NOW - 600,
    amr: [{ method: "totp", timestamp: NOW - 600 }],
  }, NOW);
  const future = hasRecentStepUp({
    aal: "aal2",
    auth_time: NOW + 60,
    amr: [{ method: "totp", timestamp: NOW + 60 }],
  }, NOW);
  const firstFactor = hasRecentStepUp({
    aal: "aal2",
    auth_time: NOW - 5,
    amr: [{ method: "password", timestamp: NOW - 5 }],
  }, NOW);
  if (stale || future || firstFactor) throw new Error("Invalid step-up evidence was accepted");
});

Deno.test("decodes only structurally valid JWT payloads", () => {
  const payload = btoa(JSON.stringify({ sub: "creator", aal: "aal2" }));
  const claims = readVerifiedJwtClaims(`header.${payload}.signature`);
  if (claims?.sub !== "creator") throw new Error("JWT payload was not decoded");
  if (readVerifiedJwtClaims("not-a-jwt") !== null) {
    throw new Error("Malformed JWT was accepted");
  }
});

Deno.test("validates UUID input", () => {
  if (!isUuid("33333333-3333-4333-8333-333333333333")) {
    throw new Error("Valid UUID rejected");
  }
  if (isUuid("creator-device") || isUuid(null)) {
    throw new Error("Invalid UUID accepted");
  }
});
