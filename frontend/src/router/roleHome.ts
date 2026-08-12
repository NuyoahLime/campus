import type { RouteLocationRaw } from 'vue-router';
import type { CurrentUser } from '../types/auth';

export const ROLE_WORKSPACES = {
  ROLE_SUPER_ADMIN: {
    routeName: 'super-admin-workspace',
    path: '/super-admin'
  },
  ROLE_SCHOOL_ADMIN: {
    routeName: 'school-admin-workspace',
    path: '/school-admin'
  },
  ROLE_STUDENT: {
    routeName: 'student-workspace',
    path: '/student'
  }
} as const;

export type FormalAuthority = keyof typeof ROLE_WORKSPACES;

export type RoleHomeResolution =
  | {
      status: 'resolved';
      authority: FormalAuthority;
      routeName: (typeof ROLE_WORKSPACES)[FormalAuthority]['routeName'];
      path: (typeof ROLE_WORKSPACES)[FormalAuthority]['path'];
    }
  | {
      status: 'unavailable';
      authority: null;
      routeName: 'role-unavailable';
      path: '/role-unavailable';
    };

function isFormalAuthority(authority: string): authority is FormalAuthority {
  return Object.prototype.hasOwnProperty.call(ROLE_WORKSPACES, authority);
}

export function resolveRoleHome(
  currentUser: Pick<CurrentUser, 'authorities'> | null | undefined
): RoleHomeResolution {
  const authorities = currentUser?.authorities ?? [];

  if (authorities.length !== 1 || !isFormalAuthority(authorities[0])) {
    return {
      status: 'unavailable',
      authority: null,
      routeName: 'role-unavailable',
      path: '/role-unavailable'
    };
  }

  const authority = authorities[0];
  const workspace = ROLE_WORKSPACES[authority];
  return {
    status: 'resolved',
    authority,
    routeName: workspace.routeName,
    path: workspace.path
  };
}

export function roleHomeLocation(
  currentUser: Pick<CurrentUser, 'authorities'> | null | undefined
): RouteLocationRaw {
  return { name: resolveRoleHome(currentUser).routeName };
}
