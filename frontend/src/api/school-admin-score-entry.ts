import http from './http';
import type { PageResponse } from '@/types/api';
import type {
  CreateScoreDraftPayload,
  SchoolAdminScoreEntryFilter,
  ScoreEntryParticipantOption,
  ScoreEntryProjectOption,
  UpdateScoreDraftPayload,
} from '@/types/school-admin-score-entry';
import type {
  SchoolAdminScoreAttemptDetail,
  SchoolAdminScoreAttemptItem,
} from '@/types/school-admin-score-review';

export async function fetchScoreEntryProjects(
  keyword = '',
  page = 0,
  size = 20,
): Promise<PageResponse<ScoreEntryProjectOption>> {
  const params: Record<string, string | number> = { page, size };
  const normalizedKeyword = keyword.trim();
  if (normalizedKeyword) params.keyword = normalizedKeyword;
  const response = await http.get<PageResponse<ScoreEntryProjectOption>>(
    '/v1/school-admin/score-entry/projects',
    { params },
  );
  return response.data;
}

export async function fetchScoreEntryParticipants(
  activityProjectId: string,
  keyword = '',
  page = 0,
  size = 20,
): Promise<PageResponse<ScoreEntryParticipantOption>> {
  const params: Record<string, string | number> = { page, size };
  const normalizedKeyword = keyword.trim();
  if (normalizedKeyword) params.keyword = normalizedKeyword;
  const response = await http.get<PageResponse<ScoreEntryParticipantOption>>(
    `/v1/school-admin/score-entry/projects/${activityProjectId}/participants`,
    { params },
  );
  return response.data;
}

export async function createSchoolAdminScoreDraft(
  payload: CreateScoreDraftPayload,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.post<SchoolAdminScoreAttemptDetail>(
    '/v1/school-admin/score-attempts/drafts',
    payload,
  );
  return response.data;
}

export async function updateSchoolAdminScoreDraft(
  attemptId: string,
  payload: UpdateScoreDraftPayload,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.patch<SchoolAdminScoreAttemptDetail>(
    `/v1/school-admin/score-attempts/${attemptId}/draft`,
    payload,
  );
  return response.data;
}

export async function submitSchoolAdminScoreDraft(
  attemptId: string,
): Promise<SchoolAdminScoreAttemptDetail> {
  const response = await http.post<SchoolAdminScoreAttemptDetail>(
    `/v1/school-admin/score-attempts/${attemptId}/submit`,
  );
  return response.data;
}

export async function fetchMySchoolAdminScoreEntries(
  filter: SchoolAdminScoreEntryFilter = {},
  page = 0,
  size = 20,
): Promise<PageResponse<SchoolAdminScoreAttemptItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.status) params.status = filter.status;
  if (filter.activityId) params.activityId = filter.activityId;
  if (filter.projectId) params.projectId = filter.projectId;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<PageResponse<SchoolAdminScoreAttemptItem>>(
    '/v1/school-admin/score-attempts/mine',
    { params },
  );
  return response.data;
}
