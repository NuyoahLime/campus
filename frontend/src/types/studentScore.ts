import type { PageResponse } from './schoolGovernance';

export interface StudentScore {
  scoreAttemptId: string;
  activityProjectId: string;
  activityId: string;
  activityName: string;
  challengeProjectName: string;
  attemptNumber: number;
  scoreStorageType: string;
  scoreValue: string | null;
  scoreUnit: string | null;
  scoreBusinessTime: string | null;
  status: string;
}

export interface StudentScoreDetail extends StudentScore {
  ruleVersionId: string;
  ruleVersionNumber: number;
  rulesText: string | null;
}

export type StudentScorePage = PageResponse<StudentScore>;
