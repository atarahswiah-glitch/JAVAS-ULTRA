import { assertEquals } from "jsr:@std/assert@1";
import { isCreatorAllowed, isOriginAllowed } from "../javas-gateway/validation.ts";
import { isValidIncidentCursor, parseIncidentLimit } from "./pagination.ts";


Deno.test("Creator authorization fails closed", () => {
  assertEquals(isCreatorAllowed("creator-1", "creator-1"), true);
  assertEquals(isCreatorAllowed("other", "creator-1"), false);
  assertEquals(isCreatorAllowed("creator-1", undefined), false);
  assertEquals(isCreatorAllowed("creator-1", " "), false);
});

Deno.test("native origin can omit Origin but browser origin must be allowlisted", () => {
  assertEquals(isOriginAllowed(null, undefined), true);
  assertEquals(isOriginAllowed("https://trusted.example", "https://trusted.example"), true);
  assertEquals(isOriginAllowed("https://evil.example", "https://trusted.example"), false);
});

Deno.test("page size is bounded to 1..50", () => {
  assertEquals(parseIncidentLimit(null), 25);
  assertEquals(parseIncidentLimit("50"), 50);
  assertEquals(parseIncidentLimit("51"), null);
  assertEquals(parseIncidentLimit("-1"), null);
  assertEquals(parseIncidentLimit("1.5"), null);
});

Deno.test("cursor requires a valid timestamp and UUID pair", () => {
  assertEquals(isValidIncidentCursor(null, null), true);
  assertEquals(isValidIncidentCursor("2026-11-26T14:00:00.000Z", "11111111-1111-4111-8111-111111111111"), true);
  assertEquals(isValidIncidentCursor("2026-11-26T14:00:00+00:00", "11111111-1111-4111-8111-111111111111"), true);
  assertEquals(isValidIncidentCursor("2026-11-26T14:00:00.000Z", null), false);
  assertEquals(isValidIncidentCursor("not-a-date", "11111111-1111-4111-8111-111111111111"), false);
  assertEquals(isValidIncidentCursor("2026-11-26T14:00:00.000Z", "not-a-uuid"), false);
});
