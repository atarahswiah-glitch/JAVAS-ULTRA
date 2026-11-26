export const STEP_UP_MAX_AGE_SECONDS = 5 * 60;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * Decode claims only AFTER Supabase Auth has verified this exact token with getUser().
 * This helper does not verify JWT signatures and must never be used as an auth check alone.
 */
export function readVerifiedJwtClaims(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - base64.length % 4) % 4);
    const payload: unknown = JSON.parse(atob(padded));
    return payload && typeof payload === "object" && !Array.isArray(payload)
      ? payload as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

export function hasRecentStepUp(
  claims: Record<string, unknown>,
  nowSeconds = Math.floor(Date.now() / 1000),
): boolean {
  const authTime = claims.auth_time;
  if (
    claims.aal !== "aal2" ||
    !Number.isInteger(authTime) ||
    (authTime as number) > nowSeconds + 30 ||
    nowSeconds - (authTime as number) > STEP_UP_MAX_AGE_SECONDS
  ) return false;

  const amr = claims.amr;
  if (!Array.isArray(amr)) return false;
  return amr.some((entry) => {
    if (!entry || typeof entry !== "object") return false;
    const method = (entry as Record<string, unknown>).method;
    const timestamp = (entry as Record<string, unknown>).timestamp;
    return (method === "totp" || method === "otp") &&
      Number.isInteger(timestamp) &&
      (timestamp as number) <= nowSeconds + 30 &&
      nowSeconds - (timestamp as number) <= STEP_UP_MAX_AGE_SECONDS;
  });
}

export function isUuid(value: unknown): value is string {
  return typeof value === "string" && UUID_PATTERN.test(value);
}
