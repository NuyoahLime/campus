import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import ProjectListView from '@/views/projects/ProjectListView.vue';
import * as projectApi from '@/api/public-project';
import { ApiError } from '@/api/http';
import type { PageResponse } from '@/types/api';
import type { PublicProjectItem } from '@/types/project';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/projects', component: ProjectListView },
      { path: '/projects/:projectId', component: { template: '<div>detail</div>' }, props: true },
    ],
  });
}

function makePage<T>(items: T[], overrides: Partial<PageResponse<T>> = {}): PageResponse<T> {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / 20),
    hasNext: false,
    ...overrides,
  };
}

function sampleItem(overrides: Partial<PublicProjectItem> = {}): PublicProjectItem {
  return {
    projectId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    name: '立定跳远',
    category: '体育',
    descriptionSummary: '测试项目',
    scoreStorageType: 'INTEGER',
    comparisonDirection: 'HIGHER_BETTER',
    scoreUnit: '厘米',
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('ProjectListView', () => {
  it('shows projects on success', async () => {
    const items = [sampleItem(), sampleItem({ projectId: 'b', name: '100米跑' })];
    vi.spyOn(projectApi, 'fetchPublicProjects').mockResolvedValue(makePage(items));

    const router = makeRouter();
    await router.push('/projects');
    await router.isReady();

    const wrapper = mount(ProjectListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('立定跳远');
    expect(wrapper.text()).toContain('100米跑');
  });

  it('shows empty state', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjects').mockResolvedValue(makePage([]));

    const router = makeRouter();
    await router.push('/projects');
    await router.isReady();

    const wrapper = mount(ProjectListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('没有找到匹配的项目');
  });

  it('shows error state', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjects').mockRejectedValue(
      new ApiError(500, 'test error'),
    );

    const router = makeRouter();
    await router.push('/projects');
    await router.isReady();

    const wrapper = mount(ProjectListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('加载失败');
  });

  it('restores filter state from URL query', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjects').mockResolvedValue(makePage([sampleItem()]));

    const router = makeRouter();
    await router.push('/projects?keyword=篮球&page=1');
    await router.isReady();

    mount(ProjectListView, {
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(projectApi.fetchPublicProjects).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '篮球' }),
      0,
      20,
    );
  });
});
