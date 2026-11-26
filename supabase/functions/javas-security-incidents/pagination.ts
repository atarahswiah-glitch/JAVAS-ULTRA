export function parseIncidentLimit(raw: string | null): number | null {
  if (raw === null || raw === "") return 25;
  if (!/^\d{1,3}$/.test(raw)) return null;
  const value = Number(raw);
  return value >= 1 && value <= 50 ? value : null;
}

export function isValidIncidentCursor(recordedAt: string | null, id: string | null): boolean {
  if (recordedAt === null && id === null) return true;
  if (!recordedAt || !id) return false;
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$/.test(recordedAt)) {
    return false;
  }
  if (Number.isNaN(Date.parse(recordedAt))) return false;
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id);
}
