export type SecurityIncidentType =
  | "ORIGIN_REJECTED"
  | "AUTH_MISSING"
  | "AUTH_INVALID"
  | "CREATOR_CONFIGURATION_MISSING"
  | "CREATOR_ACCESS_DENIED"
  | "REQUEST_TOO_LARGE"
  | "REQUEST_INVALID";

export type SecurityIncidentSeverity = "WARNING" | "HIGH" | "CRITICAL";

export interface SecurityIncidentRecord {
  schemaVersion: 1;
  eventId: string;
  occurredAt: string;
  service: "javas-gateway";
  incidentType: SecurityIncidentType;
  severity: SecurityIncidentSeverity;
  attemptedAction: string;
  outcome: "BLOCKED";
  responseStatus: number;
  authenticatedUserId: string | null;
  networkEvidence: {
    apparentClientIp: string | null;
    ipSource: "cf-connecting-ip" | "unavailable";
    ipConfidence: "edge-observed-unverified" | "unavailable";
    userAgent: string | null;
    userAgentConfidence: "client-supplied-unverified" | "unavailable";
    edgeTraceId: string | null;
  };
  deviceEvidence: {
    deviceIdentity: "not-verified";
    note: string;
  };
}

/**
 * Build a privacy-minimised incident record. The only IP field accepted is the
 * platform's CF-Connecting-IP header; do not trust the first X-Forwarded-For value.
 * This function never reads request bodies, Authorization, cookies or API keys.
 */
export function buildSecurityIncident(
  req: Request,
  input: {
    incidentType: SecurityIncidentType;
    severity: SecurityIncidentSeverity;
    attemptedAction: string;
    responseStatus: number;
    authenticatedUserId?: string | null;
    now?: Date;
    eventId?: string;
  },
): SecurityIncidentRecord {
  const rawIp = req.headers.get("cf-connecting-ip")?.trim() ?? "";
  const safeIp = rawIp.length > 0 &&
      rawIp.length <= 64 &&
      !/[\s,\u0000-\u001f\u007f]/.test(rawIp)
    ? rawIp
    : null;

  const rawAgent = req.headers.get("user-agent")?.trim() ?? "";
  const userAgent = rawAgent.length > 0
      ? rawAgent.slice(0, 256)
      : null;
  const trace = req.headers.get("cf-ray")?.trim() ?? "";

  return {
    schemaVersion: 1,
    eventId: input.eventId ?? crypto.randomUUID(),
    occurredAt: (input.now ?? new Date()).toISOString(),
    service: "javas-gateway",
    incidentType: input.incidentType,
    severity: input.severity,
    attemptedAction: input.attemptedAction.slice(0, 160),
    outcome: "BLOCKED",
    responseStatus: input.responseStatus,
    authenticatedUserId: input.authenticatedUserId ?? null,
    networkEvidence: {
      apparentClientIp: safeIp,
      ipSource: safeIp ? "cf-connecting-ip" : "unavailable",
      ipConfidence: safeIp ? "edge-observed-unverified" : "unavailable",
      userAgent,
      userAgentConfidence: userAgent ? "client-supplied-unverified" : "unavailable",
      edgeTraceId: trace ? trace.slice(0, 128) : null,
    },
    deviceEvidence: {
      deviceIdentity: "not-verified",
      note: "A user-agent and IP address are not proof of a unique device or human identity. A verified enrolled-device signature is required before naming a device.",
    },
  };
}

/** Emit only the allowlisted incident structure. Never log request headers wholesale. */
export function logSecurityIncident(
  req: Request,
  input: Parameters<typeof buildSecurityIncident>[1],
): void {
  console.warn(JSON.stringify({
    event: "JAVAS_SECURITY_INCIDENT",
    incident: buildSecurityIncident(req, input),
  }));
}
