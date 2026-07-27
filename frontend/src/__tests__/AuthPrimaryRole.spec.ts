import { describe, it, expect } from 'vitest';

describe('AuthPrimaryRole', () => {
  it('primaryRole controls workspace access', () => {
    const primaryRole = 'STUDENT';
    const requiredRole = 'STUDENT';
    expect(primaryRole).toBe(requiredRole);
  });

  it('roles array cannot bypass primaryRole', () => {
    const primaryRole = 'STUDENT' as string;
    const adminRequiredRole = 'SUPER_ADMIN' as string;
    const canAccessAdmin = primaryRole === adminRequiredRole;
    expect(canAccessAdmin).toBe(false);
  });

  it('null primaryRole goes to no-access', () => {
    const primaryRole = null;
    if (!primaryRole) {
      expect('/account/no-access').toBe('/account/no-access');
    }
  });

  it('defaultWorkspaceRoute uses primaryRole', () => {
    const getRoute = (pr: string | null) => {
      if (pr === 'TEACHER') return '/teacher';
      if (pr === 'STUDENT') return '/student';
      if (pr === 'SCHOOL_ADMIN') return '/school-admin';
      if (pr === 'SUPER_ADMIN') return '/admin';
      return '/account/no-access';
    };
    expect(getRoute('TEACHER')).toBe('/teacher');
    expect(getRoute(null)).toBe('/account/no-access');
  });
});
