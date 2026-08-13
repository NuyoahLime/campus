export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type SchoolRegistrationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'NEED_SUPPLEMENT'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN';

export interface SchoolRegistrationListItem {
  id: string;
  schoolName: string;
  schoolType: string;
  region: string;
  contactName: string;
  status: SchoolRegistrationStatus;
  createdAt: string;
}

export interface SchoolRegistrationDetail extends SchoolRegistrationListItem {
  unifiedCodeType: string;
  unifiedCode: string | null;
  address: string;
  contactPhone: string;
  contactEmail: string;
  description: string | null;
  evidenceSubmitted: boolean;
  createdSchoolId: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  rejectReason: string | null;
  updatedAt: string;
}
