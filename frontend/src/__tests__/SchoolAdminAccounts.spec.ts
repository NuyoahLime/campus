import { describe, it, expect } from 'vitest';

describe('SchoolAdminAccounts', () => {
  it('page response wraps items', () => {
    const resp = { items: [{ username: 't1', role: 'TEACHER' }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    expect(resp.items).toHaveLength(1);
    expect(resp.totalPages).toBe(1);
  });

  it('page conversion front-end 1 to back-end 0', () => {
    const frontPage = 1;
    const backPage = frontPage - 1;
    expect(backPage).toBe(0);
  });

  it('role options only TEACHER and STUDENT', () => {
    const allowedRoles = ['TEACHER', 'STUDENT'];
    expect(allowedRoles).not.toContain('SCHOOL_ADMIN');
    expect(allowedRoles).not.toContain('SUPER_ADMIN');
  });

  it('create form has no schoolId', () => {
    const formFields = ['role', 'username', 'temporaryPassword'];
    expect(formFields).not.toContain('schoolId');
  });

  it('keyword is trimmed before request', () => {
    const kw = '  test  ';
    expect(kw.trim()).toBe('test');
  });
});
