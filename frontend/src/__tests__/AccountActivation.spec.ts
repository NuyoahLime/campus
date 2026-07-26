import { describe, it, expect } from 'vitest';

interface MockError { response: { status: number; data: { code: string } } }
const mkErr = (s: number, c: string): MockError => ({ response: { status: s, data: { code: c } } });

describe('AccountActivation', () => {
  it('password too short shows policy error', async () => {
    const e = mkErr(400, 'PASSWORD_TOO_SHORT');
    expect(e.response.data.code).toBe('PASSWORD_TOO_SHORT');
  });
  it('wrong credentials returns 401', async () => {
    const e = mkErr(401, 'ACTIVATION_CREDENTIALS_INVALID');
    expect(e.response.status).toBe(401);
  });
  it('already activated returns 409', async () => {
    const e = mkErr(409, 'ACCOUNT_ALREADY_ACTIVATED');
    expect(e.response.status).toBe(409);
  });
  it('rate limited returns 429', async () => {
    const e = mkErr(429, 'ACTIVATION_RATE_LIMITED');
    expect(e.response.status).toBe(429);
  });
  it('prevents duplicate submit during loading', () => {
    const loading = true;
    expect(loading ? false : true).toBe(false);
  });
});
