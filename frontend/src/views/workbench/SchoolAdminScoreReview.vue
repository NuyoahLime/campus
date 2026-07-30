<template>
  <div class="score-review-page">
    <h2>成绩管理</h2>

    <el-button class="open-entry-dialog" type="primary" @click="openCreateEntry">
      代录成绩
    </el-button>

    <el-tabs v-model="activeTab" class="score-tabs" @tab-change="changeTab">
      <el-tab-pane label="待审核" name="review">
    <el-card class="filters">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="filter.status" class="status-filter" style="width: 160px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="活动ID">
          <el-input v-model="filter.activityId" class="activity-filter" clearable />
        </el-form-item>
        <el-form-item label="项目ID">
          <el-input v-model="filter.projectId" class="project-filter" clearable />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            class="keyword-filter"
            maxlength="100"
            clearable
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button class="search-button" type="primary" @click="search">查询</el-button>
          <el-button class="reset-button" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-skeleton v-if="loading" :rows="6" animated />
    <el-result v-else-if="listError" icon="error" title="成绩加载失败" :sub-title="listError">
      <template #extra>
        <el-button class="list-retry" type="primary" @click="loadList">重试</el-button>
      </template>
    </el-result>
    <el-empty v-else-if="items.length === 0" description="暂无成绩" />
    <template v-else>
      <el-table class="score-table" :data="items" row-key="attemptId">
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column prop="activityTitle" label="活动" min-width="150" />
        <el-table-column prop="projectName" label="项目" min-width="140" />
        <el-table-column prop="attemptNumber" label="尝试次数" width="90" />
        <el-table-column label="成绩" min-width="110">
          <template #default="{ row }">
            <span class="score-value">{{ row.displayValue }}{{ row.scoreUnit || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="enteredByName" label="录入人" min-width="120" />
        <el-table-column label="业务发生时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.scoreBusinessTime) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="当前有效" width="100">
          <template #default="{ row }">{{ row.currentEffective ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="250">
          <template #default="{ row }">
            <el-button class="view-button" link type="primary" @click="openDetail(row.attemptId)">
              查看
            </el-button>
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button
                class="approve-button"
                link
                type="success"
                :disabled="isOwnEntry(row)"
                @click="openApprove(row)"
              >
                审核通过
              </el-button>
              <el-button
                class="reject-button"
                link
                type="danger"
                :disabled="isOwnEntry(row)"
                @click="openReject(row)"
              >
                审核驳回
              </el-button>
              <span v-if="isOwnEntry(row)" class="self-review-warning">
                不能审核本人录入的成绩
              </span>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        @current-change="changePage"
      />
    </template>

      </el-tab-pane>

      <el-tab-pane label="我的录入" name="mine">
        <el-card class="my-entry-filters">
          <el-form :inline="true">
            <el-form-item label="状态">
              <el-select
                v-model="myFilter.status"
                class="my-status-filter"
                clearable
                placeholder="全部状态"
                style="width: 160px"
              >
                <el-option
                  v-for="option in statusOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input
                v-model="myFilter.keyword"
                class="my-keyword-filter"
                maxlength="100"
                clearable
                @keyup.enter="searchMine"
              />
            </el-form-item>
            <el-form-item>
              <el-button class="my-search-button" type="primary" @click="searchMine">
                查询
              </el-button>
              <el-button class="my-reset-button" @click="resetMine">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-skeleton v-if="myLoading" :rows="6" animated />
        <el-result
          v-else-if="myError"
          icon="error"
          title="我的录入加载失败"
          :sub-title="myError"
        >
          <template #extra>
            <el-button class="my-entry-retry" type="primary" @click="loadMine">
              重试
            </el-button>
          </template>
        </el-result>
        <el-empty v-else-if="myItems.length === 0" description="暂无本人录入成绩" />
        <template v-else>
          <el-table class="my-entry-table" :data="myItems" row-key="attemptId">
            <el-table-column prop="studentName" label="学生" min-width="110" />
            <el-table-column prop="activityTitle" label="活动" min-width="140" />
            <el-table-column prop="projectName" label="项目" min-width="130" />
            <el-table-column prop="attemptNumber" label="尝试次数" width="90" />
            <el-table-column label="成绩" min-width="100">
              <template #default="{ row }">
                <span class="my-score-value">{{ row.displayValue }}{{ row.scoreUnit || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="业务发生时间" min-width="170">
              <template #default="{ row }">{{ formatTime(row.scoreBusinessTime) }}</template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="170">
              <template #default="{ row }">{{ formatTime(row.submittedAt) }}</template>
            </el-table-column>
            <el-table-column label="当前有效" width="90">
              <template #default="{ row }">{{ row.currentEffective ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" min-width="220">
              <template #default="{ row }">
                <el-button
                  class="my-view-button"
                  link
                  type="primary"
                  @click="openDetail(row.attemptId)"
                >
                  查看
                </el-button>
                <el-button
                  v-if="row.status === 'DRAFT'"
                  class="edit-entry-button"
                  link
                  type="primary"
                  @click="openEditEntry(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-if="row.status === 'REJECTED'"
                  class="revise-entry-button"
                  link
                  type="warning"
                  @click="openEditEntry(row)"
                >
                  修改
                </el-button>
                <el-button
                  v-if="row.status === 'DRAFT'"
                  class="submit-entry-button"
                  link
                  type="success"
                  :disabled="submittingAttemptIds.has(row.attemptId)"
                  @click="openSubmitConfirmation(row)"
                >
                  提交审核
                </el-button>
                <span v-if="row.status === 'PENDING_REVIEW'" class="pending-entry-hint">
                  等待其他管理员审核
                </span>
                <span
                  v-if="row.status === 'APPROVED' || row.status === 'INVALIDATED'"
                  class="readonly-entry-hint"
                >
                  只读
                </span>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="myCurrentPage"
            class="my-pagination"
            layout="total, prev, pager, next"
            :total="myTotal"
            :page-size="pageSize"
            @current-change="changeMyPage"
          />
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="detailVisible"
      class="score-detail-dialog"
      title="成绩详情"
      width="720px"
      destroy-on-close
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <el-result
        v-else-if="detailError"
        icon="error"
        title="详情加载失败"
        :sub-title="detailError"
      >
        <template #extra>
          <el-button class="detail-retry" type="primary" @click="reloadDetail">重试</el-button>
        </template>
      </el-result>
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生">{{ detail.studentName }}</el-descriptions-item>
          <el-descriptions-item label="活动">{{ detail.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="尝试编号">{{ detail.attemptNumber }}</el-descriptions-item>
          <el-descriptions-item label="分数值">
            {{ detail.displayValue }}{{ detail.scoreUnit || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="分数类型">{{ detail.scoreStorageType }}</el-descriptions-item>
          <el-descriptions-item label="分数单位">{{ displayEmpty(detail.scoreUnit) }}</el-descriptions-item>
          <el-descriptions-item label="业务发生时间">
            {{ formatTime(detail.scoreBusinessTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="时间来源">
            {{ displayEmpty(detail.timeSource) }}
          </el-descriptions-item>
          <el-descriptions-item label="录入人">{{ detail.enteredByName }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatTime(detail.submittedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="当前有效状态">
            {{ detail.currentEffective ? '是' : '否' }}
          </el-descriptions-item>
          <el-descriptions-item label="有效成绩规则">
            {{ detail.effectiveScoreRule }}
          </el-descriptions-item>
          <el-descriptions-item label="比较方向">
            {{ detail.comparisonDirection }}
          </el-descriptions-item>
          <el-descriptions-item label="原始整数">{{ displayEmpty(detail.integerValue) }}</el-descriptions-item>
          <el-descriptions-item label="原始小数">{{ displayEmpty(detail.decimalValue) }}</el-descriptions-item>
          <el-descriptions-item label="原始时长">{{ displayEmpty(detail.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="原始等级">{{ displayEmpty(detail.grade) }}</el-descriptions-item>
        </el-descriptions>

        <h3>审核历史</h3>
        <el-empty v-if="detail.reviewHistory.length === 0" description="暂无审核历史" />
        <el-timeline v-else class="review-history">
          <el-timeline-item
            v-for="history in detail.reviewHistory"
            :key="history.reviewRecordId"
            :timestamp="formatTime(history.reviewedAt)"
          >
            <strong>{{ history.reviewResult === 'APPROVED' ? '审核通过' : '审核驳回' }}</strong>
            · {{ history.reviewerName }}
            <div v-if="history.rejectReason">原因：{{ history.rejectReason }}</div>
            <div v-if="history.reviewComment">备注：{{ history.reviewComment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>

    <el-dialog
      v-model="approveVisible"
      class="approve-dialog"
      title="审核通过"
      width="520px"
      :close-on-click-modal="!approving"
    >
      <el-form label-position="top">
        <el-form-item label="审核备注">
          <el-input
            v-model="approveComment"
            class="approve-comment"
            type="textarea"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item
          v-if="selectedAttempt?.effectiveScoreRule === 'ADMIN_DESIGNATED'"
          label="设为当前有效成绩"
          required
          :error="designationError"
        >
          <el-radio-group v-model="designationChoice" class="designation-choice">
            <el-radio :value="true">是</el-radio>
            <el-radio :value="false">否</el-radio>
          </el-radio-group>
          <div v-if="designationError" class="field-error designation-error">
            {{ designationError }}
          </div>
        </el-form-item>
        <div v-if="approveError" class="submit-error">{{ approveError }}</div>
      </el-form>
      <template #footer>
        <el-button :disabled="approving" @click="approveVisible = false">取消</el-button>
        <el-button
          class="confirm-approve"
          type="primary"
          :loading="approving"
          :disabled="approving"
          @click="submitApprove"
        >
          确认通过
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="rejectVisible"
      class="reject-dialog"
      title="审核驳回"
      width="520px"
      :close-on-click-modal="!rejecting"
    >
      <el-form label-position="top">
        <el-form-item label="驳回原因" required :error="rejectReasonError">
          <el-input
            v-model="rejectReason"
            class="reject-reason"
            type="textarea"
            maxlength="1000"
            show-word-limit
          />
          <div v-if="rejectReasonError" class="field-error reject-reason-error">
            {{ rejectReasonError }}
          </div>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="rejectComment"
            class="reject-comment"
            type="textarea"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <div v-if="rejectError" class="submit-error">{{ rejectError }}</div>
      </el-form>
      <template #footer>
        <el-button :disabled="rejecting" @click="rejectVisible = false">取消</el-button>
        <el-button
          class="confirm-reject"
          type="danger"
          :loading="rejecting"
          :disabled="rejecting"
          @click="submitReject"
        >
          确认驳回
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="entryVisible"
      class="score-entry-dialog"
      :title="entryMode === 'create' ? '代录成绩' : '修改录入成绩'"
      width="680px"
      :close-on-click-modal="!savingDraft && !submittingDraft"
      destroy-on-close
    >
      <el-form label-position="top">
        <el-form-item label="活动项目" required>
          <el-select
            v-model="entryForm.activityProjectId"
            class="entry-project-select"
            filterable
            :disabled="entryMode === 'edit'"
            :loading="projectsLoading"
            style="width: 100%"
            @change="changeEntryProject"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.activityProjectId"
              :label="`${project.activityTitle} / ${project.projectName}`"
              :value="project.activityProjectId"
            />
          </el-select>
          <div v-if="projectOptionsError" class="field-error project-options-error">
            {{ projectOptionsError }}
            <el-button class="project-options-retry" link type="primary" @click="loadProjects">
              重试
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="参赛学生" required>
          <el-select
            v-model="entryForm.studentId"
            class="entry-participant-select"
            filterable
            :disabled="entryMode === 'edit' || !entryForm.activityProjectId"
            :loading="participantsLoading"
            style="width: 100%"
          >
            <el-option
              v-for="participant in participantOptions"
              :key="participant.studentId"
              :label="participantLabel(participant)"
              :value="participant.studentId"
            />
          </el-select>
          <div v-if="participantOptionsError" class="field-error participant-options-error">
            {{ participantOptionsError }}
            <el-button
              class="participant-options-retry"
              link
              type="primary"
              @click="loadParticipants"
            >
              重试
            </el-button>
          </div>
        </el-form-item>

        <el-form-item
          v-if="selectedEntryProject?.scoreStorageType === 'INTEGER'"
          label="整数成绩"
          required
        >
          <el-input-number
            v-model="entryForm.integerValue"
            class="integer-score-input"
            :min="0"
            :step="1"
            step-strictly
          />
        </el-form-item>
        <el-form-item
          v-if="selectedEntryProject?.scoreStorageType === 'DECIMAL'"
          label="小数成绩"
          required
        >
          <el-input-number
            v-model="entryForm.decimalValue"
            class="decimal-score-input"
            :precision="selectedEntryProject.decimalPlaces ?? undefined"
            :step="decimalStep"
          />
        </el-form-item>
        <el-form-item
          v-if="selectedEntryProject?.scoreStorageType === 'DURATION'"
          label="时长（毫秒）"
          required
        >
          <el-input-number
            v-model="entryForm.durationMs"
            class="duration-score-input"
            :min="0"
            :step="1"
            step-strictly
          />
        </el-form-item>
        <el-form-item
          v-if="selectedEntryProject?.scoreStorageType === 'GRADE'"
          label="等级成绩"
          required
        >
          <el-select v-model="entryForm.grade" class="grade-score-select" style="width: 100%">
            <el-option
              v-for="grade in gradeOptions"
              :key="grade"
              :label="grade"
              :value="grade"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          label="成绩业务发生时间"
          required
          :error="entryValidation.businessTime"
        >
          <el-date-picker
            v-model="entryForm.scoreBusinessTime"
            class="entry-business-time"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width: 100%"
          />
          <div v-if="entryValidation.businessTime" class="field-error business-time-error">
            {{ entryValidation.businessTime }}
          </div>
        </el-form-item>
        <el-form-item label="时间来源" required :error="entryValidation.timeSource">
          <el-select
            v-model="entryForm.timeSource"
            class="entry-time-source"
            style="width: 100%"
          >
            <el-option
              v-for="source in timeSourceOptions"
              :key="source.value"
              :label="source.label"
              :value="source.value"
            />
          </el-select>
          <div v-if="entryValidation.timeSource" class="field-error time-source-error">
            {{ entryValidation.timeSource }}
          </div>
        </el-form-item>

        <el-alert
          v-if="latestRejection"
          class="entry-rejection-history"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>驳回原因：{{ latestRejection.rejectReason || '-' }}</template>
          <div>审核备注：{{ latestRejection.reviewComment || '-' }}</div>
          <div>审核人：{{ latestRejection.reviewerName }}</div>
          <div>审核时间：{{ formatTime(latestRejection.reviewedAt) }}</div>
        </el-alert>
        <div v-if="entryError" class="submit-error entry-submit-error">{{ entryError }}</div>
      </el-form>
      <template #footer>
        <el-button :disabled="savingDraft || submittingDraft" @click="entryVisible = false">
          取消
        </el-button>
        <el-button
          class="save-entry-draft"
          type="primary"
          :loading="savingDraft"
          :disabled="savingDraft || submittingDraft"
          @click="saveEntryDraft"
        >
          保存草稿
        </el-button>
        <el-button
          v-if="entryMode === 'create'"
          class="save-submit-entry"
          type="success"
          :loading="submittingDraft"
          :disabled="savingDraft || submittingDraft"
          @click="saveAndSubmitEntry"
        >
          保存并提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="submitConfirmVisible"
      class="submit-entry-confirm-dialog"
      title="确认提交审核"
      width="420px"
      :close-on-click-modal="false"
    >
      <p>提交后将不能继续编辑，并由同校其他管理员审核。确认提交吗？</p>
      <template #footer>
        <el-button @click="submitConfirmVisible = false">取消</el-button>
        <el-button
          class="confirm-submit-entry"
          type="primary"
          :loading="confirmSubmitting"
          :disabled="confirmSubmitting"
          @click="confirmSubmitEntry"
        >
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  approveSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempts,
  rejectSchoolAdminScoreAttempt,
} from '@/api/school-admin-score-review';
import {
  createSchoolAdminScoreDraft,
  fetchMySchoolAdminScoreEntries,
  fetchScoreEntryParticipants,
  fetchScoreEntryProjects,
  submitSchoolAdminScoreDraft,
  updateSchoolAdminScoreDraft,
} from '@/api/school-admin-score-entry';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type {
  ApproveScorePayload,
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
  ScoreAttemptStatus,
} from '@/types/school-admin-score-review';
import type {
  CreateScoreDraftPayload,
  ScoreDraftValuePayload,
  ScoreEntryParticipantOption,
  ScoreEntryProjectOption,
} from '@/types/school-admin-score-entry';

const auth = useAuthStore();
const pageSize = 20;
const items = ref<SchoolAdminScoreAttemptItem[]>([]);
const total = ref(0);
const currentPage = ref(1);
const loading = ref(false);
const listError = ref<string | null>(null);
const filter = reactive({
  status: 'PENDING_REVIEW' as ScoreAttemptStatus,
  activityId: '',
  projectId: '',
  keyword: '',
});

const activeTab = ref<'review' | 'mine'>('review');
const myItems = ref<SchoolAdminScoreAttemptItem[]>([]);
const myTotal = ref(0);
const myCurrentPage = ref(1);
const myLoading = ref(false);
const myError = ref<string | null>(null);
const myLoaded = ref(false);
const myFilter = reactive<{
  status: ScoreAttemptStatus | '';
  keyword: string;
}>({
  status: '',
  keyword: '',
});

const entryVisible = ref(false);
const entryMode = ref<'create' | 'edit'>('create');
const editingAttemptId = ref<string | null>(null);
const editingDetail = ref<SchoolAdminScoreAttemptDetail | null>(null);
const projectOptions = ref<ScoreEntryProjectOption[]>([]);
const participantOptions = ref<ScoreEntryParticipantOption[]>([]);
const projectsLoading = ref(false);
const participantsLoading = ref(false);
const projectOptionsError = ref<string | null>(null);
const participantOptionsError = ref<string | null>(null);
const savingDraft = ref(false);
const submittingDraft = ref(false);
const entryError = ref<string | null>(null);
const retainedDraftId = ref<string | null>(null);
const entryValidation = reactive({
  businessTime: '',
  timeSource: '',
});
const entryForm = reactive<{
  activityProjectId: string;
  studentId: string;
  integerValue: number | null;
  decimalValue: number | null;
  durationMs: number | null;
  grade: string;
  scoreBusinessTime: string;
  timeSource: string;
}>({
  activityProjectId: '',
  studentId: '',
  integerValue: null,
  decimalValue: null,
  durationMs: null,
  grade: '',
  scoreBusinessTime: '',
  timeSource: '',
});
const submittingAttemptIds = reactive(new Set<string>());
const submitConfirmVisible = ref(false);
const submitTarget = ref<SchoolAdminScoreAttemptItem | null>(null);
const confirmSubmitting = ref(false);

const selectedEntryProject = computed(() =>
  projectOptions.value.find(
    project => project.activityProjectId === entryForm.activityProjectId,
  ) ?? null,
);
const gradeOptions = computed(() =>
  (selectedEntryProject.value?.gradeOrder ?? '')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean),
);
const decimalStep = computed(() => {
  const places = selectedEntryProject.value?.decimalPlaces;
  return places === null || places === undefined ? 0.01 : 10 ** -places;
});
const latestRejection = computed(() =>
  editingDetail.value?.reviewHistory.find(
    history => history.reviewResult === 'REJECTED',
  ) ?? null,
);

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref<string | null>(null);
const detailAttemptId = ref<string | null>(null);
const detail = ref<SchoolAdminScoreAttemptDetail | null>(null);

const selectedAttempt = ref<SchoolAdminScoreAttemptItem | null>(null);
const approveVisible = ref(false);
const approveComment = ref('');
const designationChoice = ref<boolean | null>(null);
const designationError = ref('');
const approveError = ref<string | null>(null);
const approving = ref(false);

const rejectVisible = ref(false);
const rejectReason = ref('');
const rejectComment = ref('');
const rejectReasonError = ref('');
const rejectError = ref<string | null>(null);
const rejecting = ref(false);

const statusOptions: { value: ScoreAttemptStatus; label: string }[] = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING_REVIEW', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'INVALIDATED', label: '已作废' },
];
const timeSourceOptions = [
  { value: 'STUDENT_REPORTED', label: '学生自述' },
  { value: 'TEACHER_CONFIRMED', label: '老师确认' },
  { value: 'ON_SITE_RECORD', label: '现场记录' },
  { value: 'OTHER', label: '其他' },
];

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}

async function loadList(): Promise<void> {
  loading.value = true;
  listError.value = null;
  try {
    const result = await fetchSchoolAdminScoreAttempts(
      {
        status: filter.status,
        activityId: filter.activityId.trim() || undefined,
        projectId: filter.projectId.trim() || undefined,
        keyword: filter.keyword.trim() || undefined,
      },
      currentPage.value - 1,
      pageSize,
    );
    items.value = result.items;
    total.value = result.totalElements;
  } catch (error) {
    listError.value = errorMessage(error, '成绩加载失败');
  } finally {
    loading.value = false;
  }
}

async function search(): Promise<void> {
  currentPage.value = 1;
  await loadList();
}

async function reset(): Promise<void> {
  filter.status = 'PENDING_REVIEW';
  filter.activityId = '';
  filter.projectId = '';
  filter.keyword = '';
  currentPage.value = 1;
  await loadList();
}

async function changePage(page: number): Promise<void> {
  currentPage.value = page;
  await loadList();
}

async function changeTab(tab: string | number): Promise<void> {
  if (tab !== 'mine') return;
  if (!myLoaded.value) {
    await loadMine();
  }
}

async function loadMine(): Promise<void> {
  myLoading.value = true;
  myError.value = null;
  try {
    const result = await fetchMySchoolAdminScoreEntries(
      {
        status: myFilter.status || undefined,
        keyword: myFilter.keyword.trim() || undefined,
      },
      myCurrentPage.value - 1,
      pageSize,
    );
    myItems.value = result.items;
    myTotal.value = result.totalElements;
    myLoaded.value = true;
  } catch (error) {
    myError.value = errorMessage(error, '我的录入加载失败');
  } finally {
    myLoading.value = false;
  }
}

async function searchMine(): Promise<void> {
  myCurrentPage.value = 1;
  await loadMine();
}

async function resetMine(): Promise<void> {
  myFilter.status = '';
  myFilter.keyword = '';
  myCurrentPage.value = 1;
  await loadMine();
}

async function changeMyPage(page: number): Promise<void> {
  myCurrentPage.value = page;
  await loadMine();
}

function resetScoreInputs(): void {
  entryForm.integerValue = null;
  entryForm.decimalValue = null;
  entryForm.durationMs = null;
  entryForm.grade = '';
}

function resetEntryForm(): void {
  entryMode.value = 'create';
  editingAttemptId.value = null;
  editingDetail.value = null;
  retainedDraftId.value = null;
  entryForm.activityProjectId = '';
  entryForm.studentId = '';
  entryForm.scoreBusinessTime = '';
  entryForm.timeSource = '';
  resetScoreInputs();
  participantOptions.value = [];
  projectOptionsError.value = null;
  participantOptionsError.value = null;
  entryValidation.businessTime = '';
  entryValidation.timeSource = '';
  entryError.value = null;
}

async function loadProjects(): Promise<void> {
  projectsLoading.value = true;
  projectOptionsError.value = null;
  try {
    const result = await fetchScoreEntryProjects('', 0, 100);
    projectOptions.value = result.items;
  } catch (error) {
    projectOptionsError.value = errorMessage(error, '项目候选加载失败');
  } finally {
    projectsLoading.value = false;
  }
}

async function loadParticipants(): Promise<void> {
  if (!entryForm.activityProjectId) return;
  participantsLoading.value = true;
  participantOptionsError.value = null;
  try {
    const result = await fetchScoreEntryParticipants(
      entryForm.activityProjectId,
      '',
      0,
      100,
    );
    participantOptions.value = result.items;
  } catch (error) {
    participantOptionsError.value = errorMessage(error, '参赛学生候选加载失败');
  } finally {
    participantsLoading.value = false;
  }
}

async function openCreateEntry(): Promise<void> {
  resetEntryForm();
  entryVisible.value = true;
  await loadProjects();
}

async function changeEntryProject(value: string): Promise<void> {
  entryForm.activityProjectId = value;
  entryForm.studentId = '';
  participantOptions.value = [];
  participantOptionsError.value = null;
  resetScoreInputs();
  if (value) {
    await loadParticipants();
  }
}

function participantLabel(participant: ScoreEntryParticipantOption): string {
  const number = participant.studentNumber ? ` / ${participant.studentNumber}` : '';
  return `${participant.displayName}${number}`;
}

async function openEditEntry(attempt: SchoolAdminScoreAttemptItem): Promise<void> {
  if (attempt.status !== 'DRAFT' && attempt.status !== 'REJECTED') return;
  resetEntryForm();
  entryMode.value = 'edit';
  editingAttemptId.value = attempt.attemptId;
  entryVisible.value = true;
  entryError.value = null;
  try {
    await loadProjects();
    const loadedDetail = await fetchSchoolAdminScoreAttempt(attempt.attemptId);
    editingDetail.value = loadedDetail;
    if (!projectOptions.value.some(
      project => project.activityProjectId === loadedDetail.activityProjectId,
    )) {
      projectOptions.value.push({
        activityProjectId: loadedDetail.activityProjectId,
        activityId: loadedDetail.activityId,
        activityTitle: loadedDetail.activityTitle,
        executionStatus: '',
        projectId: loadedDetail.projectId,
        projectName: loadedDetail.projectName,
        scoreStorageType: loadedDetail.scoreStorageType,
        scoreUnit: loadedDetail.scoreUnit,
        decimalPlaces: loadedDetail.decimalPlaces,
        gradeOrder: loadedDetail.gradeOrder,
        comparisonDirection: loadedDetail.comparisonDirection,
        effectiveScoreRule: loadedDetail.effectiveScoreRule,
      });
    }
    entryForm.activityProjectId = loadedDetail.activityProjectId;
    entryForm.studentId = loadedDetail.studentId;
    entryForm.integerValue = loadedDetail.integerValue;
    entryForm.decimalValue = loadedDetail.decimalValue;
    entryForm.durationMs = loadedDetail.durationMs;
    entryForm.grade = loadedDetail.grade ?? '';
    entryForm.scoreBusinessTime = loadedDetail.scoreBusinessTime ?? '';
    entryForm.timeSource = loadedDetail.timeSource ?? '';
    await loadParticipants();
    if (!participantOptions.value.some(
      participant => participant.studentId === loadedDetail.studentId,
    )) {
      participantOptions.value.push({
        studentId: loadedDetail.studentId,
        displayName: loadedDetail.studentName,
        studentNumber: null,
        grade: null,
        className: null,
        attemptCount: loadedDetail.attemptNumber,
        latestAttemptNumber: loadedDetail.attemptNumber,
        latestAttemptStatus: loadedDetail.status,
        latestScoreValue: loadedDetail.displayValue,
      });
    }
  } catch (error) {
    entryError.value = errorMessage(error, '成绩详情加载失败');
  }
}

function validateEntryForm(): boolean {
  entryValidation.businessTime = entryForm.scoreBusinessTime
    ? ''
    : '请选择成绩业务发生时间';
  entryValidation.timeSource = entryForm.timeSource
    ? ''
    : '请选择时间来源';
  if (!entryForm.activityProjectId) {
    entryError.value = '请选择活动项目';
    return false;
  }
  if (!entryForm.studentId) {
    entryError.value = '请选择参赛学生';
    return false;
  }
  const project = selectedEntryProject.value;
  if (!project) {
    entryError.value = '项目配置尚未加载';
    return false;
  }
  const valueMissing =
    (project.scoreStorageType === 'INTEGER' && entryForm.integerValue === null) ||
    (project.scoreStorageType === 'DECIMAL' && entryForm.decimalValue === null) ||
    (project.scoreStorageType === 'DURATION' && entryForm.durationMs === null) ||
    (project.scoreStorageType === 'GRADE' && !entryForm.grade);
  if (valueMissing) {
    entryError.value = '请填写成绩';
    return false;
  }
  if (entryValidation.businessTime || entryValidation.timeSource) {
    entryError.value = '请完整填写必填字段';
    return false;
  }
  entryError.value = null;
  return true;
}

function buildValuePayload(): ScoreDraftValuePayload {
  const payload: ScoreDraftValuePayload = {
    scoreBusinessTime: entryForm.scoreBusinessTime,
    timeSource: entryForm.timeSource,
  };
  switch (selectedEntryProject.value?.scoreStorageType) {
    case 'INTEGER':
      payload.integerValue = entryForm.integerValue;
      break;
    case 'DECIMAL':
      payload.decimalValue = entryForm.decimalValue;
      break;
    case 'DURATION':
      payload.durationMs = entryForm.durationMs;
      break;
    case 'GRADE':
      payload.grade = entryForm.grade;
      break;
  }
  return payload;
}

async function finishEntryMutation(): Promise<void> {
  entryVisible.value = false;
  resetEntryForm();
  activeTab.value = 'mine';
  myCurrentPage.value = 1;
  await loadMine();
}

async function saveEntryDraft(): Promise<void> {
  if (savingDraft.value || submittingDraft.value || !validateEntryForm()) return;
  savingDraft.value = true;
  entryError.value = null;
  try {
    if (entryMode.value === 'create') {
      const payload: CreateScoreDraftPayload = {
        activityProjectId: entryForm.activityProjectId,
        studentId: entryForm.studentId,
        ...buildValuePayload(),
      };
      await createSchoolAdminScoreDraft(payload);
    } else if (editingAttemptId.value) {
      await updateSchoolAdminScoreDraft(
        editingAttemptId.value,
        buildValuePayload(),
      );
    }
    await finishEntryMutation();
  } catch (error) {
    entryError.value = errorMessage(error, '草稿保存失败');
  } finally {
    savingDraft.value = false;
  }
}

async function saveAndSubmitEntry(): Promise<void> {
  if (savingDraft.value || submittingDraft.value) return;
  if (!retainedDraftId.value && !validateEntryForm()) return;
  submittingDraft.value = true;
  entryError.value = null;
  try {
    if (!retainedDraftId.value) {
      const payload: CreateScoreDraftPayload = {
        activityProjectId: entryForm.activityProjectId,
        studentId: entryForm.studentId,
        ...buildValuePayload(),
      };
      const draft = await createSchoolAdminScoreDraft(payload);
      retainedDraftId.value = draft.attemptId;
    }
    await submitSchoolAdminScoreDraft(retainedDraftId.value);
    await finishEntryMutation();
    await loadList();
  } catch (error) {
    entryError.value = retainedDraftId.value
      ? `草稿已保存，但提交失败：${errorMessage(error, '请稍后重试')}`
      : errorMessage(error, '草稿创建失败');
    if (retainedDraftId.value) {
      myLoaded.value = false;
    }
  } finally {
    submittingDraft.value = false;
  }
}

function openSubmitConfirmation(attempt: SchoolAdminScoreAttemptItem): void {
  if (attempt.status !== 'DRAFT' || submittingAttemptIds.has(attempt.attemptId)) return;
  submitTarget.value = attempt;
  submitConfirmVisible.value = true;
}

async function confirmSubmitEntry(): Promise<void> {
  const target = submitTarget.value;
  if (!target || confirmSubmitting.value || submittingAttemptIds.has(target.attemptId)) return;
  confirmSubmitting.value = true;
  submittingAttemptIds.add(target.attemptId);
  try {
    await submitSchoolAdminScoreDraft(target.attemptId);
    submitConfirmVisible.value = false;
    await Promise.all([loadMine(), loadList()]);
  } catch (error) {
    myError.value = errorMessage(error, '提交审核失败');
  } finally {
    submittingAttemptIds.delete(target.attemptId);
    confirmSubmitting.value = false;
  }
}

async function openDetail(attemptId: string): Promise<void> {
  detailAttemptId.value = attemptId;
  detailVisible.value = true;
  await reloadDetail();
}

async function reloadDetail(): Promise<void> {
  if (!detailAttemptId.value) return;
  detailLoading.value = true;
  detailError.value = null;
  try {
    detail.value = await fetchSchoolAdminScoreAttempt(detailAttemptId.value);
  } catch (error) {
    detail.value = null;
    detailError.value = errorMessage(error, '详情加载失败');
  } finally {
    detailLoading.value = false;
  }
}

function isOwnEntry(attempt: SchoolAdminScoreAttemptItem): boolean {
  return auth.user?.userId === attempt.enteredBy;
}

function openApprove(attempt: SchoolAdminScoreAttemptItem): void {
  if (isOwnEntry(attempt) || attempt.status !== 'PENDING_REVIEW') return;
  selectedAttempt.value = attempt;
  approveComment.value = '';
  designationChoice.value = null;
  designationError.value = '';
  approveError.value = null;
  approveVisible.value = true;
}

async function submitApprove(): Promise<void> {
  if (approving.value || !selectedAttempt.value) return;
  if (
    selectedAttempt.value.effectiveScoreRule === 'ADMIN_DESIGNATED' &&
    designationChoice.value === null
  ) {
    designationError.value = '请选择是否设为当前有效成绩';
    return;
  }
  designationError.value = '';
  approveError.value = null;
  approving.value = true;
  const attemptId = selectedAttempt.value.attemptId;
  const payload: ApproveScorePayload = {
    reviewComment: approveComment.value.trim() || null,
  };
  if (selectedAttempt.value.effectiveScoreRule === 'ADMIN_DESIGNATED') {
    payload.makeCurrentEffective = designationChoice.value;
  }
  try {
    await approveSchoolAdminScoreAttempt(attemptId, payload);
    approveVisible.value = false;
    await loadList();
    try {
      detail.value = await fetchSchoolAdminScoreAttempt(attemptId);
      if (detailVisible.value) {
        detailAttemptId.value = attemptId;
        detailError.value = null;
      }
    } catch (error) {
      detailError.value = errorMessage(error, '详情加载失败');
    }
  } catch (error) {
    approveError.value = errorMessage(error, '审核通过失败');
  } finally {
    approving.value = false;
  }
}

function openReject(attempt: SchoolAdminScoreAttemptItem): void {
  if (isOwnEntry(attempt) || attempt.status !== 'PENDING_REVIEW') return;
  selectedAttempt.value = attempt;
  rejectReason.value = '';
  rejectComment.value = '';
  rejectReasonError.value = '';
  rejectError.value = null;
  rejectVisible.value = true;
}

async function submitReject(): Promise<void> {
  if (rejecting.value || !selectedAttempt.value) return;
  const reason = rejectReason.value.trim();
  if (!reason) {
    rejectReasonError.value = '请输入驳回原因';
    return;
  }
  rejectReasonError.value = '';
  rejectError.value = null;
  rejecting.value = true;
  try {
    await rejectSchoolAdminScoreAttempt(selectedAttempt.value.attemptId, {
      rejectReason: reason,
      reviewComment: rejectComment.value.trim() || null,
    });
    rejectVisible.value = false;
    await loadList();
  } catch (error) {
    rejectError.value = errorMessage(error, '审核驳回失败');
  } finally {
    rejecting.value = false;
  }
}

function statusLabel(status: ScoreAttemptStatus): string {
  return statusOptions.find((option) => option.value === status)?.label ?? status;
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function displayEmpty(value: string | number | null): string | number {
  return value === null || value === '' ? '-' : value;
}

onMounted(() => {
  void loadList();
});
</script>

<style scoped>
.score-review-page h2 {
  margin-top: 0;
}
.open-entry-dialog {
  margin-bottom: 16px;
}
.score-tabs {
  width: 100%;
}
.filters {
  margin-bottom: 16px;
}
.my-entry-filters {
  margin-bottom: 16px;
}
.pagination {
  justify-content: center;
  margin-top: 20px;
}
.my-pagination {
  justify-content: center;
  margin-top: 20px;
}
.self-review-warning {
  display: block;
  color: var(--el-color-warning);
  font-size: 12px;
  line-height: 18px;
}
.pending-entry-hint,
.readonly-entry-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.submit-error {
  color: var(--el-color-danger);
  margin-bottom: 12px;
}
.field-error {
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.4;
  width: 100%;
}
.review-history {
  margin-top: 12px;
}
.entry-rejection-history {
  margin-bottom: 16px;
}
</style>
