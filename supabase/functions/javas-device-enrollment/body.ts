export async function readBoundedJsonObject(
  req: Request,
  maxBytes: number,
): Promise<Record<string, unknown> | null> {
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0 || !req.body) return null;

  const declaredLength = req.headers.get("Content-Length");
  if (declaredLength !== null) {
    if (!/^\\d+$/.test(declaredLength) || Number(declaredLength) > maxBytes) {
      try { await req.body.cancel(); } catch { /* best-effort cancellation */ }
      return null;
    }
  }

  const reader = req.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      totalBytes += value.byteLength;
      if (totalBytes > maxBytes) {
        try { await reader.cancel("Request body exceeds configured limit"); } catch { /* best effort */ }
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }

  try {
    const parsed: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}
