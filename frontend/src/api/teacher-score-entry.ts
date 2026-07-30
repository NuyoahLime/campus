import http from './http';
import type { PageResponse } from '@/types/api';
import type {
  CreateTeacherScorePayload,
  TeacherScoreAttemptDetail,
  TeacherScoreAttemptItem,
  TeacherScoreFilter,
  UpdateTeacherScorePayload,
} from '@/types/teacher-score-entry';

export async function createTeacherScoreAttempt(
  payload: CreateTeacherScorePayload,
): Promise<TeacherScoreAttemptDetail> {
  const response = await http.post<TeacherScoreAttemptDetail>(
    '/v1/teacher/score-attempts',
    payload,
  );
  return response.data;
}

export async function fetchMyTeacherScoreAttempts(
  filter: TeacherScoreFilter = {},
  page = 0,
  size = 20,
): Promise<PageResponse<TeacherScoreAttemptItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.status) params.status = filter.status;
  if (filter.activityProjectId) {
    params.activityProjectId = filter.activityProjectId;
  }
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<PageResponse<TeacherScoreAttemptItem>>(
    '/v1/teacher/score-attempts/mine',
    { params },
  );
  return response.data;
}

export async function fetchTeacherScoreAttemptDetail(
  attemptId: string,
): Promise<TeacherScoreAttemptDetail> {
  const response = await http.get<TeacherScoreAttemptDetail>(
    `/v1/teacher/score-attempts/${attemptId}`,
  );
  return response.data;
}

export async function updateTeacherScoreDraft(
  attemptId: string,
  payload: UpdateTeacherScorePayload,
): Promise<TeacherScoreAttemptDetail> {
  const response = await http.patch<TeacherScoreAttemptDetail>(
    `/v1/teacher/score-attempts/${attemptId}/draft`,
    payload,
  );
  return response.data;
}

export async function submitTeacherScoreDraft(
  attemptId: string,
): Promise<TeacherScoreAttemptDetail> {
  const response = await http.post<TeacherScoreAttemptDetail>(
    `/v1/teacher/score-attempts/${attemptId}/submit`,
  );
  return response.data;
}
