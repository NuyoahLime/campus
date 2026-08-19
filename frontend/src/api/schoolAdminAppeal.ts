import { apiRequest } from './http';
import type { StudentAppeal, StudentAppealPage } from '../types/studentAppeal';

interface AppealMutationResponse {
  id: string;
  status: string;
}

export function listSchoolAdminAppeals(page = 0, size = 20) {
  return apiRequest<StudentAppealPage>(`/school-admin/appeals?page=${page}&size=${size}`);
}

export function getSchoolAdminAppeal(id: string) {
  return apiRequest<StudentAppeal>(`/school-admin/appeals/${encodeURIComponent(id)}`);
}

export function beginSchoolAdminAppeal(id: string) {
  return apiRequest<AppealMutationResponse>(`/school-admin/appeals/${encodeURIComponent(id)}/begin-processing`, {
    method: 'POST'
  });
}

export function rejectSchoolAdminAppeal(id: string, resolution: string) {
  return apiRequest<AppealMutationResponse>(`/school-admin/appeals/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify({ resolution })
  });
}
