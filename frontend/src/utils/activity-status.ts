const EXECUTION_LABELS: Record<string, string> = {
  DRAFT: '草稿', PUBLISHED: '已发布', IN_PROGRESS: '进行中', ENDED: '已结束', CANCELLED: '已取消',
};
const PUBLIC_LABELS: Record<string, string> = {
  NOT_SUBMITTED: '未提交', PENDING_PLATFORM_REVIEW: '审核中', PLATFORM_APPROVED: '平台批准',
  PLATFORM_REJECTED: '平台驳回', PUBLIC: '已公开', SCHOOL_WITHDRAWN: '学校撤回', PLATFORM_TAKEDOWN: '平台下架',
};

export function executionLabel(s: string): string { return EXECUTION_LABELS[s] || s; }
export function publicLabel(s: string): string { return PUBLIC_LABELS[s] || s; }
export function execTagType(s: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const m: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = { DRAFT: 'info', PUBLISHED: '', IN_PROGRESS: 'success', ENDED: 'warning', CANCELLED: 'danger' };
  return m[s] || 'info';
}
export function publicTagType(s: string): '' | 'success' | 'warning' | 'info' | 'danger' {
  const m: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = { PUBLIC: 'success', PLATFORM_APPROVED: '', PLATFORM_REJECTED: 'danger', PENDING_PLATFORM_REVIEW: 'warning' };
  return m[s] || 'info';
}
