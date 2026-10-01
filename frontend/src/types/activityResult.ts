import type { PageResponse } from './schoolGovernance';

export type ActivityResultInternalStatus = 'DRAFT' | 'INTERNAL_PUBLISHED' | 'INTERNAL_WITHDRAWN' | string;
export type ActivityResultPublicStatus =
  | 'NOT_SUBMITTED'
  | 'PENDING_PUBLIC_REVIEW'
  | 'PLATFORM_APPROVED'
  | 'PLATFORM_REJECTED'
  | 'PUBLIC'
  | 'PLATFORM_TAKEDOWN'
  | 'ANOMALY_PENDING'
  | string;

export interface ActivityResultVersionProjection {
  versionId: string;
  versionNumber: number;
  title: string;
  summaryText: string;
  scoreHighlights: string[];
  mediaRefs: string[];
  publishedInternallyAt: string | null;
  publishedPubliclyAt: string | null;
}

export interface ActivityResultEditor {
  activityId: string;
  resultId: string | null;
  internalStatus: ActivityResultInternalStatus;
  publicStatus: ActivityResultPublicStatus;
  currentCandidateVersionId: string | null;
  currentInternalVersionId: string | null;
  currentPublicVersionId: string | null;
  publicVisibilityBlocked: boolean;
  candidateProjection: ActivityResultVersionProjection | null;
  internalProjection: ActivityResultVersionProjection | null;
  publicProjection: ActivityResultVersionProjection | null;
}

export interface ActivityResultSavePayload {
  title: string;
  summaryText: string;
  scoreHighlights: string[];
  mediaRefs: string[];
}

export interface ActivityResultMutationResponse {
  id: string;
  internalStatus: ActivityResultInternalStatus;
  publicStatus: ActivityResultPublicStatus;
}

export interface ActivityResultHistoryEntry {
  id: string;
  action: string;
  resultVersionId: string;
  occurredAt: string;
  reason: string | null;
  actorId: string | null;
}

export interface PublicActivityResult {
  activityId: string;
  resultId: string;
  versionId: string;
  versionNumber: number;
  title: string;
  summaryText: string;
  scoreHighlights: string[];
  publishedPubliclyAt: string;
}

export interface StudentActivityResult extends PublicActivityResult {
  visibilitySource: string;
  publishedInternallyAt: string | null;
}

export interface PendingResultReviewSummary {
  resultId: string;
  schoolId: string;
  activityId: string;
  schoolName: string;
  activityTitle: string;
  activityExecutionStatus: string;
  candidateVersionId: string;
  candidateVersionNumber: number;
  candidateTitle: string;
  submittedAt: string;
  submittedBy: string;
  publicStatus: string;
}

export interface ResultReviewHistoryEntry {
  id: string;
  resultVersionId: string;
  action: string;
  submittedBy: string | null;
  submittedAt: string | null;
  reviewerId: string | null;
  reviewedAt: string | null;
  reason: string | null;
  createdAt: string;
}

export interface PendingResultReviewDetail {
  resultId: string;
  schoolId: string;
  activityId: string;
  schoolName: string | null;
  activityTitle: string | null;
  activityExecutionStatus: string | null;
  candidateVersionId: string;
  candidateVersionNumber: number;
  candidateTitle: string;
  summaryText: string;
  scoreHighlights: string[];
  mediaRefs: string[];
  submittedAt: string;
  submittedBy: string;
  publicStatus: string;
  history: ResultReviewHistoryEntry[];
}

export interface GovernanceResultSummary {
  resultId: string;
  schoolId: string;
  activityId: string;
  schoolName: string;
  activityTitle: string;
  activityExecutionStatus: string;
  internalStatus: string;
  publicStatus: string;
  publicVisibilityBlocked: boolean;
  currentPublicVersionId: string | null;
  updatedAt: string;
}

export interface GovernanceResultDetail {
  resultId: string;
  schoolId: string;
  activityId: string;
  schoolName: string;
  activityTitle: string;
  activityExecutionStatus: string;
  internalStatus: string;
  publicStatus: string;
  publicVisibilityBlocked: boolean;
  currentCandidateVersionId: string | null;
  currentInternalVersionId: string | null;
  currentPublicVersionId: string;
  currentPublicVersion: ActivityResultVersionProjection;
  governanceHistory: GovernanceHistoryEntry[];
  updatedAt: string;
}

export interface GovernanceHistoryEntry {
  id: string;
  resultVersionId: string;
  action: string;
  reviewerId: string;
  reviewedAt: string;
  reason: string;
  createdAt: string;
}

export type ResultPage<T> = PageResponse<T>;
