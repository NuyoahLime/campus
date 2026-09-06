import type { WorkspaceNavigationItem } from '../types/workspace';

export const schoolAdminNavigation: WorkspaceNavigationItem[] = [
  { label: 'Workspace', to: '/school-admin' },
  { label: 'Student Review', to: '/school-admin/student-applications' },
  { label: 'Activities', to: '/school-admin/activities' },
  { label: 'Appeals', to: '/school-admin/appeals' },
  { label: 'Feedback', to: '/school-admin/feedback' },
  { label: 'Students', disabled: true },
  { label: 'L3 Data Authorization', to: '/school-admin/l3-authorizations' },
  { label: 'Ranking Management', to: '/school-admin/ranking-management' },
  { label: 'Rankings', to: '/school-admin/rankings' }
];
