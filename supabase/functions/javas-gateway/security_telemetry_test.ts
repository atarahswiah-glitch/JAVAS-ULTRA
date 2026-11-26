import { assert, assertEquals } from "jsr:@std/assert@1";
import { buildSecurityIncident } from "./security_telemetry.ts";

Deno.test("incident records edge-observed IP, user agent and trace as unverified evidence", () => {
  const req = new Request("https://example.test/functions/v1/javas-gateway", {
    headers: {
      "cf-connecting-ip": "203.0.113.24",
      "user-agent": "JAVAS-test-client/1.0",
      "cf-ray": "trace-123",
      "x-forwarded-for": "198.51.100.88",
      "authorization": "Bearer MUST_NOT_BE_RECORDED",
    },
  });

  const incident = buildSecurityIncident(req, {
    incidentType: "CREATOR_ACCESS_DENIED",
    severity: "HIGH",
    attemptedAction: "Use gateway without Creator authorization",
    responseStatus: 403,
    authenticatedUserId: "auth-user-123",
    now: new Date("2026-11-26T12:00:00.000Z"),
    eventId: "incident-1",
  });

  assertEquals(incident.networkEvidence.apparentClientIp, "203.0.113.24");
  assertEquals(incident.networkEvidence.ipSource, "cf-connecting-ip");
  assertEquals(incident.networkEvidence.ipConfidence, "edge-observed-unverified");
  assertEquals(incident.networkEvidence.userAgent, "JAVAS-test-client/1.0");
  assertEquals(incident.networkEvidence.edgeTraceId, "trace-123");
  assertEquals(incident.deviceEvidence.deviceIdentity, "not-verified");
  assertEquals(incident.authenticatedUserId, "auth-user-123");
  assertEquals(JSON.stringify(incident).includes("MUST_NOT_BE_RECORDED"), false);
  assertEquals(JSON.stringify(incident).includes("198.51.100.88"), false);
});

Deno.test("does not use spoofable forwarded-for when edge IP is unavailable", () => {
  const req = new Request("https://example.test", {
    headers: { "x-forwarded-for": "198.51.100.88" },
  });
  const incident = buildSecurityIncident(req, {
    incidentType: "AUTH_MISSING",
    severity: "WARNING",
    attemptedAction: "Call protected gateway without authentication",
    responseStatus: 401,
  });

  assertEquals(incident.networkEvidence.apparentClientIp, null);
  assertEquals(incident.networkEvidence.ipSource, "unavailable");
});

Deno.test("bounds untrusted metadata and marks device identity unverified", () => {
  const req = new Request("https://example.test", {
    headers: {
      "cf-connecting-ip": "bad,spoofed",
      "user-agent": "x".repeat(400),
    },
  });
  const incident = buildSecurityIncident(req, {
    incidentType: "ORIGIN_REJECTED",
    severity: "HIGH",
    attemptedAction: "Attempt browser-origin bypass",
    responseStatus: 403,
  });

  assertEquals(incident.networkEvidence.apparentClientIp, null);
  assert(incident.networkEvidence.userAgent?.length === 256);
  assertEquals(incident.deviceEvidence.deviceIdentity, "not-verified");
});
