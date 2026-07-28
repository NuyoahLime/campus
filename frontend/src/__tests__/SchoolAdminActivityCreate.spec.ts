import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import SchoolAdminActivityCreate from '@/views/workbench/SchoolAdminActivityCreate.vue';
import * as api from '@/api/school-admin-activity';
import { ApiError } from '@/api/http';
import type { ActivityMutationResponse } from '@/types/school-admin-activity';

const ACTIVITY_ID = '11111111-1111-4111-8111-111111111111';

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

function mockCreated(): ActivityMutationResponse {
  return { activityId: ACTIVITY_ID, executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED' };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('SchoolAdminActivityCreate', () => {
  it('realFormSubmitSendsCompletePayload', async () => {
    const spy = vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());
    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();
    const wrapper = mount(SchoolAdminActivityCreate, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    // Fill title via real input
    await wrapper.find('input[placeholder="请输入活动名称"]').setValue('Test Activity');
    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();
    expect(spy).toHaveBeenCalledWith(expect.objectContaining({ title: 'Test Activity' }));
  });

  it('duplicateSubmitCallsCreateOnce', async () => {
    let resolvePromise: (v: ActivityMutationResponse) => void = () => {};
    const deferred = new Promise<ActivityMutationResponse>(r => { resolvePromise = r; });
    const spy = vi.spyOn(api, 'createActivity').mockReturnValue(deferred);
    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();
    const wrapper = mount(SchoolAdminActivityCreate, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    await wrapper.find('input[placeholder="请输入活动名称"]').setValue('Test');
    // Trigger submit twice rapidly
    await wrapper.find('form').trigger('submit.prevent');
    await wrapper.find('form').trigger('submit.prevent');
    resolvePromise(mockCreated());
    await flushPromises();
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('successNavigatesToUuidDetail', async () => {
    vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());
    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();
    const wrapper = mount(SchoolAdminActivityCreate, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    await wrapper.find('input[placeholder="请输入活动名称"]').setValue('Test');
    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe(`/school-admin/activities/${ACTIVITY_ID}`);
  });

  it('invalidTimeDoesNotRequest', async () => {
    const spy = vi.spyOn(api, 'createActivity').mockResolvedValue(mockCreated());
    const router = makeRouter();
    await router.push('/school-admin/activities/new');
    await router.isReady();
    const wrapper = mount(SchoolAdminActivityCreate, { global: { plugins: [router, createPinia(), ElementPlus] } });
    await flushPromises();
    const vm = wrapper.vm as unknown as { form: Record<string, string> };
    vm.form.title = 'Test';
    vm.form.startTime = '2026-09-02T00:00:00';
    vm.form.endTime = '2026-09-01T00:00:00';
    await wrapper.find('form').trigger('submit.prevent');
    await flushPromises();
    expect(spy).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('结束时间不得早于开始时间');
  });
});
