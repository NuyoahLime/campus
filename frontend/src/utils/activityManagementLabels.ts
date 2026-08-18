const EXECUTION_LABELS: Record<string, string> = {
  DRAFT: '草稿', PUBLISHED: '已发布', IN_PROGRESS: '进行中', ENDED: '已结束', CANCELLED: '已取消'
};

const PUBLIC_LABELS: Record<string, string> = {
  NOT_SUBMITTED: '未提交公开审核', PENDING_PLATFORM_REVIEW: '平台审核中',
  PLATFORM_APPROVED: '平台已通过', PLATFORM_REJECTED: '平台未通过', PUBLIC: '已公开',
  SCHOOL_WITHDRAWN: '学校已撤回', PLATFORM_TAKEDOWN: '平台已下架'
};

export function labelForActivityExecution(value: string | null | undefined) {
  return value ? (EXECUTION_LABELS[value] ?? value) : '未填写';
}

export function labelForActivityPublic(value: string | null | undefined) {
  return value ? (PUBLIC_LABELS[value] ?? value) : '未填写';
}
