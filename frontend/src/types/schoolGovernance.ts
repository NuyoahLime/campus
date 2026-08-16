export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type SchoolStatus = 'PENDING_ENABLE' | 'NORMAL' | 'SUSPENDED' | 'DISABLED';
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED' | 'EXPIRED';

export interface GovernanceSchoolListItem {
  id: string;
  name: string;
  status: SchoolStatus;
  schoolType: string;
  region: string;
  internalCode: string;
  unifiedCodeType: string;
  unifiedCode: string | null;
  normalActiveSchoolAdminCount: number;
}

export interface GovernanceSchoolDetail extends GovernanceSchoolListItem {
  address: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  createdAt: string;
  updatedAt: string;
}

export interface SchoolAdminAccount {
  userId: string;
  username: string;
  accountStatus: string;
  membershipStatus: string;
  startedAt: string;
  lockedUntil: string | null;
}

export interface SchoolAdminInvitation {
  invitationId: string;
  userId: string;
  username: string;
  schoolId: string;
  status: InvitationStatus;
  expiresAt: string;
  acceptedAt: string | null;
  revokedAt: string | null;
  createdAt: string;
  expired: boolean;
}

export interface InvitationCommandResponse {
  userId: string;
  invitationId: string;
  username: string;
  schoolId: string;
  invitationCode: string;
  expiresAt: string;
  status: InvitationStatus;
}
