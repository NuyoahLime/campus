import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createRouter, createWebHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import ElementPlus from 'element-plus';
import ProjectDetailView from '@/views/projects/ProjectDetailView.vue';
import * as projectApi from '@/api/public-project';
import { ApiError } from '@/api/http';
import type { PublicProjectDetail } from '@/types/project';

function makeRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/projects', component: { template: '<div>list</div>' } },
      { path: '/projects/:projectId', component: ProjectDetailView, props: true },
    ],
  });
}

function sampleDetail(overrides: Partial<PublicProjectDetail> = {}): PublicProjectDetail {
  return {
    projectId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    name: '立定跳远',
    category: '体育',
    description: '跳远项目',
    venueRequirements: '操场',
    equipmentRequirements: '卷尺',
    rulesText: '规则文本',
    scoreStorageType: 'INTEGER',
    scoreIndicatorType: 'DISTANCE',
    comparisonDirection: 'HIGHER_BETTER',
    effectiveScoreRule: null,
    allowTie: false,
    scoreUnit: '厘米',
    decimalPlaces: 0,
    gradeOrder: null,
    ...overrides,
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('ProjectDetailView', () => {
  it('shows project detail on success', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjectById').mockResolvedValue(sampleDetail());

    const router = makeRouter();
    await router.push('/projects/a1b2c3d4-e5f6-7890-abcd-ef1234567890');
    await router.isReady();

    const wrapper = mount(ProjectDetailView, {
      props: { projectId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('立定跳远');
    expect(wrapper.text()).toContain('体育');
    expect(wrapper.text()).toContain('跳远项目');
    expect(wrapper.text()).toContain('操场');
    expect(wrapper.text()).toContain('厘米');
  });

  it('shows 404 for non-existent project', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjectById').mockRejectedValue(
      new ApiError(404, 'not found'),
    );

    const router = makeRouter();
    await router.push('/projects/non-existent');
    await router.isReady();

    const wrapper = mount(ProjectDetailView, {
      props: { projectId: 'non-existent' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('404');
    expect(wrapper.text()).toContain('不存在');
  });

  it('displays null fields as placeholder text', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjectById').mockResolvedValue(
      sampleDetail({ description: null, venueRequirements: null, equipmentRequirements: null }),
    );

    const router = makeRouter();
    await router.push('/projects/test');
    await router.isReady();

    const wrapper = mount(ProjectDetailView, {
      props: { projectId: 'test' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无说明');
    expect(wrapper.text()).toContain('暂无要求');
  });

  it('shows allowTie boolean as 是/否', async () => {
    vi.spyOn(projectApi, 'fetchPublicProjectById').mockResolvedValue(
      sampleDetail({ allowTie: true }),
    );

    const router = makeRouter();
    await router.push('/projects/test');
    await router.isReady();

    const wrapper = mount(ProjectDetailView, {
      props: { projectId: 'test' },
      global: { plugins: [router, createPinia(), ElementPlus] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('是');
  });
});
