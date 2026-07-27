import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import SchoolAdminActivityCreate from '@/views/workbench/SchoolAdminActivityCreate.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { ActivityMutationResponse } from '@/types/school-admin-activity';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/school-admin/activities/new', component: SchoolAdminActivityCreate },
      { path: '/school-admin/activities/:activityId', component: { template: '<div>detail</div>' }, props: true },
    ],
  });
}

function mockCreated(id = 'new-id'): ActivityMutationResponse {
  return { activityId: id, executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED' };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('SchoolAdminActivityCreate', () => {
  it('renders create form', async () => {
    vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('创建活动');
  });

  it('does not request when title is empty', async () => {
    const spy = vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Submit without filling title
    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(spy).not.toHaveBeenCalled();
  });

  it('rejects endTime before startTime without requesting', async () => {
    const spy = vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Manually set time values and form data
    const vm = wrapper.vm as unknown as { form: Record<string, string> };
    vm.form.title = 'Test Title';
    vm.form.startTime = '2026-09-02T00:00:00';
    vm.form.endTime = '2026-09-01T00:00:00';

    await wrapper.find('form').trigger('submit');
    await flushPromises();

    expect(spy).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('结束时间不得早于开始时间');
  });

  it('navigates to detail on success', async () => {
    vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated('created-123'));

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    // Fill title and submit
    const vm = wrapper.vm as unknown as { form: Record<string, string>; handleSubmit: () => Promise<void> };
    vm.form.title = 'Valid Title';

    await vm.handleSubmit();
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/school-admin/activities/created-123');
  });

  it('shows error on API failure', async () => {
    vi.spyOn(api, 'createActivity').mockRejectedValue(new ApiError(400, '创建失败：标题过长'));

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    const vm = wrapper.vm as unknown as { form: Record<string, string>; handleSubmit: () => Promise<void> };
    vm.form.title = 'Test';
    await vm.handleSubmit();
    await flushPromises();

    expect(wrapper.text()).toContain('创建失败');
  });

  it('converts local time to ISO when submitting', async () => {
    const spy = vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());

    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();

    const wrapper = mount(SchoolAdminActivityCreate, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    const vm = wrapper.vm as unknown as { form: Record<string, string>; handleSubmit: () => Promise<void> };
    vm.form.title = 'Time Test';
    vm.form.startTime = '2026-09-01T08:00:00';
    vm.form.endTime = '2026-09-02T17:00:00';
    await vm.handleSubmit();
    await flushPromises();

    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({
        startTime: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/) as unknown as string,
        endTime: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/) as unknown as string,
      })
    );
  });
});
