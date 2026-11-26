import { createClient } from "jsr:@supabase/supabase-js@2";
import { isOriginAllowed } from "../javas-gateway/validation.ts";
import {
  issueEnrollmentChallenge,
  sha256Base64,
  verifyDeviceProof,
  type DeviceProof,
} from "./crypto.ts";

const MAX_BODY_BYTES = 8 * 1024;
const STEP_UP_MAX_AGE_SECONDS = 5 * 60;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const publishableKey =
  Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ?? Deno.env.get("SUPABASE_ANON_KEY");
const serverKey =
  Deno.env.get("SUPABASE_SECRET_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

const authClient = supabaseUrl && publishableKey
  ? createClient(supabaseUrl, publishableKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
  : null;

const adminClient = supabaseUrl && serverKey
  ? createClient(supabaseUrl, serverKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: {
      fetch: (input, init) => fetch(input, {
        ...init,
        signal: AbortSignal.timeout(5000),
      }),
    },
  })
  : null;

function json(body: unknown, status: number, origin: string | null): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...(origin ? { "Access-Control-Allow-Origin": origin } : {}),
      "Access-Control-Allow-Headers": "authorization, apikey, content-type",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Max-Age": "86400",
      "Vary": "Origin",
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Pragma": "no-cache",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function decodeBase64(value: string): Uint8Array {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) {
    throw new Error("Invalid base64");
  }
  return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
}

function readJwtPayloadAfterAuthVerification(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64 + "=".repeat((4 - base64.length % 4) % 4);
    const payload = JSON.parse(atob(padded));
    return payload && typeof payload === "object" ? payload : null;
  } catch {
    return null;
  }
}

function hasRecentStepUp(claims: Record<string, unknown>): boolean {
  const now = Math.floor(Date.now() / 1000);
  const authTime = claims.auth_time;
  if (
    claims.aal !== "aal2" ||
    !Number.isInteger(authTime) ||
    (authTime as number) > now + 30 ||
    now - (authTime as number) > STEP_UP_MAX_AGE_SECONDS
  ) return false;

  const amr = claims.amr;
  if (!Array.isArray(amr)) return false;
  return amr.some((entry) => {
    if (!entry || typeof entry !== "object") return false;
    const method = (entry as Record<string, unknown>).method;
    const timestamp = (entry as Record<string, unknown>).timestamp;
    return (method === "totp" || method === "otp") &&
      Number.isInteger(timestamp) &&
      (timestamp as number) <= now + 30 &&
      now - (timestamp as number) <= STEP_UP_MAX_AGE_SECONDS;
  });
}

async function authenticateCreator(req: Request): Promise<
  { userId: string } | { error: string; status: number }
> {
  const tokenMatch = req.headers.get("Authorization")?.match(/^Bearer\s+(.+)$/i);
  if (!tokenMatch?.[1] || !authClient) return { error: "Unauthorized", status: 401 };

  // getUser verifies the exact access token with Supabase Auth. Only after it
  // succeeds do we read signed claims from that same token for AAL2 freshness.
  const { data, error } = await authClient.auth.getUser(tokenMatch[1]);
  if (error || !data.user) return { error: "Unauthorized", status: 401 };

  const creatorId = Deno.env.get("JAVAS_CREATOR_USER_ID")?.trim();
  if (!creatorId) return { error: "Creator access is not configured", status: 503 };
  if (data.user.id !== creatorId) return { error: "Forbidden", status: 403 };

  const claims = readJwtPayloadAfterAuthVerification(tokenMatch[1]);
  if (!claims || claims.sub !== data.user.id || !hasRecentStepUp(claims)) {
    return { error: "Recent multi-factor authentication is required", status: 403 };
  }
  return { userId: data.user.id };
}

async function readBody(req: Request): Promise<Record<string, unknown> | null> {
  const declaredLength = Number(req.headers.get("Content-Length") ?? "0");
  if (declaredLength > MAX_BODY_BYTES) return null;
  const raw = await req.text();
  if (new TextEncoder().encode(raw).byteLength > MAX_BODY_BYTES) return null;
  const parsed = JSON.parse(raw);
  return parsed && typeof parsed === "object" && !Array.isArray(parsed)
    ? parsed as Record<string, unknown>
    : null;
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("Origin");
  if (!isOriginAllowed(origin, Deno.env.get("JAVAS_ALLOWED_ORIGINS"))) {
    return json({ error: "Origin not allowed" }, 403, null);
  }
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        ...(origin ? { "Access-Control-Allow-Origin": origin } : {}),
        "Access-Control-Allow-Headers": "authorization, apikey, content-type",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Max-Age": "86400",
        "Vary": "Origin",
      },
    });
  }
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405, origin);
  if (!adminClient) return json({ error: "Enrollment service is not configured" }, 503, origin);

  const identity = await authenticateCreator(req);
  if ("error" in identity) return json({ error: identity.error }, identity.status, origin);

  let body: Record<string, unknown> | null;
  try {
    body = await readBody(req);
  } catch {
    return json({ error: "Invalid request body" }, 400, origin);
  }
  if (!body) return json({ error: "Request body is too large or invalid" }, 400, origin);

  if (body.action === "challenge") {
    const deviceId = body.deviceId;
    if (typeof deviceId !== "string" || !UUID_PATTERN.test(deviceId)) {
      return json({ error: "A valid installation device ID is required" }, 400, origin);
    }

    try {
      const issued = await issueEnrollmentChallenge({
        creatorId: identity.userId,
        deviceId,
      });
      const { error } = await adminClient.rpc("issue_javas_device_enrollment_challenge", {
        p_challenge_id: issued.challenge.challengeId,
        p_creator_user_id: identity.userId,
        p_device_id: deviceId,
        p_nonce_sha256_base64: issued.nonceDigestBase64,
        p_expires_at: issued.challenge.expiresAt,
      });
      if (error) {
        const limited = error.code === "P0001";
        return json(
          { error: limited ? "Enrollment challenge rate limit reached" : "Unable to issue challenge" },
          limited ? 429 : 503,
          origin,
        );
      }
      return json({ challenge: issued.challenge }, 200, origin);
    } catch {
      return json({ error: "Unable to issue challenge" }, 400, origin);
    }
  }

  if (body.action === "prove") {
    const proof = body.proof as DeviceProof | undefined;
    if (
      !proof || typeof proof !== "object" ||
      !proof.challenge || typeof proof.challenge !== "object" ||
      typeof proof.publicKeySpkiBase64 !== "string" ||
      proof.publicKeySpkiBase64.length > 1024 ||
      typeof proof.signatureDerBase64 !== "string" ||
      proof.signatureDerBase64.length > 200
    ) {
      return json({ error: "Invalid device proof" }, 400, origin);
    }

    const challenge = proof.challenge;
    if (
      typeof challenge.deviceId !== "string" || !UUID_PATTERN.test(challenge.deviceId) ||
      challenge.creatorId !== identity.userId ||
      typeof challenge.challengeId !== "string" || !UUID_PATTERN.test(challenge.challengeId)
    ) {
      return json({ error: "Invalid device proof" }, 400, origin);
    }

    const valid = await verifyDeviceProof(proof, {
      creatorId: identity.userId,
      deviceId: challenge.deviceId,
    });
    if (!valid) return json({ error: "Device proof was not valid" }, 403, origin);

    try {
      const nonce = decodeBase64(challenge.nonceBase64);
      const nonceDigestBase64 = await sha256Base64(nonce);
      const publicKey = decodeBase64(proof.publicKeySpkiBase64);
      const publicKeySha256Base64 = await sha256Base64(publicKey);
      const { data, error } = await adminClient.rpc(
        "consume_javas_device_enrollment_challenge",
        {
          p_challenge_id: challenge.challengeId,
          p_creator_user_id: identity.userId,
          p_device_id: challenge.deviceId,
          p_nonce_sha256_base64: nonceDigestBase64,
          p_public_key_spki_base64: proof.publicKeySpkiBase64,
          p_public_key_sha256_base64: publicKeySha256Base64,
        },
      );
      if (error) return json({ error: "Unable to complete device proof" }, 503, origin);
      if (!Array.isArray(data) || data.length !== 1) {
        return json({ error: "Challenge expired, already used or not recognised" }, 409, origin);
      }
      return json({ deviceId: challenge.deviceId, state: "PENDING_APPROVAL" }, 201, origin);
    } catch {
      return json({ error: "Unable to complete device proof" }, 400, origin);
    }
  }

  return json({ error: "Unknown enrollment action" }, 400, origin);
});
