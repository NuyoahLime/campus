import http from './http';
import type { PageResponse } from '@/types/api';
import type { SchoolAdminActivityItem, SchoolAdminActivityDetail, CreateActivityPayload, UpdateActivityPayload, ActivityListFilter, ActivityProjectItem } from '@/types/school-admin-activity';

export async function fetchActivities(filter: ActivityListFilter, page = 0, size = 20): Promise<PageResponse<SchoolAdminActivityItem>> {
  const params: Record<string, string | number> = { page, size };
  if (filter.executionStatus) params.executionStatus = filter.executionStatus;
  if (filter.publicStatus) params.publicStatus = filter.publicStatus;
  if (filter.keyword) params.keyword = filter.keyword;
  const res = await http.get<PageResponse<SchoolAdminActivityItem>>('/v1/school-admin/activities', { params });
  return res.data;
}

export async function fetchActivity(id: string): Promise<SchoolAdminActivityDetail> {
  const res = await http.get<SchoolAdminActivityDetail>(`/v1/school-admin/activities/${id}`);
  return res.data;
}

export async function createActivity(payload: CreateActivityPayload): Promise<SchoolAdminActivityDetail> {
  const res = await http.post<SchoolAdminActivityDetail>('/v1/school-admin/activities', payload);
  return res.data;
}

export async function updateActivity(id: string, payload: UpdateActivityPayload): Promise<SchoolAdminActivityDetail> {
  const res = await http.patch<SchoolAdminActivityDetail>(`/v1/school-admin/activities/${id}`, payload);
  return res.data;
}

export async function addProject(activityId: string, projectId: string): Promise<ActivityProjectItem> {
  const res = await http.post<ActivityProjectItem>(`/v1/school-admin/activities/${activityId}/projects`, { projectId });
  return res.data;
}

export async function removeProject(activityId: string, projectId: string): Promise<void> {
  await http.delete(`/v1/school-admin/activities/${activityId}/projects/${projectId}`);
}

export async function publishActivity(id: string): Promise<SchoolAdminActivityDetail> {
  const res = await http.post<SchoolAdminActivityDetail>(`/v1/school-admin/activities/${id}/publish`);
  return res.data;
}
