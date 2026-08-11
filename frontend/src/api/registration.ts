import { apiRequest } from './http';
import type {
  PageResponse,
  PublicSchoolSummary,
  StudentRegistrationRequest,
  StudentRegistrationResponse
} from '../types/registration';

export async function getPublicSchools(): Promise<PageResponse<PublicSchoolSummary>> {
  return apiRequest<PageResponse<PublicSchoolSummary>>('/schools');
}

export async function registerStudent(
  request: StudentRegistrationRequest
): Promise<StudentRegistrationResponse> {
  return apiRequest<StudentRegistrationResponse>('/auth/student/register', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}
