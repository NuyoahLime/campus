import type { PageResponse } from './schoolGovernance';

export interface StudentAppeal {
  appealId: string;
  scoreAttemptId: string;
  activityName: string;
  challengeProjectName: string;
  scoreStorageType: string | null;
  scoreValue: string | null;
  scoreUnit: string | null;
  appealType: string;
  appealReason: string | null;
  status: string;
  resolution: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitStudentAppealRequest {
  scoreAttemptId: string;
  appealType: string;
  appealReason: string;
}

export type StudentAppealPage = PageResponse<StudentAppeal>;
