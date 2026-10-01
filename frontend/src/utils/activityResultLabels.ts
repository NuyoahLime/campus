const labels: Record<string, string> = {
  DRAFT: '草稿',
  INTERNAL_PUBLISHED: '已内部发布',
  INTERNAL_WITHDRAWN: '已内部撤回',
  NOT_SUBMITTED: '未提交审核',
  PENDING_PUBLIC_REVIEW: '平台审核中',
  PLATFORM_APPROVED: '审核通过，待公开',
  PLATFORM_REJECTED: '平台已驳回',
  PUBLIC: '已公开',
  PLATFORM_TAKEDOWN: '平台已下架',
  ANOMALY_PENDING: '异常处理中',
  SUBMITTED: '提交审核',
  APPROVED: '审核通过',
  REJECTED: '审核驳回',
  TAKEDOWN: '平台下架',
  RESET: '恢复治理'
};

export function labelForActivityResultStatus(status: string | null | undefined): string {
  return status ? labels[status] ?? status : '未设置';
}

export function labelForActivityResultAction(action: string): string {
  return labelForActivityResultStatus(action);
}

export interface ActivityResultActions {
  edit: boolean;
  publish: boolean;
  submit: boolean;
  makePublic: boolean;
  withdraw: boolean;
  returnToDraft: boolean;
}

export function activityResultActions(
  internalStatus: string,
  publicStatus: string,
  hasCandidate: boolean
): ActivityResultActions {
  return {
    edit: !['PENDING_PUBLIC_REVIEW', 'PLATFORM_APPROVED', 'PLATFORM_TAKEDOWN'].includes(publicStatus)
      && internalStatus !== 'INTERNAL_WITHDRAWN',
    publish: internalStatus === 'DRAFT' && hasCandidate,
    submit: internalStatus === 'INTERNAL_PUBLISHED' && publicStatus === 'NOT_SUBMITTED',
    makePublic: publicStatus === 'PLATFORM_APPROVED',
    withdraw: internalStatus === 'INTERNAL_PUBLISHED',
    returnToDraft: internalStatus === 'INTERNAL_WITHDRAWN'
  };
}

export function resultMutationErrorMessage(status: number | undefined): string {
  return status === 409
    ? '成果状态已变化，页面已保留服务端数据，请重试当前允许的操作。'
    : '成果操作失败，请稍后重试。';
}
