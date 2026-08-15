import { apiRequest } from './http';
import type {
  PageResponse,
  SchoolRegistrationDetail,
  SchoolRegistrationListItem,
  SchoolRegistrationStatus
} from '../types/schoolRegistration';

export interface SchoolRegistrationActionResponse {
  id: string;
  schoolName: string;
  status: SchoolRegistrationStatus;
  createdSchoolId: string | null;
}

export async function listSchoolRegistrations(
  page = 0,
  size = 20,
  status: SchoolRegistrationStatus | null = null
): Promise<PageResponse<SchoolRegistrationListItem>> {
  const query = new URLSearchParams({
    page: String(page),
    size: String(size)
  });
  if (status) {
    query.set('status', status);
  }
  return apiRequest<PageResponse<SchoolRegistrationListItem>>(
    `/school-registrations?${query.toString()}`
  );
}

export async function getSchoolRegistration(
  registrationId: string
): Promise<SchoolRegistrationDetail> {
  return apiRequest<SchoolRegistrationDetail>(
    `/school-registrations/${encodeURIComponent(registrationId)}`
  );
}

export async function requestSchoolRegistrationSupplement(
  registrationId: string,
  comment: string
): Promise<SchoolRegistrationActionResponse> {
  return apiRequest<SchoolRegistrationActionResponse>(
    `/school-registrations/${encodeURIComponent(registrationId)}/request-supplement`,
    { method: 'POST', body: JSON.stringify({ comment }) }
  );
}

export async function approveSchoolRegistration(
  registrationId: string,
  comment?: string
): Promise<SchoolRegistrationActionResponse> {
  return apiRequest<SchoolRegistrationActionResponse>(
    `/school-registrations/${encodeURIComponent(registrationId)}/approve`,
    { method: 'POST', body: JSON.stringify(comment?.trim() ? { comment: comment.trim() } : {}) }
  );
}

export async function rejectSchoolRegistration(
  registrationId: string,
  reason: string
): Promise<SchoolRegistrationActionResponse> {
  return apiRequest<SchoolRegistrationActionResponse>(
    `/school-registrations/${encodeURIComponent(registrationId)}/reject`,
    { method: 'POST', body: JSON.stringify({ reason }) }
  );
}
