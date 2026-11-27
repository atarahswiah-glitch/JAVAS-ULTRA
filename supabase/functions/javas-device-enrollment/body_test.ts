import { readBoundedJsonObject } from "./body.ts";

Deno.test("reads a valid bounded JSON object", async () => {
  const request = new Request("https://example.invalid", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "challenge" }),
  });
  const value = await readBoundedJsonObject(request, 1024);
  if (value?.action !== "challenge") throw new Error("Valid JSON object was not read");
});

Deno.test("rejects oversized streamed bodies even without Content-Length", async () => {
  const payload = new TextEncoder().encode(JSON.stringify({ data: "x".repeat(256) }));
  const request = new Request("https://example.invalid", {
    method: "POST",
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(payload);
        controller.close();
      },
    }),
  });
  if (await readBoundedJsonObject(request, 64) !== null) {
    throw new Error("Oversized streamed body was accepted");
  }
});

Deno.test("rejects invalid JSON, arrays and primitive values", async () => {
  for (const body of ["{", "[]", "null", "42", '"text"']) {
    const request = new Request("https://example.invalid", { method: "POST", body });
    if (await readBoundedJsonObject(request, 1024) !== null) {
      throw new Error(`Invalid JSON object accepted: ${body}`);
    }
  }
});

Deno.test("rejects a declared oversized or malformed Content-Length", async () => {
  for (const contentLength of ["999", "unknown", "-1"]) {
    const request = new Request("https://example.invalid", {
      method: "POST",
      headers: { "Content-Length": contentLength },
      body: "{}",
    });
    if (await readBoundedJsonObject(request, 32) !== null) {
      throw new Error(`Invalid Content-Length accepted: ${contentLength}`);
    }
  }
});
