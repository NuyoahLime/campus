import { apiRequest } from './http';
import type {
  SchoolAdminScoreCandidatesResponse,
  SchoolAdminScoreDetail,
  SchoolAdminScoreDraftRequest,
  SchoolAdminScoreDraftUpdateRequest,
  SchoolAdminScoreListItem,
  SchoolAdminScoresResponse
} from '../types/schoolAdminScore';

export function getSchoolAdminScores(activityId: string) {
  return apiRequest<SchoolAdminScoresResponse>(`/school-admin/activities/${encodeURIComponent(activityId)}/scores`);
}

export function getSchoolAdminScoreCandidates(activityId: string) {
  return apiRequest<SchoolAdminScoreCandidatesResponse>(`/school-admin/activities/${encodeURIComponent(activityId)}/score-candidates`);
}

export function getSchoolAdminScoreDetail(scoreAttemptId: string) {
  return apiRequest<SchoolAdminScoreDetail>(`/school-admin/score-attempts/${encodeURIComponent(scoreAttemptId)}`);
}

export function createSchoolAdminScoreDraft(activityProjectId: string, body: SchoolAdminScoreDraftRequest) {
  return apiRequest<SchoolAdminScoreListItem>(`/school-admin/activity-projects/${encodeURIComponent(activityProjectId)}/score-attempts`, {
    method: 'POST',
    body: JSON.stringify(body)
  });
}

export function updateSchoolAdminScoreDraft(scoreAttemptId: string, body: SchoolAdminScoreDraftUpdateRequest) {
  return apiRequest<SchoolAdminScoreListItem>(`/school-admin/score-attempts/${encodeURIComponent(scoreAttemptId)}`, {
    method: 'PATCH',
    body: JSON.stringify(body)
  });
}
