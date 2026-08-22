import type { PageResponse } from './schoolGovernance';

export type RankingViewMode = 'public' | 'student' | 'school-admin';

export interface RankingSummary {
  id: string;
  name: string;
  layer: string;
  schoolId: string | null;
  schoolName: string | null;
  projectId: string;
  projectName: string;
  versionNumber: number;
  publishedAt: string | null;
}

export interface RankingEntry {
  rankPosition: number;
  studentDisplayName: string;
  schoolName: string | null;
  scoreDisplayValue: string;
}

export interface RankingDetail extends RankingSummary {
  entries: RankingEntry[];
}

export type RankingPage = PageResponse<RankingSummary>;
