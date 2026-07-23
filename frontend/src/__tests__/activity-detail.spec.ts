import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import ActivityDetailView from '@/views/activities/ActivityDetailView.vue';
import * as activityApi from '@/api/public-activity';
import { ApiError } from '@/api/http';
import type { PublicActivityDetail } from '@/types/activity';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/activities', component: { template: '<div>list</div>' } },
      { path: '/activities/:activityId', component: ActivityDetailView, props: true },
      { path: '/projects/:projectId', component: { template: '<div>project</div>' }, props: true },
    ],
  });
}

function sampleDetail(overrides: Partial<PublicActivityDetail> = {}): PublicActivityDetail {
  return {
    id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    title: '春季运动会',
    description: '年度体育盛会',
    status: 'IN_PROGRESS',
    projects: [{ projectId: 'p1' }, { projectId: 'p2' }],
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('ActivityDetailView', () => {
  it('shows activity detail on success', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivityById').mockResolvedValue(sampleDetail());

    const router = makeRouter();
    await router.push('/activities/test');
    await router.isReady();

    const wrapper = mount(ActivityDetailView, {
      props: { activityId: 'test' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('春季运动会');
    expect(wrapper.text()).toContain('年度体育盛会');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('查看项目详情');
  });

  it('shows 404 for non-existent activity', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivityById').mockRejectedValue(
      new ApiError(404, 'not found'),
    );

    const router = makeRouter();
    await router.push('/activities/non-existent');
    await router.isReady();

    const wrapper = mount(ActivityDetailView, {
      props: { activityId: 'non-existent' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('404');
    expect(wrapper.text()).toContain('不存在');
  });

  it('shows empty projects message', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivityById').mockResolvedValue(
      sampleDetail({ projects: [] }),
    );

    const router = makeRouter();
    await router.push('/activities/test');
    await router.isReady();

    const wrapper = mount(ActivityDetailView, {
      props: { activityId: 'test' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无项目');
  });

  it('shows description placeholder when null', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivityById').mockResolvedValue(
      sampleDetail({ description: null }),
    );

    const router = makeRouter();
    await router.push('/activities/test');
    await router.isReady();

    const wrapper = mount(ActivityDetailView, {
      props: { activityId: 'test' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无说明');
  });
});
