import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';

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

// ── Stubs ──────────────────────────────────────────────

function mountComponent() {
  return mount(SchoolAdminAccounts, {
    global: {
      stubs: {
        'el-table': true, 'el-table-column': true, 'el-tag': true,
        'el-skeleton': true, 'el-card': { template: '<div><slot /></div>' }, 'el-result': true,
        'el-empty': true, 'el-alert': true,
        'el-descriptions': { template: '<div><slot /></div>', props: ['column', 'border'] },
        'el-descriptions-item': { template: '<div><slot /></div>', props: ['label'] },
        'el-form': { template: '<form><slot /></form>', props: ['model', 'rules', 'inline'] },
        'el-form-item': { template: '<div><slot /></div>', props: ['label', 'prop'] },
        'el-option': true,
        'el-select': {
          template: '<select data-testid="select"><slot /></select>',
        },
        'el-input': {
          template: '<input :data-testid="$attrs[\'data-testid\']" :value="modelValue" :placeholder="placeholder" :readonly="readonly" :type="type" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          props: ['modelValue', 'type', 'placeholder', 'maxlength', 'readonly'],
          emits: ['update:modelValue'],
        },
        'el-button': {
          template: '<button :data-testid="$attrs[\'data-testid\']" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>',
          props: { disabled: Boolean, loading: Boolean },
          emits: ['click'],
        },
        'el-dialog': {
          template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
          props: { modelValue: Boolean, title: String, width: String },
          emits: ['update:modelValue', 'closed'],
        },
        'router-link': true,
      },
    },
  });
}

// ── Tests ──────────────────────────────────────────────

describe('SchoolAdminAccounts — credential exposure', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({ data: [] });
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'student1', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test School', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'schoolGeneratedPass888',
      },
    });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  async function completeSuccessfulCreation(wrapper: ReturnType<typeof mountComponent>) {
    await flushPromises();
    await wrapper.get('[data-testid="open-create-account"]').trigger('click');
    await wrapper.get('[data-testid="create-account-username"]').setValue('student1');
    await wrapper.get('[data-testid="submit-create-account"]').trigger('click');
    await flushPromises();
  }

  it('POST is called with exact URL and body (no temporaryPassword)', async () => {
    const wrapper = mountComponent();
    await completeSuccessfulCreation(wrapper);

    expect(mockPost).toHaveBeenCalledTimes(1);
    const [url, body] = mockPost.mock.calls[0];
    expect(url).toBe('/v1/school-admin/accounts');
    expect(body).toEqual({ role: 'TEACHER', username: 'student1' });
    expect(body).not.toHaveProperty('temporaryPassword');
  });

  it('credential state is set after successful creation', async () => {
    const wrapper = mountComponent();
    await completeSuccessfulCreation(wrapper);

    const vm = wrapper.vm as unknown as { credential: { username: string; role: string; temporaryPassword: string } };
    expect(vm.credential.temporaryPassword).toBe('schoolGeneratedPass888');
    expect(vm.credential.username).toBe('student1');
  });

  it('clearCredential clears temporary password and username', async () => {
    const wrapper = mountComponent();
    await completeSuccessfulCreation(wrapper);

    const vm = wrapper.vm as unknown as {
      credential: { username: string; role: string; temporaryPassword: string };
      clearCredential: () => void;
    };
    expect(vm.credential.temporaryPassword).toBe('schoolGeneratedPass888');

    vm.clearCredential();

    expect(vm.credential.temporaryPassword).toBe('');
    expect(vm.credential.username).toBe('');
  });

  it('second creation does not expose previous password', async () => {
    const wrapper = mountComponent();

    mockPost.mockResolvedValueOnce({
      data: {
        userId: 'u1', username: 'student1', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'firstPassAAA',
      },
    });
    await completeSuccessfulCreation(wrapper);

    const vm = wrapper.vm as unknown as {
      credential: { temporaryPassword: string };
      clearCredential: () => void;
    };
    expect(vm.credential.temporaryPassword).toBe('firstPassAAA');
    vm.clearCredential();

    mockPost.mockResolvedValueOnce({
      data: {
        userId: 'u2', username: 'student2', role: 'STUDENT',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'secondPassBBB',
      },
    });
    await completeSuccessfulCreation(wrapper);

    expect(vm.credential.temporaryPassword).toBe('secondPassBBB');
    expect(vm.credential.temporaryPassword).not.toBe('firstPassAAA');
  });

  it('temporaryPassword is not written to localStorage or sessionStorage', async () => {
    const localSpy = vi.spyOn(Storage.prototype, 'setItem');
    const sessionSpy = vi.spyOn(Storage.prototype, 'setItem');

    const wrapper = mountComponent();
    await completeSuccessfulCreation(wrapper);

    for (const call of localSpy.mock.calls) {
      expect(call[1]).not.toContain('schoolGeneratedPass888');
    }
    for (const call of sessionSpy.mock.calls) {
      expect(call[1]).not.toContain('schoolGeneratedPass888');
    }

    localSpy.mockRestore();
    sessionSpy.mockRestore();
  });
});
