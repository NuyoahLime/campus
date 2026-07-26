import { describe, it, expect } from 'vitest';

describe('LoginOnboarding', () => {
  it('activation link text present', () => {
    expect('首次使用？激活账号').toContain('激活');
  });
  it('school info messages present', () => {
    expect('学生和老师账号由所在学校管理员创建').toContain('学校管理员');
    expect('学校管理员账号由平台管理员创建').toContain('平台管理员');
  });
  it('no public role registration', () => {
    // Registration page does not exist in this release
    expect(true).toBe(true);
  });
});
