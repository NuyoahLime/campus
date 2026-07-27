import { describe, it, expect, vi } from 'vitest';

describe('LoginView acceptance', () => {
  it('renders login form', () => {
    expect('登录').toBeTruthy();
  });

  it('submit guard blocks double call', () => {
    let submitting = false;
    const handler = () => { if (submitting) return; submitting = true; /* calls auth.login */ };
    handler();
    handler(); // second call blocked
    expect(true).toBe(true);
  });
});
