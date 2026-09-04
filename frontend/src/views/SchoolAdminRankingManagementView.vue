<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import RankingEntriesTable from '../components/RankingEntriesTable.vue';
import { ApiError } from '../api/http';
import { getManagedActivity, listManagedActivities } from '../api/activityManagement';
import { listPublicProjects } from '../api/challengeProjects';
import { getRanking } from '../api/ranking';
import {
  createRankingDefinition,
  disableRankingDefinition,
  enableRankingDefinition,
  generateRankingDefinition,
  getManagedRankingDefinition,
  listManagedRankingDefinitions,
  publishRankingVersion
} from '../api/rankingManagement';
import { schoolAdminNavigation as navigation } from '../router/schoolAdminNavigation';
import type { ActivityManagementDetail, ActivityManagementListItem } from '../types/activityManagement';
import type { ChallengeProjectListItem } from '../types/challengeProject';
import type { RankingDetail } from '../types/ranking';
import type {
  RankingDefinitionCreateForm,
  RankingManagementDefinition,
  RankingManagementLayer,
  RankingManagementVersion
} from '../types/rankingManagement';
import { labelForCategory } from '../utils/challengeProjectLabels';

const definitions = ref<RankingManagementDefinition[]>([]);
const selected = ref<RankingManagementDefinition | null>(null);
const published = ref<RankingDetail | null>(null);
const activities = ref<ActivityManagementListItem[]>([]);
const projects = ref<ChallengeProjectListItem[]>([]);
const selectedActivity = ref<ActivityManagementDetail | null>(null);
const loading = ref(true);
const loadingActivity = ref(false);
const saving = ref(false);
const generatingId = ref('');
const publishingId = ref('');
const togglingId = ref('');
const pageError = ref('');
const actionMessage = ref('');
const actionError = ref('');
const publishCandidate = ref<RankingManagementVersion | null>(null);
const form = ref({
  layer: 'L1' as RankingManagementLayer,
  name: '',
  activityId: '',
  activityProjectId: '',
  projectId: '',
  grade: '',
  className: '',
  activityPeriodStart: '',
  activityPeriodEnd: ''
});

const selectedActivityProject = computed(() =>
  selectedActivity.value?.projects.find((project) => project.id === form.value.activityProjectId) ?? null
);

const selectedChallengeProject = computed(() =>
  projects.value.find((project) => project.id === form.value.projectId) ?? null
);

const activityPeriodError = computed(() => {
  if (form.value.layer !== 'L2' || !form.value.activityPeriodStart || !form.value.activityPeriodEnd) {
    return '';
  }
  return new Date(form.value.activityPeriodStart).getTime() > new Date(form.value.activityPeriodEnd).getTime()
    ? 'Activity period start must not be after end.'
    : '';
});

const canCreate = computed(() => {
  if (saving.value || form.value.name.trim().length === 0 || activityPeriodError.value) return false;
  return form.value.layer === 'L1'
    ? Boolean(selectedActivityProject.value)
    : Boolean(selectedChallengeProject.value);
});

function resetCreateFields(layer: RankingManagementLayer = 'L1', keepName = true) {
  form.value = {
    layer,
    name: keepName ? form.value.name : '',
    activityId: '',
    activityProjectId: '',
    projectId: '',
    grade: '',
    className: '',
    activityPeriodStart: '',
    activityPeriodEnd: ''
  };
  selectedActivity.value = null;
}

function localDateTimeToIso(value: string) {
  return value ? new Date(value).toISOString() : null;
}

function buildL2DimensionFilters() {
  return JSON.stringify({
    selectionPolicy: 'BEST_SCORE',
    grade: form.value.grade.trim() || null,
    className: form.value.className.trim() || null,
    activityPeriodStart: localDateTimeToIso(form.value.activityPeriodStart),
    activityPeriodEnd: localDateTimeToIso(form.value.activityPeriodEnd)
  });
}

function describeDefinition(definition: RankingManagementDefinition) {
  const scope = definition.layer === 'L1'
    ? [definition.activityTitle || 'Activity unavailable', definition.projectName].filter(Boolean).join(' / ')
    : [
        definition.projectName,
        definition.grade ? `Grade ${definition.grade}` : '',
        definition.className ? `Class ${definition.className}` : ''
      ].filter(Boolean).join(' / ');
  return scope || definition.projectName;
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : 'Not recorded';
}

function describeError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in again.';
    if (error.status === 403) return 'This account cannot manage rankings.';
    if (error.status === 404) return 'The ranking resource was not found.';
    if (error.code === 'L2_RANKING_DEFINITION_ALREADY_EXISTS') {
      return 'An L2 ranking already exists for this project.';
    }
    if (error.status === 409) return 'The ranking state changed. Refresh and try again.';
    if (error.status === 400) return 'Check the required fields and try again.';
  }
  return 'The request failed. Try again shortly.';
}

async function loadDefinitions(selectId = selected.value?.id ?? '') {
  loading.value = true;
  pageError.value = '';
  try {
    const definitionPage = await listManagedRankingDefinitions(0, 50);
    definitions.value = definitionPage.items;
    const next = definitions.value.find((definition) => definition.id === selectId) ?? definitions.value[0] ?? null;
    selected.value = next;
    await refreshSelected();
    const [activityPage, projectPage] = await Promise.allSettled([
      listManagedActivities(0, 50),
      listPublicProjects(0, 100)
    ]);
    if (activityPage.status === 'fulfilled') {
      activities.value = activityPage.value.items;
    } else {
      activities.value = [];
      actionError.value = describeError(activityPage.reason);
    }
    if (projectPage.status === 'fulfilled') {
      projects.value = projectPage.value.items;
    } else {
      projects.value = [];
      actionError.value = describeError(projectPage.reason);
    }
  } catch (error) {
    definitions.value = [];
    selected.value = null;
    published.value = null;
    projects.value = [];
    pageError.value = describeError(error);
  } finally {
    loading.value = false;
  }
}

async function refreshSelected() {
  published.value = null;
  if (!selected.value) return;
  const detail = await getManagedRankingDefinition(selected.value.id);
  selected.value = detail;
  const index = definitions.value.findIndex((definition) => definition.id === detail.id);
  if (index >= 0) definitions.value[index] = detail;
  if (detail.currentPublishedVersion) {
    try {
      published.value = await getRanking('school-admin', detail.id);
    } catch {
      published.value = null;
    }
  }
}

async function selectDefinition(definition: RankingManagementDefinition) {
  actionError.value = '';
  actionMessage.value = '';
  selected.value = definition;
  try {
    await refreshSelected();
  } catch (error) {
    actionError.value = describeError(error);
  }
}

watch(() => form.value.activityId, async (activityId) => {
  if (form.value.layer !== 'L1') return;
  form.value.activityProjectId = '';
  selectedActivity.value = null;
  if (!activityId) return;
  loadingActivity.value = true;
  actionError.value = '';
  try {
    selectedActivity.value = await getManagedActivity(activityId);
    if (selectedActivity.value.projects.length === 1) {
      form.value.activityProjectId = selectedActivity.value.projects[0].id;
    }
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    loadingActivity.value = false;
  }
});

watch(() => form.value.layer, () => {
  actionError.value = '';
  actionMessage.value = '';
  resetCreateFields(form.value.layer, true);
});

async function createDefinition() {
  if (!canCreate.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const payload: RankingDefinitionCreateForm = form.value.layer === 'L1'
      ? {
          layer: 'L1',
          name: form.value.name.trim(),
          projectId: selectedActivityProject.value!.projectId,
          activityProjectId: selectedActivityProject.value!.id
        }
      : {
          layer: 'L2',
          name: form.value.name.trim(),
          projectId: selectedChallengeProject.value!.id,
          dimensionFilters: buildL2DimensionFilters()
        };
    const created = await createRankingDefinition(payload);
    resetCreateFields(form.value.layer, false);
    await loadDefinitions(created.id);
    actionMessage.value = `${payload.layer} RankingDefinition created.`;
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    saving.value = false;
  }
}

async function generate(definition: RankingManagementDefinition) {
  if (!definition.enabled || generatingId.value) return;
  generatingId.value = definition.id;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await generateRankingDefinition(definition.id);
    await loadDefinitions(definition.id);
    actionMessage.value = `Generated V${result.versionNumber} with ${result.entryCount} entries.`;
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    generatingId.value = '';
  }
}

async function toggleDefinition(definition: RankingManagementDefinition) {
  togglingId.value = definition.id;
  actionError.value = '';
  actionMessage.value = '';
  try {
    if (definition.enabled) {
      await disableRankingDefinition(definition.id);
      actionMessage.value = 'RankingDefinition disabled.';
    } else {
      await enableRankingDefinition(definition.id);
      actionMessage.value = 'RankingDefinition enabled.';
    }
    await loadDefinitions(definition.id);
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    togglingId.value = '';
  }
}

function askPublish(version: RankingManagementVersion) {
  publishCandidate.value = version;
  actionError.value = '';
}

async function confirmPublish() {
  if (!selected.value || !publishCandidate.value || publishingId.value) return;
  const definitionId = selected.value.id;
  const versionId = publishCandidate.value.id;
  publishingId.value = versionId;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await publishRankingVersion(definitionId, versionId);
    publishCandidate.value = null;
    await loadDefinitions(definitionId);
    actionMessage.value = `Published V${selected.value?.currentPublishedVersion?.versionNumber ?? result.status}.`;
  } catch (error) {
    actionError.value = describeError(error);
  } finally {
    publishingId.value = '';
  }
}

onMounted(() => void loadDefinitions());
</script>

<template>
  <WorkspaceShell
    role-label="School Admin"
    workspace-title="School Admin Workspace"
    page-title="Ranking Management"
    description="Create, generate, publish, and verify L1 and L2 rankings for the current school."
    home-path="/school-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="ranking-management-grid">
      <div class="ranking-management-panel">
        <header class="student-score-toolbar">
          <div>
            <p class="eyebrow">MANAGED DEFINITIONS</p>
            <h2>Managed Rankings</h2>
            <span>{{ definitions.length }} definitions</span>
          </div>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadDefinitions()">
            Refresh
          </button>
        </header>

        <div v-if="loading" class="project-state" role="status">Loading rankings...</div>
        <div v-else-if="pageError" class="project-state project-state-error" role="alert">
          <strong>{{ pageError }}</strong>
          <button class="secondary-button" type="button" @click="loadDefinitions()">Retry</button>
        </div>
        <div v-else-if="definitions.length === 0" class="project-state">
          <strong>No RankingDefinition yet</strong>
          <p>Create the first ranking definition for this school.</p>
        </div>
        <div v-else class="ranking-definition-list">
          <button
            v-for="definition in definitions"
            :key="definition.id"
            class="ranking-definition-row"
            :class="{ 'ranking-definition-row-active': selected?.id === definition.id }"
            type="button"
            @click="selectDefinition(definition)"
          >
            <span>
              <strong>{{ definition.name }}</strong>
              <small>{{ definition.layer }} · {{ describeDefinition(definition) }}</small>
            </span>
            <em :data-status="definition.enabled ? 'ENABLED' : 'DISABLED'">
              {{ definition.enabled ? 'Enabled' : 'Disabled' }}
            </em>
          </button>
        </div>
      </div>

      <div class="ranking-management-panel ranking-create-panel">
        <header class="student-score-toolbar">
          <div>
            <p class="eyebrow">CREATE DEFINITION</p>
            <h2>Create RankingDefinition</h2>
            <span>School scope is derived from the signed-in account.</span>
          </div>
        </header>
        <form class="ranking-create-form" @submit.prevent="createDefinition">
          <label>
            <span>Name</span>
            <input v-model.trim="form.name" maxlength="200" required placeholder="Ranking name" />
          </label>
          <label>
            <span>Layer</span>
            <select v-model="form.layer">
              <option value="L1">L1</option>
              <option value="L2">L2</option>
            </select>
          </label>
          <template v-if="form.layer === 'L1'">
            <label>
              <span>Activity</span>
              <select v-model="form.activityId" required>
                <option value="">Select activity</option>
                <option v-for="activity in activities" :key="activity.id" :value="activity.id">
                  {{ activity.title }}
                </option>
              </select>
            </label>
            <label>
              <span>Activity Project</span>
              <select v-model="form.activityProjectId" required :disabled="!selectedActivity || loadingActivity">
                <option value="">{{ loadingActivity ? 'Loading projects...' : 'Select activity project' }}</option>
                <option v-for="project in selectedActivity?.projects ?? []" :key="project.id" :value="project.id">
                  {{ project.projectName }} / Rule V{{ project.ruleVersionNumber }}
                </option>
              </select>
            </label>
          </template>
          <template v-else>
            <label class="project-form-wide">
              <span>Challenge Project</span>
              <select v-model="form.projectId" required>
                <option value="">Select challenge project</option>
                <option v-for="project in projects" :key="project.id" :value="project.id">
                  {{ project.name }} / {{ labelForCategory(project.category) }}
                </option>
              </select>
            </label>
            <label>
              <span>Grade Filter</span>
              <input v-model.trim="form.grade" maxlength="32" placeholder="Optional" />
            </label>
            <label>
              <span>Class Filter</span>
              <input v-model.trim="form.className" maxlength="64" placeholder="Optional" />
            </label>
            <label>
              <span>Activity Period Start</span>
              <input v-model="form.activityPeriodStart" type="datetime-local" />
            </label>
            <label>
              <span>Activity Period End</span>
              <input v-model="form.activityPeriodEnd" type="datetime-local" />
            </label>
            <p v-if="activityPeriodError" class="project-inline-error project-form-wide" role="alert">
              {{ activityPeriodError }}
            </p>
          </template>
          <button class="primary-button" type="submit" :disabled="!canCreate">
            {{ saving ? 'Creating...' : form.layer === 'L1' ? 'Create L1 Ranking' : 'Create L2 Ranking' }}
          </button>
        </form>
      </div>
    </section>

    <p v-if="actionMessage" class="project-inline-success" role="status">{{ actionMessage }}</p>
    <p v-if="actionError" class="project-inline-error" role="alert">{{ actionError }}</p>

    <section v-if="selected" class="ranking-management-panel ranking-management-detail">
      <header class="student-score-toolbar">
        <div>
          <p class="eyebrow">SELECTED DEFINITION</p>
          <h2>{{ selected.name }}</h2>
          <span>{{ selected.layer }} · {{ describeDefinition(selected) }}</span>
        </div>
        <div class="ranking-management-actions">
          <button
            class="secondary-button"
            type="button"
            :disabled="Boolean(generatingId) || !selected.enabled"
            @click="generate(selected)"
          >
            {{ generatingId === selected.id ? 'Generating...' : 'Generate' }}
          </button>
          <button class="secondary-button" type="button" :disabled="togglingId === selected.id" @click="toggleDefinition(selected)">
            {{ togglingId === selected.id ? 'Saving...' : selected.enabled ? 'Disable' : 'Enable' }}
          </button>
        </div>
      </header>

      <dl class="ranking-management-summary">
        <div>
          <dt>Status</dt>
          <dd>{{ selected.enabled ? 'Enabled' : 'Disabled' }}</dd>
        </div>
        <div>
          <dt>Current Published</dt>
          <dd>{{ selected.currentPublishedVersion ? `V${selected.currentPublishedVersion.versionNumber}` : 'None' }}</dd>
        </div>
        <div>
          <dt>Latest Generated</dt>
          <dd>{{ selected.latestGeneratedVersion ? `V${selected.latestGeneratedVersion.versionNumber}` : 'None' }}</dd>
        </div>
      </dl>

      <div v-if="!selected.enabled" class="ranking-management-notice" role="status">
        Disabled definitions cannot generate or publish rankings. Published read remains hidden while disabled.
      </div>

      <section v-if="selected.layer === 'L2'" class="ranking-management-section">
        <div class="project-section-heading">
          <div>
            <p class="eyebrow">L2 SCOPE</p>
            <h2>Dimension Filters</h2>
          </div>
        </div>
        <dl class="ranking-management-summary">
          <div>
            <dt>Selection Policy</dt>
            <dd>{{ selected.selectionPolicy || 'BEST_SCORE' }}</dd>
          </div>
          <div>
            <dt>Grade</dt>
            <dd>{{ selected.grade || 'Any' }}</dd>
          </div>
          <div>
            <dt>Class</dt>
            <dd>{{ selected.className || 'Any' }}</dd>
          </div>
          <div>
            <dt>Activity Period Start</dt>
            <dd>{{ formatDate(selected.activityPeriodStart) }}</dd>
          </div>
          <div>
            <dt>Activity Period End</dt>
            <dd>{{ formatDate(selected.activityPeriodEnd) }}</dd>
          </div>
        </dl>
      </section>

      <section class="ranking-management-section">
        <div class="project-section-heading">
          <div>
            <p class="eyebrow">GENERATED SNAPSHOT</p>
            <h2>Generated Preview</h2>
          </div>
          <button
            v-if="selected.latestGeneratedVersion"
            class="primary-button"
            type="button"
            :disabled="!selected.enabled || Boolean(publishingId)"
            @click="askPublish(selected.latestGeneratedVersion)"
          >
            Publish
          </button>
        </div>
        <div v-if="!selected.latestGeneratedVersion" class="project-state">
          <strong>No generated version</strong>
          <p>Generate a ranking to inspect the latest unpublished snapshot.</p>
        </div>
        <article v-else class="ranking-version-box">
          <div class="ranking-version-meta">
            <span>V{{ selected.latestGeneratedVersion.versionNumber }}</span>
            <span>{{ selected.latestGeneratedVersion.status }}</span>
            <span>{{ selected.latestGeneratedVersion.entryCount }} entries</span>
            <time>{{ formatDate(selected.latestGeneratedVersion.generatedAt) }}</time>
          </div>
          <RankingEntriesTable :entries="selected.latestGeneratedVersion.entries" />
        </article>
      </section>

      <section class="ranking-management-section">
        <div class="project-section-heading">
          <div>
            <p class="eyebrow">CURRENT PUBLISHED</p>
            <h2>Published Result</h2>
          </div>
        </div>
        <div v-if="!selected.currentPublishedVersion" class="project-state">
          <strong>No published ranking</strong>
          <p>Published results appear after a generated version is confirmed.</p>
        </div>
        <article v-else-if="published" class="ranking-version-box">
          <div class="ranking-version-meta">
            <span>V{{ published.versionNumber }}</span>
            <span>PUBLISHED</span>
            <span>{{ published.entries.length }} entries</span>
            <time>{{ formatDate(published.publishedAt) }}</time>
          </div>
          <RankingEntriesTable :entries="published.entries" />
        </article>
      </section>
    </section>

    <div v-if="publishCandidate && selected" class="project-modal-backdrop">
      <div class="project-modal ranking-publish-dialog" role="dialog" aria-modal="true" aria-labelledby="publish-title">
        <p class="eyebrow">PUBLISH RANKING</p>
        <h2 id="publish-title">Confirm publication</h2>
        <p>V{{ publishCandidate.versionNumber }} will become the current published ranking for {{ selected.name }}.</p>
        <div class="project-modal-actions">
          <button class="secondary-button" type="button" :disabled="Boolean(publishingId)" @click="publishCandidate = null">
            Cancel
          </button>
          <button class="primary-button" type="button" :disabled="Boolean(publishingId)" @click="confirmPublish">
            {{ publishingId ? 'Publishing...' : 'Publish' }}
          </button>
        </div>
      </div>
    </div>
  </WorkspaceShell>
</template>
