import http from './http';
import type { PageResponse } from '@/types/api';
import type {
  ApproveScorePayload,
  RejectScorePayload,
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
  ScoreReviewListFilter,
} from '@/types/school-admin-score-review';

export async function fetchSchoolAdminScoreAttempts(
  filter: ScoreReviewListFilter = {},
  page = 0,
  size = 20,
): Promise<PageResponse<SchoolAdminScoreAttemptItem>> {
  const params: Record<string, string | number> = {
    status: filter.status ?? 'PENDING_REVIEW',
    page,
    size,
  };
  if (filter.activityId) params.activityId = filter.activityId;
  if (filter.projectId) params.projectId = filter.projectId;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<PageResponse<SchoolAdminScoreAttemptItem>>(
    '/v1/school-admin/score-attempts',
    { params },
  );
  return response.data;
}

export async function fetchSchoolAdminScoreAttempt(
  attemptId: string,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.get<SchoolAdminScoreAttemptDetail>(
    `/v1/school-admin/score-attempts/${attemptId}`,
  );
  return response.data;
}

export async function approveSchoolAdminScoreAttempt(
  attemptId: string,
  payload: ApproveScorePayload,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.post<SchoolAdminScoreAttemptDetail>(
    `/v1/school-admin/score-attempts/${attemptId}/approve`,
    payload,
  );
  return response.data;
}

export async function rejectSchoolAdminScoreAttempt(
  attemptId: string,
  payload: RejectScorePayload,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.post<SchoolAdminScoreAttemptDetail>(
    `/v1/school-admin/score-attempts/${attemptId}/reject`,
    payload,
  );
  return response.data;
}
