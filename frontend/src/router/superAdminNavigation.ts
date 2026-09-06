import type { WorkspaceNavigationItem } from '../types/workspace';

export const superAdminNavigation: WorkspaceNavigationItem[] = [
  { label: 'Workspace', to: '/super-admin' },
  { label: 'School Registration', to: '/super-admin/school-registrations' },
  { label: 'Schools', to: '/super-admin/schools' },
  { label: 'Challenge Projects', to: '/super-admin/projects' },
  { label: 'L3 Authorization Review', to: '/super-admin/l3-authorizations' },
  { label: 'Platform Ops', disabled: true }
];
