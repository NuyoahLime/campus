import axios from 'axios';
import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: attach CSRF token to write requests
http.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const method = (config.method || 'get').toLowerCase();
  if (['post', 'put', 'patch', 'delete'].includes(method)) {
    try {
      const { getCsrfToken } = await import('./csrf');
      const csrf = await getCsrfToken();
      config.headers.set(csrf.headerName, csrf.token);
    } catch {
      // CSRF fetch failed — let request proceed without token (server will reject if required)
    }
  }
  return config;
});

// Response interceptor: normalize errors and handle 401
http.interceptors.response.use(
  (response) => {
    // Clear CSRF cache after every write request — Spring Security rotates the token
    const method = (response.config.method || 'get').toLowerCase();
    if (['post', 'put', 'patch', 'delete'].includes(method)) {
      import('./csrf').then(({ clearCsrfToken }) => clearCsrfToken());
    }
    return response;
  },
  (error: AxiosError<{ message?: string; code?: string }>) => {
    if (!error.response) {
      throw new ApiError(0, '网络连接失败，请检查网络后重试');
    }
    const status = error.response.status;
    const serverMessage = error.response.data?.message;
    const message = serverMessage || getDefaultMessage(status);

    // On 401, only clear local session for non-auth-attempt requests.
    // Auth-attempt endpoints (login / activate / register) returning 401
    // must NOT clear an existing session — failed auth is not a session loss.
    if (status === 401 && !isAuthenticationAttempt(error.config?.url)) {
      import('@/stores/auth').then(({ useAuthStore }) => {
        useAuthStore().clearLocalSession();
      });
    }

    // Also clear CSRF cache after failed write requests
    if (error.config) {
      const method = (error.config.method || 'get').toLowerCase();
      if (['post', 'put', 'patch', 'delete'].includes(method)) {
        import('./csrf').then(({ clearCsrfToken }) => clearCsrfToken());
      }
    }

    throw new ApiError(status, message, error.response.data?.code);
  },
);

/**
 * Returns true when the URL is an explicit authentication attempt
 * (login, activate, register). These endpoints returning 401 must NOT
 * clear an existing session — they represent a failed auth attempt,
 * not a loss of already-authenticated identity.
 *
 * /auth/me is intentionally excluded: a 401 from /auth/me means the
 * current session is genuinely invalid and local state should be cleared.
 */
function isAuthenticationAttempt(url?: string): boolean {
  if (!url) return false;
  return (
    url.includes('/v1/auth/login') ||
    url.includes('/v1/auth/activate') ||
    url.includes('/v1/auth/register')
  );
}

function getDefaultMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数有误';
    case 401:
      return '请先登录';
    case 403:
      return '没有访问权限';
    case 404:
      return '请求的资源不存在';
    case 409:
      return '操作冲突，请刷新后重试';
    case 500:
      return '服务器内部错误，请稍后重试';
    default:
      return `请求失败 (${status})`;
  }
}

export default http;
