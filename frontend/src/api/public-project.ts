import http from './http';
import type { PageResponse } from '@/types/api';
import type { PublicProjectItem, PublicProjectDetail, ProjectListFilter } from '@/types/project';

export async function fetchPublicProjects(
  filter: ProjectListFilter,
  page = 0,
  size = 20,
): Promise<PageResponse<PublicProjectItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.keyword) params.keyword = filter.keyword;
  if (filter.category) params.category = filter.category;
  if (filter.scoreStorageType) params.scoreStorageType = filter.scoreStorageType;
  if (filter.venueKeyword) params.venueKeyword = filter.venueKeyword;
  if (filter.equipmentKeyword) params.equipmentKeyword = filter.equipmentKeyword;

  const response = await http.get<PageResponse<PublicProjectItem>>('/v1/public/challenge-projects', {
    params,
  });
  return response.data;
}

export async function fetchPublicProjectById(
  projectId: string,
): Promise<PublicProjectDetail> {
  const response = await http.get<PublicProjectDetail>(
    `/v1/public/challenge-projects/${projectId}`,
  );
  return response.data;
}
