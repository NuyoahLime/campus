import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { fetchMe, login as apiLogin, logout as apiLogout } from '@/api/auth';
import type { AuthContextResponse } from '@/types/auth';
import { ApiError } from '@/api/http';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthContextResponse | null>(null);
  const initialized = ref(false);
  const loading = ref(false);

  const authenticated = computed(() => user.value !== null);
  const roles = computed(() => user.value?.roles ?? []);
  const schoolMemberships = computed(() => user.value?.schoolMemberships ?? []);

  function hasRole(role: string): boolean {
    return roles.value.includes(role);
  }

  function hasAnyRole(checkRoles: string[]): boolean {
    return checkRoles.some((r) => roles.value.includes(r));
  }

  function defaultWorkspaceRoute(): string {
    if (!user.value) return '/login';
    switch (user.value.primaryRole) {
      case 'SUPER_ADMIN':
        return '/admin';
      case 'SCHOOL_ADMIN':
        return '/school-admin';
      case 'TEACHER':
        return '/teacher';
      case 'STUDENT':
        return '/student';
      case 'REGISTERED_USER':
        return '/onboarding';
      default:
        return '/account/no-access';
    }
  }

  async function restoreSession(): Promise<void> {
    if (initialized.value) return;
    loading.value = true;
    try {
      const me = await fetchMe();
      user.value = me;
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        user.value = null;
      } else {
        console.error('Session restore failed:', e);
      }
    } finally {
      initialized.value = true;
      loading.value = false;
    }
  }

  async function login(username: string, password: string): Promise<AuthContextResponse> {
    const result = await apiLogin({ username, password });
    user.value = result;
    initialized.value = true;
    return result;
  }

  async function logout(): Promise<void> {
    try {
      await apiLogout();
    } finally {
      user.value = null;
      initialized.value = true;
    }
  }

  /** Clear local auth state without calling the server (e.g. on 401). */
  function clearLocalSession(): void {
    user.value = null;
    initialized.value = true;
  }

  return {
    user,
    initialized,
    loading,
    authenticated,
    roles,
    schoolMemberships,
    hasRole,
    hasAnyRole,
    defaultWorkspaceRoute,
    restoreSession,
    login,
    logout,
    clearLocalSession,
  };
});
