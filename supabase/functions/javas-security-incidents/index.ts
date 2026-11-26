import { createClient } from "jsr:@supabase/supabase-js@2";
import { isCreatorAllowed, isOriginAllowed } from "../javas-gateway/validation.ts";

const INCIDENT_COLUMNS = [
  "id",
  "event_id",
  "schema_version",
  "occurred_at",
  "service",
  "incident_type",
  "severity",
  "attempted_action",
  "outcome",
  "response_status",
  "authenticated_user_id",
  "apparent_client_ip",
  "ip_source",
  "ip_confidence",
  "user_agent",
  "user_agent_confidence",
  "edge_trace_id",
  "device_identity",
  "device_note",
  "recorded_at",
].join(",");

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const publishableKey =
  Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ??
  Deno.env.get("SUPABASE_ANON_KEY");
const serverKey =
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
  Deno.env.get("SUPABASE_SECRET_KEY");

const authClient = supabaseUrl && publishableKey
  ? createClient(supabaseUrl, publishableKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
  : null;

const archiveClient = supabaseUrl && serverKey
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

function response(body: unknown, status: number, origin: string | null): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...(origin ? { "Access-Control-Allow-Origin": origin } : {}),
      "Access-Control-Allow-Headers": "authorization, apikey, content-type",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Max-Age": "86400",
      "Vary": "Origin",
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function parseLimit(raw: string | null): number | null {
  if (raw === null || raw === "") return 25;
  if (!/^\d{1,3}$/.test(raw)) return null;
  const value = Number(raw);
  return value >= 1 && value <= 50 ? value : null;
}

function validCursor(recordedAt: string | null, id: string | null): boolean {
  if (recordedAt === null && id === null) return true;
  if (!recordedAt || !id) return false;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(recordedAt)) return false;
  if (Number.isNaN(Date.parse(recordedAt))) return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id);
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("Origin");
  if (!isOriginAllowed(origin, Deno.env.get("JAVAS_ALLOWED_ORIGINS"))) {
    return response({ error: "Origin not allowed" }, 403, null);
  }
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        ...(origin ? { "Access-Control-Allow-Origin": origin } : {}),
        "Access-Control-Allow-Headers": "authorization, apikey, content-type",
        "Access-Control-Allow-Methods": "GET, OPTIONS",
        "Access-Control-Max-Age": "86400",
        "Vary": "Origin",
      },
    });
  }
  if (req.method !== "GET") return response({ error: "Method not allowed" }, 405, origin);

  const match = req.headers.get("Authorization")?.match(/^Bearer\s+(.+)$/i);
  if (!match?.[1] || !authClient) return response({ error: "Unauthorized" }, 401, origin);

  const { data: { user }, error: authError } = await authClient.auth.getUser(match[1]);
  if (authError || !user) return response({ error: "Unauthorized" }, 401, origin);

  const creatorId = Deno.env.get("JAVAS_CREATOR_USER_ID");
  if (!isCreatorAllowed(user.id, creatorId)) {
    return response({ error: "Forbidden" }, 403, origin);
  }
  if (!archiveClient) return response({ error: "Incident archive is not configured" }, 503, origin);

  const url = new URL(req.url);
  const limit = parseLimit(url.searchParams.get("limit"));
  const beforeAt = url.searchParams.get("before_recorded_at");
  const beforeId = url.searchParams.get("before_id");
  if (limit === null || !validCursor(beforeAt, beforeId)) {
    return response({ error: "Invalid pagination parameters" }, 400, origin);
  }

  let query = archiveClient
    .from("javas_security_incidents")
    .select(INCIDENT_COLUMNS)
    .order("recorded_at", { ascending: false })
    .order("id", { ascending: false })
    .limit(limit + 1);

  if (beforeAt && beforeId) {
    query = query.or(
      `recorded_at.lt.${beforeAt},and(recorded_at.eq.${beforeAt},id.lt.${beforeId})`,
    );
  }

  const { data, error } = await query;
  if (error || !data) return response({ error: "Unable to retrieve incident history" }, 503, origin);

  const hasMore = data.length > limit;
  const items = hasMore ? data.slice(0, limit) : data;
  const last = items.at(-1) as { recorded_at?: string; id?: string } | undefined;
  const nextCursor = hasMore && last?.recorded_at && last.id
    ? { before_recorded_at: last.recorded_at, before_id: last.id }
    : null;

  return response({ items, nextCursor }, 200, origin);
});
