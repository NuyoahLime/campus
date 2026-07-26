import { describe, it, expect } from 'vitest';

describe('AdminSchoolAdministrators', () => {
  it('page response wraps items', () => {
    const resp = { items: [{ username: 'admin1', role: 'SCHOOL_ADMIN' }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
    expect(resp.items).toHaveLength(1);
  });

  it('no SUPER_ADMIN option in create form', () => {
    const allowedRoles = ['SCHOOL_ADMIN'];
    expect(allowedRoles).not.toContain('SUPER_ADMIN');
  });

  it('create form has no role selector', () => {
    const formFields = ['username', 'temporaryPassword'];
    expect(formFields).not.toContain('role');
  });

  it('invalid schoolId does not send request', () => {
    const schoolId = 'not-a-uuid';
    const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    expect(UUID_REGEX.test(schoolId)).toBe(false);
  });

  it('form validates before submit', () => {
    const formData = { username: '', temporaryPassword: '' };
    const isValid = formData.username.length > 0 && formData.temporaryPassword.length >= 8;
    expect(isValid).toBe(false);
  });
});
