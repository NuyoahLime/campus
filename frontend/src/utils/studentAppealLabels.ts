const APPEAL_STATUS_LABELS: Record<string, string> = {
  SUBMITTED: '已提交',
  PROCESSING: '处理中',
  REJECTED: '已驳回',
  ACCEPTED_PENDING_CORRECTION: '待更正',
  SCORE_CORRECTING: '成绩更正中',
  RANK_CHECKING: '排名复核中',
  RANK_FIXING: '排名修正中',
  ESCALATED: '已升级',
  PLATFORM_PROCESSING: '平台处理中',
  RETURNED_TO_SCHOOL: '退回学校',
  PLATFORM_DECIDED: '平台已裁决',
  RESOLVED: '已解决',
  WITHDRAWN: '已撤回'
};

const APPEAL_TYPE_LABELS: Record<string, string> = {
  SCORE: '成绩申诉',
  RANKING: '排名申诉'
};

export function labelForAppealStatus(value: string | null | undefined): string {
  return APPEAL_STATUS_LABELS[value ?? ''] ?? '申诉状态';
}

export function labelForAppealType(value: string | null | undefined): string {
  return APPEAL_TYPE_LABELS[value ?? ''] ?? '申诉类型';
}
