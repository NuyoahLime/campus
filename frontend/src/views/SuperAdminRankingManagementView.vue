<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import WorkspaceShell from '../components/WorkspaceShell.vue';
import RankingEntriesTable from '../components/RankingEntriesTable.vue';
import { ApiError } from '../api/http';
import {
  createL3RankingDefinition,
  disableL3RankingDefinition,
  enableL3RankingDefinition,
  generateL3RankingDefinition,
  getL3RankingDefinition,
  listL3RankingDefinitions,
  publishL3RankingVersion
} from '../api/l3RankingManagement';
import { getRanking } from '../api/ranking';
import { getGovernanceProject, listGovernanceProjects } from '../api/challengeProjects';
import { superAdminNavigation as navigation } from '../router/superAdminNavigation';
import type { GovernanceProjectDetail, GovernanceProjectListItem, RuleVersion } from '../types/challengeProject';
import type { RankingDetail } from '../types/ranking';
import type {
  L3RankingDefinitionCreateForm,
  L3RankingManagementDefinition,
  RankingManagementVersion
} from '../types/rankingManagement';

const definitions = ref<L3RankingManagementDefinition[]>([]);
const selected = ref<L3RankingManagementDefinition | null>(null);
const projects = ref<GovernanceProjectListItem[]>([]);
const projectDetails = ref<Record<string, GovernanceProjectDetail>>({});
const published = ref<RankingDetail | null>(null);
const loading = ref(true);
const loadingProjects = ref(true);
const loadingRuleVersions = ref(false);
const saving = ref(false);
const generatingId = ref('');
const publishingId = ref('');
const togglingId = ref('');
const pageError = ref('');
const projectError = ref('');
const actionMessage = ref('');
const actionError = ref('');
const publishCandidate = ref<RankingManagementVersion | null>(null);
const form = ref<L3RankingDefinitionCreateForm>({
  name: '',
  projectId: '',
  ruleVersionId: ''
});

const selectedProjectDetail = computed(() => form.value.projectId
  ? projectDetails.value[form.value.projectId] ?? null
  : null);

const ruleVersions = computed<RuleVersion[]>(() => selectedProjectDetail.value?.ruleVersions ?? []);

const selectedRuleVersion = computed(() =>
  ruleVersions.value.find((version) => version.id === form.value.ruleVersionId) ?? null
);

const canCreate = computed(() =>
  !saving.value
  && form.value.name.trim().length > 0
  && Boolean(form.value.projectId)
  && Boolean(selectedRuleVersion.value));

function describeError(value: unknown) {
  if (value instanceof ApiError) {
    if (value.status === 401) return 'Please sign in again.';
    if (value.status === 403) return 'This account cannot manage platform rankings.';
    if (value.status === 404) return 'The ranking resource was not found.';
    if (value.status === 409) return 'The ranking state changed. Refresh and try again.';
  }
  return 'The request failed. Try again shortly.';
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : 'Not recorded';
}

function describeVersion(version: RuleVersion) {
  return `V${version.versionNumber} / ${version.scoreIndicatorType} / ${version.comparisonDirection}`;
}

async function loadProjects() {
  loadingProjects.value = true;
  projectError.value = '';
  try {
    const result = await listGovernanceProjects(0, 100);
    projects.value = result.items;
  } catch (value) {
    projects.value = [];
    projectError.value = describeError(value);
  } finally {
    loadingProjects.value = false;
  }
}

async function loadProjectDetail(projectId: string) {
  if (!projectId || projectDetails.value[projectId]) return;
  loadingRuleVersions.value = true;
  actionError.value = '';
  try {
    projectDetails.value[projectId] = await getGovernanceProject(projectId);
  } catch (value) {
    actionError.value = describeError(value);
  } finally {
    loadingRuleVersions.value = false;
  }
}

watch(() => form.value.projectId, async (projectId) => {
  form.value.ruleVersionId = '';
  await loadProjectDetail(projectId);
});

async function refreshSelected() {
  published.value = null;
  if (!selected.value) return;
  const detail = await getL3RankingDefinition(selected.value.id);
  selected.value = detail;
  const index = definitions.value.findIndex((definition) => definition.id === detail.id);
  if (index >= 0) definitions.value[index] = detail;
  if (detail.currentPublishedVersion) {
    try {
      published.value = await getRanking('public', detail.id);
    } catch {
      published.value = null;
    }
  }
}

async function loadDefinitions(selectId = selected.value?.id ?? '') {
  loading.value = true;
  pageError.value = '';
  try {
    const result = await listL3RankingDefinitions(0, 50);
    definitions.value = result.items;
    const next = definitions.value.find((definition) => definition.id === selectId)
      ?? definitions.value[0]
      ?? null;
    selected.value = next;
    await refreshSelected();
  } catch (value) {
    definitions.value = [];
    selected.value = null;
    published.value = null;
    pageError.value = describeError(value);
  } finally {
    loading.value = false;
  }
}

async function selectDefinition(definition: L3RankingManagementDefinition) {
  actionError.value = '';
  actionMessage.value = '';
  selected.value = definition;
  try {
    await refreshSelected();
  } catch (value) {
    actionError.value = describeError(value);
  }
}

async function createDefinition() {
  if (!canCreate.value) return;
  saving.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const created = await createL3RankingDefinition({
      name: form.value.name.trim(),
      projectId: form.value.projectId,
      ruleVersionId: form.value.ruleVersionId
    });
    form.value.name = '';
    await loadDefinitions(created.id);
    actionMessage.value = 'L3 RankingDefinition created.';
  } catch (value) {
    actionError.value = describeError(value);
  } finally {
    saving.value = false;
  }
}

async function generate(definition: L3RankingManagementDefinition) {
  if (!definition.enabled || generatingId.value) return;
  generatingId.value = definition.id;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await generateL3RankingDefinition(definition.id);
    await loadDefinitions(definition.id);
    actionMessage.value = `Generated V${result.versionNumber} with ${result.entryCount} entries.`;
  } catch (value) {
    actionError.value = describeError(value);
  } finally {
    generatingId.value = '';
  }
}

async function toggleDefinition(definition: L3RankingManagementDefinition) {
  togglingId.value = definition.id;
  actionError.value = '';
  actionMessage.value = '';
  try {
    if (definition.enabled) {
      await disableL3RankingDefinition(definition.id);
      actionMessage.value = 'L3 RankingDefinition disabled.';
    } else {
      await enableL3RankingDefinition(definition.id);
      actionMessage.value = 'L3 RankingDefinition enabled.';
    }
    await loadDefinitions(definition.id);
  } catch (value) {
    actionError.value = describeError(value);
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
    await publishL3RankingVersion(definitionId, versionId);
    publishCandidate.value = null;
    await loadDefinitions(definitionId);
    actionMessage.value = `Published V${selected.value?.currentPublishedVersion?.versionNumber ?? '?'}.`;
  } catch (value) {
    actionError.value = describeError(value);
  } finally {
    publishingId.value = '';
  }
}

onMounted(() => {
  void Promise.all([loadDefinitions(), loadProjects()]);
});
</script>

<template>
  <WorkspaceShell
    role-label="Super Admin"
    workspace-title="Platform Workspace"
    page-title="L3 Ranking Management"
    description="Create, generate, publish, and operate platform-scoped L3 ranking snapshots."
    home-path="/super-admin"
    :navigation="navigation"
    :show-identity="false"
  >
    <section class="ranking-management-grid l3-ranking-management">
      <div class="ranking-management-panel">
        <header class="student-score-toolbar">
          <div>
            <p class="eyebrow">PLATFORM L3 DEFINITIONS</p>
            <h2>L3 Rankings</h2>
            <span>{{ definitions.length }} definitions</span>
          </div>
          <button class="secondary-button" type="button" :disabled="loading" @click="loadDefinitions()">
            Refresh
          </button>
        </header>

        <div v-if="loading" class="project-state" role="status">Loading L3 rankings...</div>
        <div v-else-if="pageError" class="project-state project-state-error" role="alert">
          <strong>{{ pageError }}</strong>
          <button class="secondary-button" type="button" @click="loadDefinitions()">Retry</button>
        </div>
        <div v-else-if="definitions.length === 0" class="project-state">
          <strong>No platform L3 RankingDefinition yet</strong>
          <p>Create the first platform ranking from a ChallengeProject RuleVersion.</p>
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
              <small>{{ definition.projectName }} / Rule V{{ definition.ruleVersionNumber }}</small>
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
            <p class="eyebrow">CREATE PLATFORM L3</p>
            <h2>Create L3 Ranking</h2>
            <span>Platform scope and layer are fixed by this endpoint.</span>
          </div>
        </header>
        <div v-if="projectError" class="project-inline-error" data-testid="l3-ranking-project-error" role="alert">
          {{ projectError }}
          <button class="secondary-button" type="button" @click="loadProjects()">Retry projects</button>
        </div>
        <form class="ranking-create-form" @submit.prevent="createDefinition">
          <label class="project-form-wide">
            <span>Name</span>
            <input v-model.trim="form.name" data-testid="l3-ranking-name" maxlength="200" required placeholder="Ranking name" />
          </label>
          <label class="project-form-wide">
            <span>Challenge Project</span>
            <select v-model="form.projectId" data-testid="l3-ranking-project" required :disabled="loadingProjects">
              <option value="">{{ loadingProjects ? 'Loading projects...' : 'Select challenge project' }}</option>
              <option v-for="project in projects" :key="project.id" :value="project.id">
                {{ project.name }} / {{ project.category }}
              </option>
            </select>
          </label>
          <label class="project-form-wide">
            <span>RuleVersion</span>
            <select
              v-model="form.ruleVersionId"
              data-testid="l3-ranking-rule-version"
              required
              :disabled="!form.projectId || loadingRuleVersions"
            >
              <option value="">{{ loadingRuleVersions ? 'Loading RuleVersions...' : 'Select RuleVersion' }}</option>
              <option v-for="version in ruleVersions" :key="version.id" :value="version.id">
                {{ describeVersion(version) }}
              </option>
            </select>
          </label>
          <p v-if="selectedRuleVersion" class="project-state l3-rule-version-summary">
            RuleVersion V{{ selectedRuleVersion.versionNumber }} is immutable after creation.
          </p>
          <button class="primary-button" data-testid="l3-ranking-create" type="submit" :disabled="!canCreate">
            {{ saving ? 'Creating...' : 'Create L3 Ranking' }}
          </button>
        </form>
      </div>
    </section>

    <p v-if="actionMessage" class="project-inline-success" data-testid="l3-ranking-action-message" role="status">
      {{ actionMessage }}
    </p>
    <p v-if="actionError" class="project-inline-error" data-testid="l3-ranking-action-error" role="alert">
      {{ actionError }}
    </p>

    <section v-if="selected" class="ranking-management-panel ranking-management-detail">
      <header class="student-score-toolbar">
        <div>
          <p class="eyebrow">SELECTED PLATFORM DEFINITION</p>
          <h2>{{ selected.name }}</h2>
          <span>{{ selected.projectName }} / Rule V{{ selected.ruleVersionNumber }}</span>
        </div>
        <div class="ranking-management-actions">
          <button
            class="secondary-button"
            data-testid="l3-ranking-generate"
            type="button"
            :disabled="Boolean(generatingId) || !selected.enabled"
            @click="generate(selected)"
          >
            {{ generatingId === selected.id ? 'Generating...' : 'Generate' }}
          </button>
          <button
            class="secondary-button"
            data-testid="l3-ranking-toggle"
            type="button"
            :disabled="togglingId === selected.id"
            @click="toggleDefinition(selected)"
          >
            {{ togglingId === selected.id ? 'Saving...' : selected.enabled ? 'Disable' : 'Enable' }}
          </button>
        </div>
      </header>

      <dl class="ranking-management-summary">
        <div><dt>Layer</dt><dd>L3</dd></div>
        <div><dt>Scope</dt><dd>Platform</dd></div>
        <div><dt>ChallengeProject</dt><dd>{{ selected.projectName }}</dd></div>
        <div><dt>RuleVersion</dt><dd>V{{ selected.ruleVersionNumber }}</dd></div>
        <div><dt>Status</dt><dd>{{ selected.enabled ? 'Enabled' : 'Disabled' }}</dd></div>
        <div><dt>Current Published</dt><dd>{{ selected.currentPublishedVersion ? `V${selected.currentPublishedVersion.versionNumber}` : 'None' }}</dd></div>
        <div><dt>Latest Generated</dt><dd>{{ selected.latestGeneratedVersion ? `V${selected.latestGeneratedVersion.versionNumber}` : 'None' }}</dd></div>
      </dl>

      <div v-if="!selected.enabled" class="ranking-management-notice" role="status">
        Disabled definitions cannot generate or publish. Public reads remain hidden while disabled.
      </div>

      <section class="ranking-management-section">
        <div class="project-section-heading">
          <div>
            <p class="eyebrow">GENERATED IMMUTABLE SNAPSHOT</p>
            <h2>Generated Preview</h2>
          </div>
          <button
            v-if="selected.latestGeneratedVersion"
            class="primary-button"
            data-testid="l3-ranking-publish"
            type="button"
            :disabled="!selected.enabled || Boolean(publishingId)"
            @click="askPublish(selected.latestGeneratedVersion)"
          >
            Publish
          </button>
        </div>
        <div v-if="!selected.latestGeneratedVersion" class="project-state">
          <strong>No generated version</strong>
          <p>Generate a snapshot to inspect entries before publication.</p>
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
            <h2>Public Result</h2>
          </div>
        </div>
        <div v-if="!selected.currentPublishedVersion" class="project-state">
          <strong>No published ranking</strong>
          <p>Public detail remains unavailable until a snapshot is published.</p>
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
        <div v-else class="project-state project-state-error">
          <strong>Published detail is not currently visible.</strong>
          <p>The authoritative public read returned no visible result.</p>
        </div>
      </section>
    </section>

    <div v-if="publishCandidate && selected" class="project-modal-backdrop">
      <div class="project-modal ranking-publish-dialog" role="dialog" aria-modal="true" aria-labelledby="l3-publish-title">
        <p class="eyebrow">PUBLISH L3 RANKING</p>
        <h2 id="l3-publish-title">Confirm publication</h2>
        <p>V{{ publishCandidate.versionNumber }} will become the current published platform ranking.</p>
        <div class="project-modal-actions">
          <button class="secondary-button" type="button" :disabled="Boolean(publishingId)" @click="publishCandidate = null">
            Cancel
          </button>
          <button class="primary-button" data-testid="l3-ranking-publish-confirm" type="button" :disabled="Boolean(publishingId)" @click="confirmPublish">
            {{ publishingId ? 'Publishing...' : 'Publish' }}
          </button>
        </div>
      </div>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.l3-ranking-management { align-items: start; }
.l3-rule-version-summary { margin: 0; }
.ranking-management-detail { margin-top: 18px; }
</style>
