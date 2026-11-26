import { assertEquals, assert } from "jsr:@std/assert@1";
import { buildSecurityIncident } from "./security_telemetry.ts";
import { toSecurityIncidentRow } from "./incident_archive.ts";

Deno.test("archive row maps only allowlisted incident evidence", () => {
  const req = new Request("https://gateway.example.test", {
    headers: {
      "cf-connecting-ip": "203.0.113.10",
      "user-agent": "JAVAS-test/1",
      "cf-ray": "trace-123",
      "x-forwarded-for": "198.51.100.9",
      "authorization": "Bearer MUST_NOT_BE_STORED",
    },
  });
  const record = buildSecurityIncident(req, {
    incidentType: "AUTH_INVALID",
    severity: "HIGH",
    attemptedAction: "Invalid bearer token",
    responseStatus: 401,
    authenticatedUserId: null,
    now: new Date("2026-11-26T14:00:00.000Z"),
    eventId: "11111111-1111-4111-8111-111111111111",
  });
  const row = toSecurityIncidentRow(record);

  assertEquals(row.event_id, "11111111-1111-4111-8111-111111111111");
  assertEquals(row.incident_type, "AUTH_INVALID");
  assertEquals(row.apparent_client_ip, "203.0.113.10");
  assertEquals(row.user_agent, "JAVAS-test/1");
  assertEquals(row.authenticated_user_id, null);
  assertEquals(row.device_identity, "not-verified");
  assert(!JSON.stringify(row).includes("MUST_NOT_BE_STORED"));
  assert(!("authorization" in row));
  assert(!("request_body" in row));
});

Deno.test("archive row preserves unverified network confidence labels", () => {
  const record = buildSecurityIncident(new Request("https://gateway.example.test"), {
    incidentType: "AUTH_MISSING",
    severity: "WARNING",
    attemptedAction: "Missing bearer token",
    responseStatus: 401,
    now: new Date("2026-11-26T14:00:00.000Z"),
    eventId: "22222222-2222-4222-8222-222222222222",
  });
  const row = toSecurityIncidentRow(record);

  assertEquals(row.ip_source, "unavailable");
  assertEquals(row.ip_confidence, "unavailable");
  assertEquals(row.user_agent_confidence, "unavailable");
  assertEquals(row.device_identity, "not-verified");
});
