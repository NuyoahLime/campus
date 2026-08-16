import { apiRequest } from './http';
import type {
  GovernanceSchoolDetail,
  GovernanceSchoolListItem,
  InvitationCommandResponse,
  InvitationStatus,
  PageResponse,
  SchoolAdminAccount,
  SchoolAdminInvitation,
  SchoolStatus
} from '../types/schoolGovernance';

export async function listGovernanceSchools(
  page = 0,
  size = 20,
  status: SchoolStatus | null = null,
  search = ''
): Promise<PageResponse<GovernanceSchoolListItem>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  if (search.trim()) query.set('q', search.trim());
  return apiRequest<PageResponse<GovernanceSchoolListItem>>(`/schools/governance?${query}`);
}

export async function getGovernanceSchool(schoolId: string): Promise<GovernanceSchoolDetail> {
  return apiRequest<GovernanceSchoolDetail>(`/schools/${encodeURIComponent(schoolId)}`);
}

export async function listSchoolAdmins(schoolId: string): Promise<SchoolAdminAccount[]> {
  return apiRequest<SchoolAdminAccount[]>(
    `/schools/${encodeURIComponent(schoolId)}/school-admins`
  );
}

export async function listSchoolAdminInvitations(
  schoolId: string,
  page = 0,
  size = 20,
  status: InvitationStatus | null = null
): Promise<PageResponse<SchoolAdminInvitation>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set('status', status);
  return apiRequest<PageResponse<SchoolAdminInvitation>>(
    `/schools/${encodeURIComponent(schoolId)}/school-admin-invitations?${query}`
  );
}

export async function createSchoolAdminInvitation(
  schoolId: string,
  username: string,
  expiresAt: string | null
): Promise<InvitationCommandResponse> {
  return apiRequest<InvitationCommandResponse>('/school-admin-invitations', {
    method: 'POST',
    body: JSON.stringify({
      username: username.trim(),
      schoolId,
      ...(expiresAt ? { expiresAt } : {})
    })
  });
}

export async function revokeSchoolAdminInvitation(invitationId: string): Promise<void> {
  await apiRequest<null>(
    `/school-admin-invitations/${encodeURIComponent(invitationId)}/revoke`,
    { method: 'POST' }
  );
}

export async function regenerateSchoolAdminInvitation(
  invitationId: string
): Promise<InvitationCommandResponse> {
  return apiRequest<InvitationCommandResponse>(
    `/school-admin-invitations/${encodeURIComponent(invitationId)}/regenerate`,
    { method: 'POST' }
  );
}
