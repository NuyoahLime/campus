import type { PageResponse } from './schoolGovernance';
import type { RankingEntry } from './ranking';

export type SchoolRankingManagementLayer = 'L1' | 'L2';
export type RankingManagementLayer = SchoolRankingManagementLayer | 'L3';

export interface RankingManagementVersion {
  id: string;
  versionNumber: number;
  status: string;
  generatedAt: string | null;
  publishedAt: string | null;
  entryCount: number;
  entries: RankingEntry[];
}

export interface RankingManagementDefinition {
  id: string;
  name: string;
  layer: RankingManagementLayer;
  enabled: boolean;
  schoolId: string | null;
  schoolName: string | null;
  projectId: string;
  projectName: string;
  ruleVersionId: string | null;
  ruleVersionNumber: number | null;
  activityId: string | null;
  activityTitle: string | null;
  activityProjectId: string | null;
  dimensionFilters: string | null;
  selectionPolicy: string | null;
  grade: string | null;
  className: string | null;
  activityPeriodStart: string | null;
  activityPeriodEnd: string | null;
  latestGeneratedVersion: RankingManagementVersion | null;
  currentPublishedVersion: RankingManagementVersion | null;
}

export interface RankingDefinitionCreateForm {
  layer: SchoolRankingManagementLayer;
  name: string;
  projectId: string;
  activityProjectId?: string;
  dimensionFilters?: string;
}

export interface RankingGenerationResult {
  rankingDefinitionId: string;
  rankingVersionId: string;
  versionNumber: number;
  entryCount: number;
  status: string;
  generatedAt: string;
}

export interface RankingPublicationResult {
  rankingDefinitionId: string;
  rankingVersionId: string;
  previousCurrentVersionId: string | null;
  currentVersionId: string;
  status: string;
  publishedAt: string;
}

export type RankingManagementPage = PageResponse<RankingManagementDefinition>;

export type L3RankingManagementDefinition = Omit<RankingManagementDefinition, 'layer'> & {
  layer: 'L3';
};

export type L3RankingManagementPage = PageResponse<L3RankingManagementDefinition>;

export interface L3RankingDefinitionCreateForm {
  name: string;
  projectId: string;
  ruleVersionId: string;
}
