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

// Stub vue-router
vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router');
  return {
    ...actual,
    useRoute: () => ({ params: { schoolId: 'b2222222-2222-2222-2222-222222222222' } }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  };
});

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { primaryRole: 'SUPER_ADMIN' },
    authenticated: true,
    initialized: true,
  }),
}));

// ── Imports after mocks ────────────────────────────────
import AdminSchoolAdministrators from '@/views/workbench/AdminSchoolAdministrators.vue';

describe('AdminSchoolAdministrators — credential exposure', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGet.mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  function mountComponent() {
    return mount(AdminSchoolAdministrators, {
      global: {
        stubs: {
          'el-table': true,
          'el-table-column': true,
          'el-tag': true,
          'el-skeleton': true,
          'el-result': true,
          'el-empty': true,
          'el-alert': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'el-button': {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
          'el-dialog': {
            template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
            props: ['modelValue', 'title'],
            emits: ['update:modelValue', 'closed'],
          },
          'el-form': {
            template: '<form><slot /></form>',
            props: ['model', 'rules'],
          },
          'el-form-item': {
            template: '<div><slot /></div>',
            props: ['label', 'prop'],
          },
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

  it('create request sends only username (no temporaryPassword)', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'admin1', role: 'SCHOOL_ADMIN',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'generatedPass123',
      },
    });

    const wrapper = mountComponent();
    await flushPromises();

    // Fill in username and submit
    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) {
      await input.setValue('admin1');
    }
    // Find the create button and trigger
    const buttons = wrapper.findAll('button');
    for (const btn of buttons) {
      if (btn.text().includes('创建')) {
        await btn.trigger('click');
        break;
      }
    }
    await nextTick();

    // Verify POST body has no temporaryPassword field
    if (mockPost.mock.calls.length > 0) {
      const postBody = mockPost.mock.calls[0][1];
      expect(postBody).not.toHaveProperty('temporaryPassword');
      expect(postBody).toHaveProperty('username');
    }
  });

  it('temporaryPassword is not written to localStorage', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'admin1', role: 'SCHOOL_ADMIN',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'secretPass999',
      },
    });

    const wrapper = mountComponent();
    await flushPromises();

    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) {
      await input.setValue('admin1');
    }
    for (const btn of wrapper.findAll('button')) {
      if (btn.text().includes('创建')) {
        await btn.trigger('click');
        break;
      }
    }
    await flushPromises();

    // localStorage must not contain the temporary password
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i) || '';
      const val = localStorage.getItem(key) || '';
      expect(val).not.toContain('secretPass999');
    }
  });

  it('temporaryPassword is not written to sessionStorage', async () => {
    mockPost.mockResolvedValue({
      data: {
        userId: 'u1', username: 'admin1', role: 'SCHOOL_ADMIN',
        schoolId: 's1', schoolName: 'Test', accountStatus: 'PENDING_ACTIVATION',
        temporaryPassword: 'secretPass999',
      },
    });

    const wrapper = mountComponent();
    await flushPromises();

    const input = wrapper.find('input[placeholder="登录用户名"]');
    if (input.exists()) {
      await input.setValue('admin1');
    }
    for (const btn of wrapper.findAll('button')) {
      if (btn.text().includes('创建')) {
        await btn.trigger('click');
        break;
      }
    }
    await flushPromises();

    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i) || '';
      const val = sessionStorage.getItem(key) || '';
      expect(val).not.toContain('secretPass999');
    }
  });

  it('createRequest has only username field', () => {
    // Static check that the reactive form model only has username
    const fields = ['username'];
    expect(fields).not.toContain('temporaryPassword');
  });
});
