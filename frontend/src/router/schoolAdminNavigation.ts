import type { WorkspaceNavigationItem } from '../types/workspace';

export const schoolAdminNavigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/school-admin' },
  { label: '学生身份审核', to: '/school-admin/student-applications' },
  { label: '活动管理', to: '/school-admin/activities' },
  { label: '成绩申诉', to: '/school-admin/appeals' },
  { label: '意见反馈', to: '/school-admin/feedback' },
  { label: '学生管理', disabled: true },
  { label: '排行榜管理', to: '/school-admin/ranking-management' },
  { label: '排行榜与成果', to: '/school-admin/rankings' }
];
