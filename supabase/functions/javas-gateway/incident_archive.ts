import type { SecurityIncidentRecord } from "./security_telemetry.ts";

/** Map the deliberately allowlisted evidence record to the database row shape. */
export function toSecurityIncidentRow(record: SecurityIncidentRecord): Record<string, unknown> {
  return {
    event_id: record.eventId,
    schema_version: record.schemaVersion,
    occurred_at: record.occurredAt,
    service: record.service,
    incident_type: record.incidentType,
    severity: record.severity,
    attempted_action: record.attemptedAction,
    outcome: record.outcome,
    response_status: record.responseStatus,
    authenticated_user_id: record.authenticatedUserId,
    apparent_client_ip: record.networkEvidence.apparentClientIp,
    ip_source: record.networkEvidence.ipSource,
    ip_confidence: record.networkEvidence.ipConfidence,
    user_agent: record.networkEvidence.userAgent,
    user_agent_confidence: record.networkEvidence.userAgentConfidence,
    edge_trace_id: record.networkEvidence.edgeTraceId,
    device_identity: record.deviceEvidence.deviceIdentity,
    device_note: record.deviceEvidence.note,
  };
}
