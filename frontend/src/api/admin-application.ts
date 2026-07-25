import http from './http';
import type { PageResponse } from '@/types/api';
import type { AdminApplicationItem, AdminApplicationDetail, AdminApplicationStats, AdminSchoolOption, AdminListParams } from '@/types/admin-application';

export async function fetchAdminApplications(params: AdminListParams = {}): Promise<PageResponse<AdminApplicationItem>> {
  const res = await http.get<PageResponse<AdminApplicationItem>>('/v1/admin/activity-applications', { params: params as Record<string,unknown> });
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
