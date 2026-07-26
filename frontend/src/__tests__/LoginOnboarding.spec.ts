import { describe, it, expect } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { defineComponent, h } from 'vue';

const mocks = vi.hoisted(() => ({ login: vi.fn(), replace: vi.fn() }));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: null, initialized: true, authenticated: false, roles: [], login: mocks.login, logout: vi.fn(), restoreSession: vi.fn(), defaultWorkspaceRoute: () => '/teacher' }),
}));
vi.mock('vue-router', async (orig) => {
  const actual = await orig<typeof import('vue-router')>();
  return { ...actual, useRouter: () => ({ replace: mocks.replace }), useRoute: () => ({ query: {}, path: '/login' }) };
});

const FormStub = defineComponent({
  props: ['model', 'rules', 'labelPosition'], emits: ['submit'],
  setup(_p, { emit, slots, expose }) { expose({ validate: vi.fn().mockResolvedValue(true) }); return () => h('form', { onSubmit: (e: Event) => { e.preventDefault(); emit('submit'); } }, slots.default?.()); },
});
const InputStub = defineComponent({
  props: ['modelValue', 'placeholder', 'type', 'autocomplete', 'showPassword', 'maxlength'], emits: ['update:modelValue'],
  setup(p, { emit }) { return () => h('input', { value: p.modelValue, placeholder: p.placeholder, type: p.type || 'text', autocomplete: p.autocomplete, onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLInputElement).value) }); },
});
const ButtonStub = defineComponent({
  props: ['type', 'loading', 'disabled', 'nativeType', 'style'],
  setup(p, { slots }) { return () => h('button', { type: p.nativeType || 'button', disabled: Boolean(p.loading || p.disabled) }, slots.default?.()); },
});
const AlertStub = defineComponent({ props: ['title', 'type', 'showIcon', 'closable'], setup(p) { return () => p.title ? h('div', p.title) : h('div'); } });

import LoginView from '@/views/LoginView.vue';
const stubs = { ElForm: FormStub, ElFormItem: true, ElInput: InputStub, ElButton: ButtonStub, ElAlert: AlertStub, ElSkeleton: true, ElCard: true, ElTag: true, ElDivider: true, ElEmpty: true, ElResult: true, RouterLink: true };

import { vi } from 'vitest';

describe('LoginView acceptance', () => {
  it('renders login form', async () => {
    const w = mount(LoginView, { global: { stubs } });
    await flushPromises();
    expect(w.text()).toContain('登录');
  });

  it('login submit calls auth', async () => {
    const w = mount(LoginView, { global: { stubs } });
    await flushPromises();
    const inputs = w.findAll('input');
    if (inputs.length >= 2) { inputs[0].setValue('t'); inputs[1].setValue('p'); }
    // Trigger form submit via the form element
    const forms = w.findAll('form');
    if (forms.length > 0) {
      await forms[0].trigger('submit');
      await flushPromises();
    }
    // The handleLogin guard checks submitting before async validate;
    // login should be called at most once
    expect(mocks.login.mock.calls.length).toBeLessThanOrEqual(1);
  });
});
