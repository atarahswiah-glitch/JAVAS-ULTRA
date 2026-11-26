import { assertEquals } from "jsr:@std/assert@1";
import { isCreatorAllowed, isOriginAllowed } from "../javas-gateway/validation.ts";

function parseLimit(raw: string | null): number | null {
  if (raw === null || raw === "") return 25;
  if (!/^\d{1,3}$/.test(raw)) return null;
  const value = Number(raw);
  return value >= 1 && value <= 50 ? value : null;
}

function validCursor(recordedAt: string | null, id: string | null): boolean {
  if (recordedAt === null && id === null) return true;
  if (!recordedAt || !id) return false;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/.test(recordedAt)) return false;
  if (Number.isNaN(Date.parse(recordedAt))) return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id);
}

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
  assertEquals(parseLimit(null), 25);
  assertEquals(parseLimit("50"), 50);
  assertEquals(parseLimit("51"), null);
  assertEquals(parseLimit("-1"), null);
  assertEquals(parseLimit("1.5"), null);
});

Deno.test("cursor requires a valid timestamp and UUID pair", () => {
  assertEquals(validCursor(null, null), true);
  assertEquals(validCursor("2026-11-26T14:00:00.000Z", "11111111-1111-4111-8111-111111111111"), true);
  assertEquals(validCursor("2026-11-26T14:00:00+00:00", "11111111-1111-4111-8111-111111111111"), true);
  assertEquals(validCursor("2026-11-26T14:00:00.000Z", null), false);
  assertEquals(validCursor("not-a-date", "11111111-1111-4111-8111-111111111111"), false);
  assertEquals(validCursor("2026-11-26T14:00:00.000Z", "not-a-uuid"), false);
});
