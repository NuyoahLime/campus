// Shared status label maps — single source of truth for all pages

export const ACTIVITY_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  IN_PROGRESS: '进行中',
  ENDED: '已结束',
  CANCELLED: '已取消',
};

export const SCORE_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING_REVIEW: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  INVALIDATED: '已失效',
};

export const SCORE_TYPE_LABELS: Record<string, string> = {
  INTEGER: '整数',
  DECIMAL: '小数',
  DURATION: '时长',
  GRADE: '等级',
};

export function activityStatusLabel(status: string): string {
  return ACTIVITY_STATUS_LABELS[status] || status;
}

export function scoreStatusLabel(status: string): string {
  return SCORE_STATUS_LABELS[status] || status;
}

export function scoreTypeLabel(type: string): string {
  return SCORE_TYPE_LABELS[type] || type;
}

export function scoreStatusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' | '' {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | ''> = {
    APPROVED: 'success',
    PENDING_REVIEW: 'warning',
    REJECTED: 'danger',
    INVALIDATED: 'info',
    DRAFT: '',
  };
  return map[status] || 'info';
}

export function activityStatusTagType(status: string): 'success' | 'warning' | 'info' | 'danger' | '' {
  const map: Record<string, 'success' | 'warning' | 'info' | 'danger' | ''> = {
    PUBLISHED: 'info',
    IN_PROGRESS: 'success',
    ENDED: '',
    CANCELLED: 'danger',
    DRAFT: 'warning',
  };
  return map[status] || 'info';
}
