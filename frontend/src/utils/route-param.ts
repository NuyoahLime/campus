const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function resolveUuidParam(value: string | string[] | undefined): string | null {
  if (value === undefined) return null;
  if (Array.isArray(value)) return null;
  const trimmed = value.trim();
  if (trimmed.length === 0) return null;
  if (!UUID_REGEX.test(trimmed)) return null;
  return trimmed;
}
