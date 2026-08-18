import type { WorkspaceNavigationItem } from '../types/workspace';

export const studentNavigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/student' },
  { label: '活动', disabled: true },
  { label: '我的成绩', to: '/student/scores' },
  { label: '排行榜', disabled: true },
  { label: '申诉', disabled: true },
  { label: '个人信息', disabled: true }
];
