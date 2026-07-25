import http from './http';
import type { PageResponse } from '@/types/api';
import type {
  TeacherActivityApplicationItem,
  CreateActivityApplicationRequest,
  UpdateActivityApplicationRequest,
} from '@/types/teacher-application';

export async function fetchMyApplications(
  params: { status?: string; schoolId?: string; keyword?: string; page?: number; size?: number } = {},
): Promise<PageResponse<TeacherActivityApplicationItem>> {
  const res = await http.get<PageResponse<TeacherActivityApplicationItem>>(
    '/v1/activity-applications/mine/page',
    { params },
  );
  return res.data;
}

export async function createApplication(
  data: CreateActivityApplicationRequest,
): Promise<TeacherActivityApplicationItem> {
  const res = await http.post<TeacherActivityApplicationItem>('/v1/activity-applications', data);
  return res.data;
}

export async function getMyApplication(id: string): Promise<TeacherActivityApplicationItem> {
  const res = await http.get<TeacherActivityApplicationItem>(`/v1/activity-applications/mine/${id}`);
  return res.data;
}

export async function updateDraft(
  id: string,
  data: UpdateActivityApplicationRequest,
): Promise<TeacherActivityApplicationItem> {
  const res = await http.put<TeacherActivityApplicationItem>(`/v1/activity-applications/mine/${id}`, data);
  return res.data;
}

export async function withdrawApplication(id: string): Promise<TeacherActivityApplicationItem> {
  const res = await http.post<TeacherActivityApplicationItem>(`/v1/activity-applications/mine/${id}/withdraw`);
  return res.data;
}

export async function returnToDraft(id: string): Promise<TeacherActivityApplicationItem> {
  const res = await http.post<TeacherActivityApplicationItem>(`/v1/activity-applications/mine/${id}/return-to-draft`);
  return res.data;
}

export async function resubmitApplication(id: string): Promise<TeacherActivityApplicationItem> {
  const res = await http.post<TeacherActivityApplicationItem>(`/v1/activity-applications/mine/${id}/submit`);
  return res.data;
}
