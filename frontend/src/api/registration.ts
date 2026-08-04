import http from './http';

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export interface RegisterResponse {
  username: string;
  verificationRequired: true;
  nextAction: 'VERIFY_EMAIL';
}

export interface ResendVerificationPayload {
  email: string;
}

export interface ResendVerificationResponse {
  message: string;
}

export async function register(payload: RegisterPayload): Promise<RegisterResponse> {
  const response = await http.post<RegisterResponse>('/v1/auth/register', payload);
  return response.data;
}

export async function verifyEmail(token: string): Promise<void> {
  await http.post('/v1/auth/verify-email', { token });
}

export async function resendVerification(
  email: string,
): Promise<ResendVerificationResponse> {
  const response = await http.post<ResendVerificationResponse>(
    '/v1/auth/resend-verification',
    { email },
  );
  return response.data;
}
