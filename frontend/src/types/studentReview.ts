export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface StudentIdentityApplicationSummary {
  applicationId: string;
  userId: string;
  schoolId: string;
  username: string;
  realName: string;
  studentNumber: string;
  grade: string;
  className: string;
  applicationStatus: string;
  submittedAt: string;
  reviewedAt: string | null;
}

export interface StudentIdentityApplicationDetail extends StudentIdentityApplicationSummary {
  reviewerId: string | null;
  reviewReason: string | null;
  proofFileCount: number;
  proofFileKeys: string[];
}

export interface StudentIdentityApplicationReviewResult {
  applicationId: string;
  userId: string;
  schoolId: string;
  applicationStatus: string;
  accountStatus: string;
  membershipRole: string | null;
  membershipStatus: string | null;
  reason: string | null;
  reviewedAt: string;
}
