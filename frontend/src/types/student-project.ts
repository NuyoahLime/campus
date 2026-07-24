export interface StudentProjectItem {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  projectId: string;
  projectName: string;
  category: string;
  scoreStorageType: string;
  comparisonDirection: string;
  scoreUnit: string | null;
  attemptCount: number;
  latestAttemptId: string | null;
  latestAttemptStatus: string | null;
  latestScoreDisplay: string | null;
  hasApprovedScore: boolean;
  assignedAt: string;
}

export interface StudentProjectDetail extends StudentProjectItem {
  activityDescription: string | null;
  activityStartTime: string | null;
  activityEndTime: string | null;
  location: string | null;
  projectDescription: string | null;
  rulesText: string | null;
  venueRequirements: string | null;
  equipmentRequirements: string | null;
  effectiveScoreRule: string | null;
  allowTie: boolean;
  decimalPlaces: number | null;
  gradeOrder: string | null;
}

export interface StudentProjectFilter {
  executionStatus?: string;
  scoreStatus?: string;
  keyword?: string;
}
