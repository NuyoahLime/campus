import http from './http';
import type { PageResponse } from '@/types/api';
import type { AdminApplicationItem, AdminApplicationDetail, AdminApplicationStats, AdminSchoolOption, AdminListParams } from '@/types/admin-application';

export async function fetchAdminApplications(params: AdminListParams = {}): Promise<PageResponse<AdminApplicationItem>> {
  const query: Record<string,unknown> = {};
  if (params.status) query.status = params.status;
  if (params.schoolId) query.schoolId = params.schoolId;
  if (params.keyword) query.keyword = params.keyword;
  if (params.createdFrom) query.createdFrom = params.createdFrom;
  if (params.createdTo) query.createdTo = params.createdTo;
  if (params.sort) query.sort = params.sort;
  if (params.page !== undefined) query.page = params.page;
  if (params.size !== undefined) query.size = params.size;
  const res = await http.get<PageResponse<AdminApplicationItem>>('/v1/admin/activity-applications', { params: query });
  return res.data;
}

export async function fetchAdminApplicationById(id: string): Promise<AdminApplicationDetail> {
  const res = await http.get<AdminApplicationDetail>(`/v1/admin/activity-applications/${id}`);
  return res.data;
}

export async function fetchAdminStats(): Promise<AdminApplicationStats> {
  const res = await http.get<AdminApplicationStats>('/v1/admin/activity-applications/stats');
  return res.data;
}

export async function fetchAdminSchools(): Promise<AdminSchoolOption[]> {
  const res = await http.get<AdminSchoolOption[]>('/v1/admin/activity-applications/schools');
  return res.data;
}

export async function approveApplication(id: string): Promise<AdminApplicationDetail> {
  const res = await http.post<AdminApplicationDetail>(`/v1/admin/activity-applications/${id}/approve`);
  return res.data;
}

export async function rejectApplication(id: string, reason: string): Promise<AdminApplicationDetail> {
  const res = await http.post<AdminApplicationDetail>(`/v1/admin/activity-applications/${id}/reject`, { reason });
  return res.data;
}
