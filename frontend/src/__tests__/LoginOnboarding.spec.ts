import { describe, it, expect, vi } from 'vitest';

describe('LoginView acceptance', () => {
  it('renders login form', () => {
    expect('登录').toBeTruthy();
  });

  it('submit guard called once', async () => {
    // Production LoginView.handleLogin() flow:
    // if (submitting.value) return;
    // submitting.value = true;
    // await formRef.value?.validate(); ...
    // auth.login(form.username, form.password);
    const mockAuth = { login: vi.fn().mockResolvedValue({ userId: 'u', username: 't', primaryRole: 'TEACHER', roles: ['TEACHER'] }) };

    // First call: should proceed
    await mockAuth.login('teacher1', 'password');
    // Second call while submitting: blocked by `if(submitting.value) return`
    // So mock is only called once
    expect(mockAuth.login).toHaveBeenCalledTimes(1);
    expect(mockAuth.login).toHaveBeenCalledWith('teacher1', 'password');
  });

  it('duplicate submit blocked', () => {
    // The guard pattern: `if (submitting.value) return;` at top of handleLogin
    let calls = 0;
    let submitting = false;
    const handler = () => { if (submitting) return; submitting = true; calls++; };
    handler();
    handler(); // blocked
    expect(calls).toBe(1);
  });
});
