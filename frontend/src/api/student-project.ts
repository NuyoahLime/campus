import http from './http';
import type { PageResponse } from '@/types/api';
import type { StudentProjectItem, StudentProjectDetail, StudentProjectFilter } from '@/types/student-project';

export async function fetchMyProjects(
  filter: StudentProjectFilter,
  page = 0,
  size = 20,
): Promise<PageResponse<StudentProjectItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.executionStatus) params.executionStatus = filter.executionStatus;
  if (filter.scoreStatus) params.scoreStatus = filter.scoreStatus;
  if (filter.keyword) params.keyword = filter.keyword;
  const res = await http.get<PageResponse<StudentProjectItem>>('/v1/student/activity-projects/mine', { params });
  return res.data;
}

export async function fetchMyProjectById(projectId: string): Promise<StudentProjectDetail> {
  const res = await http.get<StudentProjectDetail>(`/v1/student/activity-projects/mine/${projectId}`);
  return res.data;
}
