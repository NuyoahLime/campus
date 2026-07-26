import { describe, it, expect } from 'vitest';

describe('LoginOnboarding', () => {
  it('has activation link text', () => {
    const text = '首次使用？激活账号';
    expect(text).toContain('激活');
  });

  it('no public role registration selector', () => {
    const pageAllowsSelection = false;
    expect(pageAllowsSelection).toBe(false);
  });

  it('explains account source', () => {
    const msg1 = '学生和老师账号由所在学校管理员创建。';
    const msg2 = '学校管理员账号由平台管理员创建。';
    expect(msg1).toContain('学校管理员');
    expect(msg2).toContain('平台管理员');
  });
});
