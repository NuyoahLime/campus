import type { PageResponse } from './schoolGovernance';

export type L3AuthorizationStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'WITHDRAWN';

export interface L3Authorization {
  id: string;
  schoolId: string | null;
  schoolName: string | null;
  projectId: string | null;
  projectName: string | null;
  ruleVersionId: string | null;
  ruleVersionNumber: number | null;
  dataScope: string | null;
  allowSchoolName: boolean;
  allowStudentName: boolean;
  status: L3AuthorizationStatus;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  rejectReason: string | null;
  pausedAt: string | null;
  withdrawnAt: string | null;
  withdrawReason: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface L3AuthorizationForm {
  projectId: string;
  ruleVersionId: string;
  dataScope: Record<string, unknown>;
  allowSchoolName: boolean;
  allowStudentName: boolean;
}

export type L3AuthorizationPage = PageResponse<L3Authorization>;
