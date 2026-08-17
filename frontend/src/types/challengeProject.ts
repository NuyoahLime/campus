import type { PageResponse } from './schoolGovernance';

export type ProjectStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface ChallengeProjectListItem {
  id: string;
  name: string;
  category: string;
  scoreStorageType: string;
  comparisonDirection: string;
  projectStatus: ProjectStatus;
  createdAt: string;
}

export interface ChallengeProjectDetail extends ChallengeProjectListItem {
  description: string | null;
  venueRequirements: string | null;
  equipmentRequirements: string | null;
  rulesText: string | null;
  scoreIndicatorType: string;
  scoreUnit: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  allowTie: boolean;
  effectiveScoreRule: string;
  status: ProjectStatus;
  currentRuleVersionId: string | null;
  currentRuleVersionNumber: number | null;
  updatedAt: string;
}

export interface GovernanceProjectListItem {
  id: string;
  name: string;
  category: string;
  status: ProjectStatus;
  scoreStorageType: string;
  scoreIndicatorType: string;
  comparisonDirection: string;
  scoreUnit: string | null;
  currentRuleVersionNumber: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RuleVersion {
  id: string;
  versionNumber: number;
  scoreStorageType: string;
  scoreIndicatorType: string;
  comparisonDirection: string;
  scoreUnit: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  allowTie: boolean;
  effectiveScoreRule: string;
  rulesText: string | null;
  venueRequirements: string | null;
  equipmentRequirements: string | null;
  changeReason: string | null;
  createdBy: string;
  createdAt: string;
}

export interface GovernanceProjectDetail {
  project: ChallengeProjectDetail;
  ruleVersions: RuleVersion[];
}

export interface ProjectForm {
  name: string;
  category: string;
  description: string;
  venueRequirements: string;
  equipmentRequirements: string;
  rulesText: string;
  scoreStorageType: string;
  scoreIndicatorType: string;
  comparisonDirection: string;
  scoreUnit: string;
  decimalPlaces: number | null;
  gradeOrder: string;
  allowTie: boolean;
  effectiveScoreRule: string;
}

export type ProjectPage<T> = PageResponse<T>;
