import type { PageResponse } from './schoolGovernance';

export interface ActivityManagementListItem {
  id: string;
  title: string;
  projectName: string | null;
  ruleVersionNumber: number | null;
  executionStatus: string;
  publicStatus: string;
  startTime: string | null;
  endTime: string | null;
  updatedAt: string;
}

export interface ActivityProjectSnapshot {
  projectId: string;
  projectName: string;
  category: string;
  ruleVersionId: string;
  ruleVersionNumber: number;
  rulesText: string | null;
  scoreStorageType: string;
  scoreIndicatorType: string;
  comparisonDirection: string;
  scoreUnit: string | null;
  allowTie: boolean;
}

export interface ActivityManagementDetail {
  id: string;
  schoolId: string;
  schoolName: string;
  title: string;
  description: string | null;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  executionStatus: string;
  publicStatus: string;
  createdAt: string;
  updatedAt: string;
  projects: ActivityProjectSnapshot[];
}

export interface ActivityManagementForm {
  projectId: string;
  title: string;
  description: string;
  startTime: string;
  endTime: string;
  location: string;
}

export type ActivityManagementPage = PageResponse<ActivityManagementListItem>;
