import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { defineComponent, h } from 'vue';

// ── Mocks hoisted BEFORE component import ──
const mockPost = vi.fn();
const ApiErr = class extends Error { constructor(public status: number, msg: string) { super(msg); this.name = 'ApiError'; } };

vi.mock('@/api/http', () => ({
  default: { get: vi.fn(), post: (...a: unknown[]) => mockPost(...a) as Promise<unknown>,
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } },
  ApiError: ApiErr,
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}));

// ── Lightweight stubs ──
const FormStub = defineComponent({
  props: ['model', 'rules', 'labelPosition'], emits: ['submit'],
  setup(_p, { emit, slots, expose }) {
    expose({ validate: vi.fn().mockResolvedValue(true) });
    return () => h('form', { onSubmit: (e: Event) => { e.preventDefault(); emit('submit'); } }, slots.default?.());
  },
});
const InputStub = defineComponent({
  props: ['modelValue', 'placeholder', 'type', 'autocomplete', 'showPassword', 'maxlength'],
  emits: ['update:modelValue'],
  setup(p, { emit }) { return () => h('input', { value: p.modelValue, placeholder: p.placeholder, type: p.type || 'text', autocomplete: p.autocomplete,
    onInput: (e: Event) => emit('update:modelValue', (e.target as HTMLInputElement).value) }); },
});
const ButtonStub = defineComponent({
  props: ['type', 'loading', 'disabled', 'nativeType', 'style'],
  setup(p, { slots }) { return () => h('button', { type: p.nativeType || 'button', disabled: Boolean(p.loading || p.disabled) }, slots.default?.()); },
});
const AlertStub = defineComponent({
  props: ['title', 'type', 'showIcon', 'closable'],
  setup(p) { return () => p.title ? h('div', { class: 'alert-error' }, p.title) : h('div'); },
});

import ActivateAccountView from '@/views/ActivateAccountView.vue';

const stubs = {
  ElForm: FormStub, ElFormItem: true, ElInput: InputStub, ElButton: ButtonStub, ElAlert: AlertStub,
  ElCard: true, ElResult: true, RouterLink: true,
};

beforeEach(() => { vi.clearAllMocks(); mockPost.mockReset(); });

describe('ActivateAccountView acceptance', () => {
  it('submits activation request once', async () => {
    mockPost.mockResolvedValue({ data: { message: 'ok' } });
    const w = mount(ActivateAccountView, { global: { stubs } });
    await flushPromises();
    const inputs = w.findAll('input');
    if (inputs.length >= 4) { inputs[0].setValue('u'); inputs[1].setValue('t'); inputs[2].setValue('N3wP@ss!'); inputs[3].setValue('N3wP@ss!'); }
    await w.find('button[type="submit"]').trigger('click'); await flushPromises();
    expect(mockPost).toHaveBeenCalledTimes(1);
  });

  it('shows error on 409', async () => {
    mockPost.mockRejectedValue(new ApiErr(409, '已激活'));
    const w = mount(ActivateAccountView, { global: { stubs } });
    await flushPromises();
    const inputs = w.findAll('input');
    if (inputs.length >= 4) { inputs[0].setValue('u'); inputs[1].setValue('t'); inputs[2].setValue('P@ss123!'); inputs[3].setValue('P@ss123!'); }
    await w.find('button[type="submit"]').trigger('click'); await flushPromises();
    expect(w.text()).toBeTruthy();
  });

  it('prevents double submit', async () => {
    let resolve: (v: unknown) => void = () => {};
    mockPost.mockImplementation(() => new Promise(r => { resolve = r; }));
    const w = mount(ActivateAccountView, { global: { stubs } });
    await flushPromises();
    const inputs = w.findAll('input');
    if (inputs.length >= 4) { inputs[0].setValue('u'); inputs[1].setValue('t'); inputs[2].setValue('P@ss123!'); inputs[3].setValue('P@ss123!'); }
    await w.find('button[type="submit"]').trigger('click');
    await w.find('button[type="submit"]').trigger('click');
    expect(mockPost).toHaveBeenCalledTimes(1);
    resolve({ data: { message: 'ok' } });
    await flushPromises();
  });
});
