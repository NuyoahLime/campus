import http from './http';
import type { PageResponse } from '@/types/api';
import type {
  TeacherProjectParticipantFilter,
  TeacherProjectParticipantItem,
  TeacherResponsibleProjectDetail,
  TeacherResponsibleProjectFilter,
  TeacherResponsibleProjectItem,
} from '@/types/teacher-responsible-project';

export async function fetchTeacherResponsibleProjects(
  filter: TeacherResponsibleProjectFilter = {},
  page = 0,
  size = 20,
): Promise<PageResponse<TeacherResponsibleProjectItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.executionStatus) params.executionStatus = filter.executionStatus;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<PageResponse<TeacherResponsibleProjectItem>>(
    '/v1/teacher/responsible-projects',
    { params },
  );
  return response.data;
}

export async function fetchTeacherResponsibleProject(
  activityProjectId: string,
): Promise<TeacherResponsibleProjectDetail> {
  const response = await http.get<TeacherResponsibleProjectDetail>(
    `/v1/teacher/responsible-projects/${activityProjectId}`,
  );
  return response.data;
}

export async function fetchTeacherProjectParticipants(
  activityProjectId: string,
  filter: TeacherProjectParticipantFilter = {},
  page = 0,
  size = 20,
): Promise<PageResponse<TeacherProjectParticipantItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.status) params.status = filter.status;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<PageResponse<TeacherProjectParticipantItem>>(
    `/v1/teacher/responsible-projects/${activityProjectId}/participants`,
    { params },
  );
  return response.data;
}
