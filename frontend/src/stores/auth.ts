import { defineStore } from 'pinia';
import * as authApi from '../api/auth';
import { ApiError } from '../api/http';
import type { CurrentUser, LoginRequest } from '../types/auth';

interface AuthState {
  currentUser: CurrentUser | null;
  loading: boolean;
  initialized: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    currentUser: null,
    loading: false,
    initialized: false
  }),

  getters: {
    isAuthenticated: (state) => state.currentUser !== null
  },

  actions: {
    async restoreSession() {
      this.loading = true;
      try {
        this.currentUser = await authApi.getMe();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          this.currentUser = null;
          return;
        }
        throw error;
      } finally {
        this.loading = false;
        this.initialized = true;
      }
    },

    async login(request: LoginRequest) {
      this.loading = true;
      try {
        await authApi.login(request);
        this.currentUser = await authApi.getMe();
      } finally {
        this.loading = false;
      }
    },

    async logout() {
      this.loading = true;
      try {
        await authApi.logout();
      } finally {
        this.currentUser = null;
        this.loading = false;
      }
    }
  }
});
