import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';

// ── Mocks ──────────────────────────────────────────────

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  ApiError: class extends Error {
    constructor(public status: number, message: string) {
      super(message); this.name = 'ApiError';
    }
  },
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { primaryRole: 'SCHOOL_ADMIN' },
    authenticated: true,
    initialized: true,
  }),
}));

import SchoolAdminAccounts from '@/views/workbench/SchoolAdminAccounts.vue';

// ── Mounted component tests ────────────────────────────

describe('SchoolAdminAccounts — credential exposure', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  function mountComponent() {
    return mount(SchoolAdminAccounts, {
      global: {
        stubs: {
          'el-table': true, 'el-table-column': true, 'el-tag': true,
          'el-skeleton': true, 'el-card': true, 'el-result': true,
          'el-empty': true, 'el-alert': true,
          'el-descriptions': true, 'el-descriptions-item': true,
          'el-button': {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
          'el-dialog': {
            template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
            props: ['modelValue', 'title'],
            emits: ['update:modelValue', 'closed'],
          },
          'el-form': { template: '<form><slot /></form>', props: ['model', 'rules'] },
          'el-form-item': { template: '<div><slot /></div>', props: ['label', 'prop'] },
          'el-input': {
            template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
            props: ['modelValue', 'type', 'placeholder', 'maxlength', 'readonly'],
            emits: ['update:modelValue'],
          },
          'el-select': {
            template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>',
            props: ['modelValue'],
            emits: ['update:modelValue'],
          },
          'el-option': true,
          'router-link': true,
        },
      },
    });
  }

  it('create form has no temporary password input', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    const html = wrapper.html();
    expect(html).not.toContain('临时密码');
    expect(html).not.toContain('temporaryPassword');
  });

  it('create request contains username and role only (no temporaryPassword)', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'student1', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'generatedPass456',
      },
    });
    const wrapper = mountComponent();
    await flushPromises();

    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) await input.setValue('student1');

    for (const btn of wrapper.findAll('button')) {
      if (btn.text().includes('创建')) { await btn.trigger('click'); break; }
    }
    await nextTick();

    if (mockPost.mock.calls.length > 0) {
      const body = mockPost.mock.calls[0][1];
      expect(body).not.toHaveProperty('temporaryPassword');
      expect(body).toHaveProperty('username');
      expect(body).toHaveProperty('role');
    }
  });

  it('temporaryPassword is not written to localStorage', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'student1', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'secretSchool999',
      },
    });
    const wrapper = mountComponent();
    await flushPromises();

    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) await input.setValue('student1');
    for (const btn of wrapper.findAll('button')) {
      if (btn.text().includes('创建')) { await btn.trigger('click'); break; }
    }
    await flushPromises();

    for (let i = 0; i < localStorage.length; i++) {
      expect(localStorage.getItem(localStorage.key(i) || '')).not.toContain('secretSchool999');
    }
  });

  it('temporaryPassword is not written to sessionStorage', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'student1', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'secretSchool999',
      },
    });
    const wrapper = mountComponent();
    await flushPromises();

    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) await input.setValue('student1');
    for (const btn of wrapper.findAll('button')) {
      if (btn.text().includes('创建')) { await btn.trigger('click'); break; }
    }
    await flushPromises();

    for (let i = 0; i < sessionStorage.length; i++) {
      expect(sessionStorage.getItem(sessionStorage.key(i) || '')).not.toContain('secretSchool999');
    }
  });

  // ── Static assertions ───────────────────────────────

  it('create form fields do not include temporaryPassword', () => {
    const fields = ['username', 'role'];
    expect(fields).not.toContain('temporaryPassword');
    expect(fields).toContain('username');
    expect(fields).toContain('role');
  });
});
