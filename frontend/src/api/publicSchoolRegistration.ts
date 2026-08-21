import { apiRequest } from './http';
import type {
  PublicSchoolRegistrationRequest,
  PublicSchoolRegistrationResponse
} from '../types/publicSchoolRegistration';

export function submitPublicSchoolRegistration(
  request: PublicSchoolRegistrationRequest
): Promise<PublicSchoolRegistrationResponse> {
  return apiRequest<PublicSchoolRegistrationResponse>('/school-registrations', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}
