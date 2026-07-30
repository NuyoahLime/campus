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
  (response) => response,
  (error: AxiosError<{ message?: string; code?: string }>) => {
    if (!error.response) {
      throw new ApiError(0, '网络连接失败，请检查网络后重试');
    }
    const status = error.response.status;
    const serverMessage = error.response.data?.message;
    const message = serverMessage || getDefaultMessage(status);

    // On 401, clear auth state to force re-login
    if (status === 401) {
      import('@/stores/auth').then(({ useAuthStore }) => {
        const store = useAuthStore();
        store.logout().catch(() => {});
      });
    }

    throw new ApiError(status, message, error.response.data?.code);
  },
);

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
