import http from './http';
import type {
  PublishRankingPayload,
  RankingPreviewResult,
  RankingProjectDetail,
  RankingProjectFilter,
  RankingProjectPage,
  RankingVersionDetail,
  RankingVersionPage,
  WithdrawRankingPayload,
} from '@/types/school-admin-ranking';

const PREFIX = '/v1/school-admin/rankings';

export async function fetchRankingProjects(
  filter: RankingProjectFilter = {},
  page = 0,
  size = 20,
): Promise<RankingProjectPage> {
  const params: Record<string, string | number> = { page, size };
  if (filter.executionStatus) params.executionStatus = filter.executionStatus;
  if (filter.rankingStatus) params.rankingStatus = filter.rankingStatus;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<RankingProjectPage>(`${PREFIX}/projects`, { params });
  return response.data;
}

export async function fetchRankingProject(
  activityProjectId: string,
): Promise<RankingProjectDetail> {
  const response = await http.get<RankingProjectDetail>(
    `${PREFIX}/projects/${activityProjectId}`,
  );
  return response.data;
}

export async function previewRanking(
  activityProjectId: string,
): Promise<RankingPreviewResult> {
  const response = await http.get<RankingPreviewResult>(
    `${PREFIX}/projects/${activityProjectId}/preview`,
  );
  return response.data;
}

export async function publishRanking(
  activityProjectId: string,
  payload: PublishRankingPayload,
): Promise<RankingVersionDetail> {
  const response = await http.post<RankingVersionDetail>(
    `${PREFIX}/projects/${activityProjectId}/publish`,
    payload,
  );
  return response.data;
}

export async function fetchCurrentRanking(
  activityProjectId: string,
): Promise<RankingVersionDetail> {
  const response = await http.get<RankingVersionDetail>(
    `${PREFIX}/projects/${activityProjectId}/current`,
  );
  return response.data;
}

export async function fetchRankingVersions(
  activityProjectId: string,
  page = 0,
  size = 20,
): Promise<RankingVersionPage> {
  const response = await http.get<RankingVersionPage>(
    `${PREFIX}/projects/${activityProjectId}/versions`,
    { params: { page, size } },
  );
  return response.data;
}

export async function fetchRankingVersion(
  versionId: string,
): Promise<RankingVersionDetail> {
  const response = await http.get<RankingVersionDetail>(
    `${PREFIX}/versions/${versionId}`,
  );
  return response.data;
}

export async function withdrawRanking(
  activityProjectId: string,
  payload: WithdrawRankingPayload,
): Promise<void> {
  await http.post(`${PREFIX}/projects/${activityProjectId}/withdraw`, payload);
}
