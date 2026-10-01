import { apiRequest } from './http';
import type { PageResponse } from '../types/schoolGovernance';
import type {
  ActivityResultEditor,
  ActivityResultHistoryEntry,
  ActivityResultMutationResponse,
  ResultFormatEditResult,
  ResultFormatHistoryEntry,
  ResultFormatPresentation,
  ActivityResultSavePayload,
  GovernanceResultDetail,
  GovernanceResultSummary,
  PendingResultReviewDetail,
  PendingResultReviewSummary,
  PublicActivityResult,
  StudentActivityResult
} from '../types/activityResult';

export function getActivityResultEditor(activityId: string) {
  return apiRequest<ActivityResultEditor>(`/activities/${encodeURIComponent(activityId)}/result`);
}

export function getActivityResultHistory(activityId: string) {
  return apiRequest<ActivityResultHistoryEntry[]>(`/school-admin/activities/${encodeURIComponent(activityId)}/result/history`);
}

export function saveActivityResult(activityId: string, payload: ActivityResultSavePayload) {
  return apiRequest<ActivityResultEditor>(`/activities/${encodeURIComponent(activityId)}/result`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  });
}

function resultAction(resultId: string, action: string) {
  return apiRequest<ActivityResultMutationResponse>(`/activity-results/${encodeURIComponent(resultId)}/${action}`, {
    method: 'POST'
  });
}

export const publishActivityResultInternally = (id: string) => resultAction(id, 'publish');
export const withdrawActivityResultInternally = (id: string) => resultAction(id, 'withdraw');
export const returnActivityResultToDraft = (id: string) => resultAction(id, 'return-to-draft');
export const submitActivityResultForReview = (id: string) => resultAction(id, 'submit-public-review');
export const makeActivityResultPublic = (id: string) => resultAction(id, 'make-public');

export function getPublicActivityResult(activityId: string) {
  return apiRequest<PublicActivityResult>(`/public/activities/${encodeURIComponent(activityId)}/result`);
}

export function getStudentActivityResult(activityId: string) {
  return apiRequest<StudentActivityResult>(`/student/activities/${encodeURIComponent(activityId)}/result`);
}

export function listPendingResultReviews(page = 0, size = 20) {
  return apiRequest<PageResponse<PendingResultReviewSummary>>(`/super-admin/activity-results/public-reviews?page=${page}&size=${size}`);
}

export function getPendingResultReview(resultId: string) {
  return apiRequest<PendingResultReviewDetail>(`/super-admin/activity-results/${encodeURIComponent(resultId)}/public-review`);
}

export function approvePendingResultReview(resultId: string) {
  return apiRequest<ActivityResultMutationResponse>(`/super-admin/activity-results/${encodeURIComponent(resultId)}/approve-public-review`, { method: 'POST' });
}

export function rejectPendingResultReview(resultId: string, reason: string) {
  return apiRequest<ActivityResultMutationResponse>(`/super-admin/activity-results/${encodeURIComponent(resultId)}/reject-public-review`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}

export function listGovernanceResults(page = 0, size = 20, publicStatus = '', blocked?: boolean) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (publicStatus) params.set('publicStatus', publicStatus);
  if (blocked !== undefined) params.set('blocked', String(blocked));
  return apiRequest<PageResponse<GovernanceResultSummary>>(`/super-admin/activity-results/governance?${params}`);
}

export function getGovernanceResult(resultId: string) {
  return apiRequest<GovernanceResultDetail>(`/super-admin/activity-results/${encodeURIComponent(resultId)}/governance`);
}

function governanceAction(resultId: string, action: string, reason: string) {
  return apiRequest<ActivityResultMutationResponse>(`/super-admin/activity-results/${encodeURIComponent(resultId)}/${action}`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}

export const takedownActivityResult = (id: string, reason: string) => governanceAction(id, 'takedown', reason);
export const resetActivityResultTakedown = (id: string, reason: string) => governanceAction(id, 'reset-takedown', reason);

export function saveResultFormatEdit(resultId: string, versionId: string, reason: string, presentation: ResultFormatPresentation) {
  return apiRequest<ResultFormatEditResult>(
    `/activity-results/${encodeURIComponent(resultId)}/versions/${encodeURIComponent(versionId)}/format-edits`,
    { method: 'POST', body: JSON.stringify({ reason, presentation }) }
  );
}

export function getResultFormatHistory(resultId: string, versionId: string) {
  return apiRequest<ResultFormatHistoryEntry[]>(
    `/school-admin/activity-results/${encodeURIComponent(resultId)}/versions/${encodeURIComponent(versionId)}/format-history`
  );
}
