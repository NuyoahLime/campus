import type { PageResponse } from './schoolGovernance';
import type { RankingEntry } from './ranking';

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
  layer: 'L1';
  enabled: boolean;
  schoolId: string;
  schoolName: string;
  projectId: string;
  projectName: string;
  activityId: string | null;
  activityTitle: string | null;
  activityProjectId: string | null;
  latestGeneratedVersion: RankingManagementVersion | null;
  currentPublishedVersion: RankingManagementVersion | null;
}

export interface RankingDefinitionCreateForm {
  name: string;
  projectId: string;
  activityProjectId: string;
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
