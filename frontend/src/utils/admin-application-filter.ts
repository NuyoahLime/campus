const VALID_STATUSES = new Set(['DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN']);
const VALID_SORTS = new Set(['updated_desc','updated_asc','created_desc','created_asc']);
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

export function validateStatus(v: unknown): string | null {
  const s = typeof v === 'string' ? v : '';
  return VALID_STATUSES.has(s) ? s : null;
}

export function validateSort(v: unknown): string | null {
  const s = typeof v === 'string' ? v : '';
  return VALID_SORTS.has(s) ? s : null;
}

export function validatePage(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) && n >= 1 ? n : 1;
}

export function validateDate(v: unknown): string | null {
  const s = typeof v === 'string' ? v : '';
  return DATE_RE.test(s) ? s : null;
}

export function validateSchoolId(v: unknown, validIds: Set<string>): string | null {
  const s = typeof v === 'string' ? v : '';
  return validIds.has(s) ? s : null;
}
