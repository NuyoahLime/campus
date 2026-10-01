import { describe, expect, it } from 'vitest';
import { activityResultActions, labelForActivityResultAction, labelForActivityResultStatus, resultMutationErrorMessage } from './activityResultLabels';

describe('activity result labels', () => {
  it('maps lifecycle and review statuses to user-facing labels', () => {
    expect(labelForActivityResultStatus('INTERNAL_PUBLISHED')).toBe('已内部发布');
    expect(labelForActivityResultStatus('PENDING_PUBLIC_REVIEW')).toBe('平台审核中');
    expect(labelForActivityResultAction('REJECTED')).toBe('审核驳回');
  });

  it('preserves unknown backend statuses for diagnosability', () => {
    expect(labelForActivityResultStatus('FUTURE_STATUS')).toBe('FUTURE_STATUS');
    expect(labelForActivityResultStatus(null)).toBe('未设置');
  });

  it('derives only the actions allowed by the authoritative lifecycle state', () => {
    expect(activityResultActions('DRAFT', 'NOT_SUBMITTED', true)).toMatchObject({ edit: true, publish: true, submit: false });
    expect(activityResultActions('INTERNAL_PUBLISHED', 'PENDING_PUBLIC_REVIEW', true).edit).toBe(false);
    expect(activityResultActions('INTERNAL_PUBLISHED', 'PUBLIC', true).withdraw).toBe(true);
  });

  it('gives a conflict-specific mutation message for stale state', () => {
    expect(resultMutationErrorMessage(409)).toContain('状态已变化');
    expect(resultMutationErrorMessage(500)).toContain('操作失败');
  });
});
