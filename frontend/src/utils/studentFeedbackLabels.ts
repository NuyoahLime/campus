const FEEDBACK_STATUS_LABELS: Record<string, string> = {
  SUBMITTED: '已提交',
  PROCESSING: '处理中',
  RESOLVED: '已回复',
  ESCALATED: '已升级',
  CLOSED: '已关闭'
};

const FEEDBACK_TYPE_LABELS: Record<string, string> = {
  GENERAL: '一般反馈',
  SCORE_PROBLEM: '成绩问题',
  RANKING_PROBLEM: '排名问题'
};

export function labelForFeedbackStatus(value: string | null | undefined): string {
  return FEEDBACK_STATUS_LABELS[value ?? ''] ?? '反馈状态';
}

export function labelForFeedbackType(value: string | null | undefined): string {
  return FEEDBACK_TYPE_LABELS[value ?? ''] ?? '反馈类型';
}
