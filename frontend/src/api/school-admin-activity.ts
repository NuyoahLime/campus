import http from './http';
import type { PageResponse } from '@/types/api';
import type { SchoolAdminActivityItem, SchoolAdminActivityDetail, CreateActivityPayload, UpdateActivityPayload, ActivityListFilter, ActivityProjectItem, ActivityMutationResponse } from '@/types/school-admin-activity';

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

export async function createActivity(payload: CreateActivityPayload): Promise<ActivityMutationResponse> {
  const res = await http.post<ActivityMutationResponse>('/v1/school-admin/activities', payload);
  return res.data;
}

export async function updateActivity(id: string, payload: UpdateActivityPayload): Promise<ActivityMutationResponse> {
  const res = await http.patch<ActivityMutationResponse>(`/v1/school-admin/activities/${id}`, payload);
  return res.data;
}

export async function addProject(activityId: string, projectId: string): Promise<ActivityProjectItem> {
  const res = await http.post<ActivityProjectItem>(`/v1/school-admin/activities/${activityId}/projects`, { projectId });
  return res.data;
}

export async function removeProject(activityId: string, projectId: string): Promise<void> {
  await http.delete(`/v1/school-admin/activities/${activityId}/projects/${projectId}`);
}

export async function publishActivity(id: string): Promise<ActivityMutationResponse> {
  const res = await http.post<ActivityMutationResponse>(`/v1/school-admin/activities/${id}/publish`);
  return res.data;
}

export async function fetchAvailableProjects(page = 0, size = 100): Promise<PageResponse<{ projectId: string; name: string }>> {
  const res = await http.get<PageResponse<{ projectId: string; name: string }>>('/v1/challenge-projects', { params: { page, size } });
  return res.data;
}

import type { SchoolTeacherItem, ResponsibleTeacherItem } from '@/types/school-admin-activity';

export async function fetchSchoolTeachers(keyword = '', page = 0, size = 50): Promise<PageResponse<SchoolTeacherItem>> {
  const params: Record<string, string | number> = { page, size };
  if (keyword.trim()) params.keyword = keyword.trim();
  const res = await http.get<PageResponse<SchoolTeacherItem>>('/v1/school-admin/teachers', { params });
  return res.data;
}

export async function fetchResponsibleTeachers(activityId: string, projectId: string): Promise<ResponsibleTeacherItem[]> {
  const res = await http.get<ResponsibleTeacherItem[]>(`/v1/school-admin/activities/${activityId}/projects/${projectId}/responsible-teachers`);
  return res.data;
}

export async function assignResponsibleTeacher(activityId: string, projectId: string, teacherId: string): Promise<ResponsibleTeacherItem> {
  const res = await http.post<ResponsibleTeacherItem>(`/v1/school-admin/activities/${activityId}/projects/${projectId}/responsible-teachers`, { teacherId });
  return res.data;
}

export async function unassignResponsibleTeacher(activityId: string, projectId: string, teacherId: string): Promise<void> {
  await http.delete(`/v1/school-admin/activities/${activityId}/projects/${projectId}/responsible-teachers/${teacherId}`);
}

