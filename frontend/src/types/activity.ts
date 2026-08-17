import type { PageResponse } from './schoolGovernance';

export type ActivityExecutionStatus = 'PUBLISHED' | 'IN_PROGRESS' | 'ENDED';

export interface ActivityListItem {
  id: string;
  schoolId: string;
  schoolName: string;
  schoolRegion: string;
  title: string;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  executionStatus: ActivityExecutionStatus;
}

export interface ActivityProject {
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

export interface ActivityDetail extends ActivityListItem {
  description: string | null;
  projects: ActivityProject[];
}

export type ActivityPage = PageResponse<ActivityListItem>;
