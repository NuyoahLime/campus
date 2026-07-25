export const APPLICATION_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
};

export const APPLICATION_STATUS_TAG_TYPES: Record<string, 'info' | 'warning' | 'success' | 'danger' | ''> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: '',
};

export function appStatusLabel(status: string): string {
  return APPLICATION_STATUS_LABELS[status] || status;
}

export function appStatusTagType(status: string) {
  return APPLICATION_STATUS_TAG_TYPES[status] || 'info';
}
