import type { WorkspaceNavigationItem } from '../types/workspace';

export const superAdminNavigation: WorkspaceNavigationItem[] = [
  { label: '工作台概览', to: '/super-admin' },
  { label: '入驻审核', to: '/super-admin/school-registrations' },
  { label: '学校管理', to: '/super-admin/schools' },
  { label: '挑战项目', to: '/super-admin/projects' },
  { label: '平台运营', disabled: true }
];
