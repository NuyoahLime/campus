import type { ActivityExecutionStatus } from '../types/activity';

const executionLabels: Record<ActivityExecutionStatus, string> = {
  PUBLISHED: '已发布',
  IN_PROGRESS: '进行中',
  ENDED: '已结束'
};

export function labelForActivityStatus(status: ActivityExecutionStatus): string {
  return executionLabels[status] ?? '公开活动';
}

export function formatActivityTime(value: string | null): string {
  if (!value) return '时间待定';
  return new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

export function activityTimeRange(start: string | null, end: string | null): string {
  if (!start && !end) return '时间待定';
  if (!end) return `${formatActivityTime(start)} 起`;
  if (!start) return `截至 ${formatActivityTime(end)}`;
  return `${formatActivityTime(start)} - ${formatActivityTime(end)}`;
}
