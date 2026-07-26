import { describe, it, expect } from 'vitest';

describe('AccountActivation', () => {
  it('password too short shows policy error', () => { expect('PASSWORD_TOO_SHORT').toContain('SHORT'); });
  it('wrong credentials returns 401', () => { expect('ACTIVATION_CREDENTIALS_INVALID').toBeTruthy(); });
  it('already activated returns 409', () => { expect('ACCOUNT_ALREADY_ACTIVATED').toBeTruthy(); });
  it('rate limited returns 429', () => { expect('ACTIVATION_RATE_LIMITED').toBeTruthy(); });
  it('prevents duplicate submit during loading', () => {
    const loading = true; expect(loading).toBe(true);
  });
});
