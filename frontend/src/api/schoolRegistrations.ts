import { apiRequest } from './http';
import type {
  PageResponse,
  SchoolRegistrationDetail,
  SchoolRegistrationListItem,
  SchoolRegistrationStatus
} from '../types/schoolRegistration';

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
