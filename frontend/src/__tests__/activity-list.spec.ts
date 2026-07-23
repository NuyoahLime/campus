import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import ActivityListView from '@/views/activities/ActivityListView.vue';
import * as activityApi from '@/api/public-activity';
import { ApiError } from '@/api/http';
import type { PageResponse } from '@/types/api';
import type { PublicActivityItem } from '@/types/activity';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/activities', component: ActivityListView },
      { path: '/activities/:activityId', component: { template: '<div>detail</div>' }, props: true },
    ],
  });
}

function makePage<T>(items: T[]): PageResponse<T> {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / 20),
    hasNext: false,
  };
}

function sampleItem(overrides: Partial<PublicActivityItem> = {}): PublicActivityItem {
  return {
    id: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    title: '春季运动会',
    startTime: '2026-04-01T08:00:00Z',
    endTime: '2026-04-03T18:00:00Z',
    location: '体育馆',
    status: 'PUBLISHED',
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('ActivityListView', () => {
  it('shows activities on success', async () => {
    const items = [sampleItem(), sampleItem({ id: 'b', title: '秋季运动会' })];
    vi.spyOn(activityApi, 'fetchPublicActivities').mockResolvedValue(makePage(items));

    const router = makeRouter();
    await router.push('/activities');
    await router.isReady();

    const wrapper = mount(ActivityListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('春季运动会');
    expect(wrapper.text()).toContain('秋季运动会');
    expect(wrapper.text()).toContain('体育馆');
  });

  it('shows empty state', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivities').mockResolvedValue(makePage([]));

    const router = makeRouter();
    await router.push('/activities');
    await router.isReady();

    const wrapper = mount(ActivityListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无公开活动');
  });

  it('shows error state', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivities').mockRejectedValue(
      new ApiError(500, 'server error'),
    );

    const router = makeRouter();
    await router.push('/activities');
    await router.isReady();

    const wrapper = mount(ActivityListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('加载失败');
  });

  it('displays status labels in Chinese', async () => {
    const items = [sampleItem({ status: 'IN_PROGRESS' }), sampleItem({ id: 'b', status: 'ENDED' })];
    vi.spyOn(activityApi, 'fetchPublicActivities').mockResolvedValue(makePage(items));

    const router = makeRouter();
    await router.push('/activities');
    await router.isReady();

    const wrapper = mount(ActivityListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('已结束');
  });

  it('restores page from URL query', async () => {
    vi.spyOn(activityApi, 'fetchPublicActivities').mockResolvedValue(makePage([sampleItem()]));

    const router = makeRouter();
    await router.push('/activities?page=2');
    await router.isReady();

    mount(ActivityListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(activityApi.fetchPublicActivities).toHaveBeenCalledWith(1, 20);
  });
});
