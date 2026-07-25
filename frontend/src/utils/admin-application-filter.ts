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
  if (typeof v === 'string' && /^\d+$/.test(v)) {
    const n = parseInt(v, 10);
    if (Number.isInteger(n) && n >= 1) return n;
  }
  return 1;
}

export function validateDate(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const s = v.trim();
  if (!DATE_RE.test(s)) return null;
  // Validate real calendar date
  const parts = s.split('-');
  const y = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10);
  const d = parseInt(parts[2], 10);
  const dt = new Date(y, m - 1, d);
  return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d ? s : null;
}

export function validateSchoolId(v: unknown, validIds: Set<string>): string | null {
  const s = typeof v === 'string' ? v : '';
  return validIds.has(s) ? s : null;
}
