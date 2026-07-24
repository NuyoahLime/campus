export interface StudentScoreItem {
  attemptId: string;
  activityId: string;
  activityTitle: string;
  activityProjectId: string;
  projectId: string;
  projectName: string;
  attemptNumber: number;
  scoreStorageType: string;
  scoreDisplay: string;
  status: string;
  isCurrentEffective: boolean;
  scoreBusinessTime: string | null;
  submittedAt: string | null;
  createdAt: string;
}

export interface StudentScoreDetail extends StudentScoreItem {
  scoreValue: string | null;
  scoreDurationMs: number | null;
  scoreGrade: string | null;
  timeSource: string | null;
  enteredByDisplayName: string | null;
  reviewComment: string | null;
  rejectReason: string | null;
  reviewedAt: string | null;
}
