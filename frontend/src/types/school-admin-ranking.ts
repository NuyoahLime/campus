import type { PageResponse } from '@/types/api';

export type RankingExecutionStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'IN_PROGRESS'
  | 'ENDED'
  | 'CANCELLED';

export type RankingStatus =
  | 'NOT_PUBLISHED'
  | 'CURRENT'
  | 'WITHDRAWN'
  | 'DISABLED';

export type RankingVersionStatus =
  | 'DRAFT_CALC'
  | 'GENERATED'
  | 'PUBLISHED'
  | 'WITHDRAWN'
  | 'EXPIRED'
  | 'REPLACED'
  | 'VOIDED';

export type TiePolicy = 'COMPETITION' | 'EARLIER_BUSINESS_TIME';
export type RankingScoreStorageType = 'INTEGER' | 'DECIMAL' | 'DURATION' | 'GRADE';
export type RankingComparisonDirection =
  | 'HIGHER_BETTER'
  | 'LOWER_BETTER'
  | 'GRADE_ORDER'
  | 'NO_RANKING';
export type RankingEffectiveScoreRule = 'BEST' | 'LAST' | 'ADMIN_DESIGNATED';

export interface RankingProjectItem {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  executionStatus: RankingExecutionStatus;
  projectId: string;
  projectName: string;
  scoreStorageType: RankingScoreStorageType;
  scoreUnit: string | null;
  comparisonDirection: RankingComparisonDirection;
  effectiveScoreRule: RankingEffectiveScoreRule;
  allowTie: boolean;
  approvedEffectiveScoreCount: number;
  pendingReviewCount: number;
  rankingStatus: RankingStatus;
  currentVersionId: string | null;
  currentVersionNumber: number | null;
  currentVersionEntryCount: number | null;
  currentPublishedAt: string | null;
  lastVersionStatus: RankingVersionStatus | null;
  canPreview: boolean;
  canPublish: boolean;
}

export interface RankingProjectDetail extends RankingProjectItem {
  activityStartTime: string | null;
  activityEndTime: string | null;
  location: string | null;
  projectDescription: string | null;
  rulesText: string | null;
  gradeOrder: string | null;
  decimalPlaces: number | null;
  currentRuleVersionId: string | null;
  lastPublishedBy: string | null;
  lastPublishedByName: string | null;
  lastWithdrawalReason: string | null;
}

export interface RankingEntryItem {
  rankPosition: number;
  studentId: string;
  studentDisplayName: string;
  schoolName: string | null;
  scoreDisplayValue: string;
  scoreAttemptId: string;
  scoreBusinessTime: string | null;
}

export interface RankingPreviewResult {
  activityProjectId: string;
  activityTitle: string;
  projectName: string;
  scoreStorageType: RankingScoreStorageType;
  scoreUnit: string | null;
  comparisonDirection: RankingComparisonDirection;
  effectiveScoreRule: RankingEffectiveScoreRule;
  tiePolicy: TiePolicy;
  sourceFingerprint: string;
  totalRanked: number;
  pendingReviewCount: number;
  publishable: boolean;
  warnings: string[];
  entries: RankingEntryItem[];
}

export interface RankingVersionSummary {
  versionId: string;
  versionNumber: number;
  versionStatus: RankingVersionStatus;
  entryCount: number;
  publishedBy: string | null;
  publishedByName: string | null;
  publishedAt: string | null;
  withdrawnBy: string | null;
  withdrawnByName: string | null;
  withdrawnAt: string | null;
  withdrawalReason: string | null;
  createdReason: string | null;
}

export interface RankingVersionDetail extends RankingVersionSummary {
  activityProjectId: string;
  activityTitle: string;
  projectName: string;
  scoreStorageType: RankingScoreStorageType;
  scoreUnit: string | null;
  comparisonDirection: RankingComparisonDirection;
  effectiveScoreRule: RankingEffectiveScoreRule;
  tiePolicy: TiePolicy;
  gradeOrder: string | null;
  allowTie: boolean;
  decimalPlaces: number | null;
  currentRuleVersionId: string | null;
  sourceFingerprint: string;
  entries: RankingEntryItem[];
}

export interface RankingProjectFilter {
  executionStatus?: RankingExecutionStatus;
  rankingStatus?: RankingStatus;
  keyword?: string;
}

export interface PublishRankingPayload {
  expectedSourceFingerprint: string;
}

export interface WithdrawRankingPayload {
  reason: string;
}

export type RankingProjectPage = PageResponse<RankingProjectItem>;
export type RankingVersionPage = PageResponse<RankingVersionSummary>;
