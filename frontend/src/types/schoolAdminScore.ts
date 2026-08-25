interface SchoolAdminScoreBase {
  scoreAttemptId: string;
  activityId: string;
  activityTitle: string;
  activityProjectId: string;
  projectName: string;
  studentId: string;
  studentDisplay: string | null;
  studentNumber: string | null;
  attemptNumber: number;
  status: string;
  scoreStorageType: string;
  scoreBusinessTime: string | null;
}

export interface SchoolAdminScoreCandidateProject {
  activityProjectId: string;
  projectName: string;
  scoreStorageType: string;
  latestAttemptId: string | null;
  latestAttemptNumber: number | null;
  latestStatus: string | null;
}

export interface SchoolAdminScoreCandidate {
  studentId: string;
  studentDisplay: string;
  studentNumber: string | null;
  projects: SchoolAdminScoreCandidateProject[];
}

export interface SchoolAdminScoreListItem extends SchoolAdminScoreBase {
  integerValue: number | null;
  decimalValue: string | null;
  durationMs: number | null;
  grade: string | null;
}

export interface SchoolAdminScoresResponse {
  activityId: string;
  activityTitle: string;
  activityStatus: string;
  scores: SchoolAdminScoreListItem[];
}

export interface SchoolAdminScoreCandidatesResponse {
  activityId: string;
  activityTitle: string;
  activityStatus: string;
  candidates: SchoolAdminScoreCandidate[];
}

export interface SchoolAdminScoreDraftRequest {
  studentId: string;
  integerValue?: number | null;
  decimalValue?: string | number | null;
  durationMs?: number | null;
  grade?: string | null;
  scoreBusinessTime?: string | null;
}

export type SchoolAdminScoreDraftUpdateRequest = Omit<SchoolAdminScoreDraftRequest, 'studentId'>;

export interface SchoolAdminScoreDetail extends SchoolAdminScoreListItem {}
