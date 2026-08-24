import type { WorkspaceNavigationItem } from '../types/workspace';

export const studentNavigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/student' },
  { label: '我的活动', to: '/student/activities' },
  { label: '我的成绩', to: '/student/scores' },
  { label: '排行榜', to: '/student/rankings' },
  { label: '申诉', to: '/student/appeals' },
  { label: '意见反馈', to: '/student/feedback' },
  { label: '个人信息', disabled: true }
];
