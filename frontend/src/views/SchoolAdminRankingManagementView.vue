<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import RankingEntriesTable from '../components/RankingEntriesTable.vue';
import { ApiError } from '../api/http';
import { getManagedActivity, listManagedActivities } from '../api/activityManagement';
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
import type { RankingDetail } from '../types/ranking';
import type { RankingManagementDefinition, RankingManagementVersion } from '../types/rankingManagement';

const definitions = ref<RankingManagementDefinition[]>([]);
const selected = ref<RankingManagementDefinition | null>(null);
const published = ref<RankingDetail | null>(null);
const activities = ref<ActivityManagementListItem[]>([]);
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
const form = ref({ name: '', activityId: '', activityProjectId: '' });

const selectedActivityProject = computed(() =>
  selectedActivity.value?.projects.find((project) => project.id === form.value.activityProjectId) ?? null
);

const canCreate = computed(() =>
  form.value.name.trim().length > 0 && Boolean(selectedActivityProject.value) && !saving.value
);

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : 'Not recorded';
}

function describeError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Please sign in again.';
    if (error.status === 403) return 'This account cannot manage rankings.';
    if (error.status === 404) return 'The ranking resource was not found.';
    if (error.status === 409) return 'The ranking state changed. Refresh and try again.';
    if (error.status === 400) return 'Check the required fields and try again.';
  }
  return 'The request failed. Try again shortly.';
}

async function loadDefinitions(selectId = selected.value?.id ?? '') {
  loading.value = true;
  pageError.value = '';
  try {
    const [definitionPage, activityPage] = await Promise.all([
      listManagedRankingDefinitions(0, 50),
      listManagedActivities(0, 50)
    ]);
    definitions.value = definitionPage.items;
    activities.value = activityPage.items;
    const next = definitions.value.find((definition) => definition.id === selectId) ?? definitions.value[0] ?? null;
    selected.value = next;
    await refreshSelected();
  } catch (error) {
    definitions.value = [];
    selected.value = null;
    published.value = null;
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

async function createDefinition() {
  const project = selectedActivityProject.value;
  if (!project || !canCreate.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const created = await createRankingDefinition({
      name: form.value.name.trim(),
      activityProjectId: project.id,
      projectId: project.projectId
    });
    form.value = { name: '', activityId: '', activityProjectId: '' };
    selectedActivity.value = null;
    await loadDefinitions(created.id);
    actionMessage.value = 'RankingDefinition created.';
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
    description="Create, generate, publish, and verify L1 rankings for the current school."
    home-path="/school-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="ranking-management-grid">
      <div class="ranking-management-panel">
        <header class="student-score-toolbar">
          <div>
            <p class="eyebrow">L1 DEFINITIONS</p>
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
          <p>Create the first L1 ranking from an activity project.</p>
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
              <small>{{ definition.activityTitle || 'Activity unavailable' }} / {{ definition.projectName }}</small>
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
            <p class="eyebrow">CREATE L1</p>
            <h2>Create RankingDefinition</h2>
            <span>School scope is derived from the signed-in account.</span>
          </div>
        </header>
        <form class="ranking-create-form" @submit.prevent="createDefinition">
          <label>
            <span>Name</span>
            <input v-model.trim="form.name" maxlength="200" required placeholder="L1 ranking name" />
          </label>
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
          <button class="primary-button" type="submit" :disabled="!canCreate">
            {{ saving ? 'Creating...' : 'Create L1 Ranking' }}
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
          <span>{{ selected.activityTitle || 'Activity unavailable' }} / {{ selected.projectName }}</span>
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
