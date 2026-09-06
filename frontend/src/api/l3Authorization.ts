import { apiRequest } from './http';
import type {
  L3Authorization,
  L3AuthorizationForm,
  L3AuthorizationPage,
  L3AuthorizationStatus
} from '../types/l3Authorization';

function queryString(params: Record<string, string | number | null | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value).trim()) query.set(key, String(value));
  });
  const value = query.toString();
  return value ? `?${value}` : '';
}

export function listSchoolL3Authorizations(
  page = 0,
  size = 20,
  status: L3AuthorizationStatus | '' = '',
  projectId = ''
) {
  return apiRequest<L3AuthorizationPage>(
    `/school-admin/l3-authorizations${queryString({ page, size, status, projectId })}`
  );
}

export function getSchoolL3Authorization(id: string) {
  return apiRequest<L3Authorization>(`/school-admin/l3-authorizations/${encodeURIComponent(id)}`);
}

export function createL3Authorization(form: L3AuthorizationForm) {
  return apiRequest<L3Authorization>('/school-admin/l3-authorizations', {
    method: 'POST',
    body: JSON.stringify(form)
  });
}

export function updateL3Authorization(id: string, form: Pick<L3AuthorizationForm, 'dataScope' | 'allowSchoolName' | 'allowStudentName'>) {
  return apiRequest<L3Authorization>(`/school-admin/l3-authorizations/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(form)
  });
}

export function submitL3Authorization(id: string) {
  return apiRequest<L3Authorization>(`/school-admin/l3-authorizations/${encodeURIComponent(id)}/submit`, {
    method: 'POST'
  });
}

export function returnL3AuthorizationToDraft(id: string) {
  return apiRequest<L3Authorization>(`/school-admin/l3-authorizations/${encodeURIComponent(id)}/return-to-draft`, {
    method: 'POST'
  });
}

export function withdrawL3Authorization(id: string, reason: string) {
  return apiRequest<L3Authorization>(`/school-admin/l3-authorizations/${encodeURIComponent(id)}/withdraw`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}

export function listReviewL3Authorizations(
  page = 0,
  size = 20,
  status: L3AuthorizationStatus | '' = 'PENDING_REVIEW',
  schoolId = '',
  projectId = ''
) {
  return apiRequest<L3AuthorizationPage>(
    `/super-admin/l3-authorizations${queryString({ page, size, status, schoolId, projectId })}`
  );
}

export function getReviewL3Authorization(id: string) {
  return apiRequest<L3Authorization>(`/super-admin/l3-authorizations/${encodeURIComponent(id)}`);
}

export function approveL3Authorization(id: string, comment: string) {
  return apiRequest<L3Authorization>(`/super-admin/l3-authorizations/${encodeURIComponent(id)}/approve`, {
    method: 'POST',
    body: JSON.stringify({ comment })
  });
}

export function rejectL3Authorization(id: string, reason: string) {
  return apiRequest<L3Authorization>(`/super-admin/l3-authorizations/${encodeURIComponent(id)}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}

export function resumeL3Authorization(id: string) {
  return apiRequest<L3Authorization>(`/super-admin/l3-authorizations/${encodeURIComponent(id)}/resume`, {
    method: 'POST'
  });
}
