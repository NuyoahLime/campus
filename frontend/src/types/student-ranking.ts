import type { PageResponse } from './api';

export type StudentRankingAvailability =
  | 'CURRENT'
  | 'NOT_PUBLISHED'
  | 'WITHDRAWN'
  | 'DISABLED';

export type StudentRankingTiePolicy =
  | 'COMPETITION'
  | 'EARLIER_BUSINESS_TIME';

export interface StudentRankingProjectItem {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  schoolId: string;
  schoolName: string;
  executionStatus: string;
  projectId: string;
  projectName: string;
  scoreStorageType: string;
  scoreUnit: string | null;
  comparisonDirection: string;
  rankingAvailability: StudentRankingAvailability;
  currentVersionNumber: number | null;
  publishedAt: string | null;
  totalRanked: number | null;
  myRank: number | null;
  myScoreDisplayValue: string | null;
}

export interface StudentRankingEntry {
  rankPosition: number;
  studentDisplayName: string;
  scoreDisplayValue: string;
  isCurrentStudent: boolean;
}

export interface StudentCurrentRankingDetail {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  schoolName: string;
  projectId: string;
  projectName: string;
  scoreStorageType: string;
  scoreUnit: string | null;
  comparisonDirection: string;
  effectiveScoreRule: string;
  tiePolicy: StudentRankingTiePolicy;
  versionNumber: number;
  publishedAt: string;
  totalRanked: number;
  myRank: number | null;
  myScoreDisplayValue: string | null;
  entries: StudentRankingEntry[];
}

export interface StudentOwnRanking {
  activityProjectId: string;
  versionNumber: number;
  rankPosition: number;
  scoreDisplayValue: string;
  totalRanked: number;
  publishedAt: string;
}

export interface StudentRankingFilter {
  executionStatus?: string;
  rankingAvailability?: StudentRankingAvailability | '';
  keyword?: string;
}

export type StudentRankingProjectPage = PageResponse<StudentRankingProjectItem>;
