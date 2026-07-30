export type ScoreAttemptStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'INVALIDATED';

export type ScoreStorageType = 'INTEGER' | 'DECIMAL' | 'DURATION' | 'GRADE';
export type EffectiveScoreRule = 'BEST' | 'LAST' | 'ADMIN_DESIGNATED';

export interface ScoreReviewHistoryItem {
  reviewRecordId: string;
  reviewerId: string;
  reviewerName: string;
  reviewResult: 'APPROVED' | 'REJECTED';
  reviewComment: string | null;
  rejectReason: string | null;
  reviewedAt: string;
}

export interface SchoolAdminScoreAttemptItem {
  attemptId: string;
  schoolId: string;
  activityId: string;
  activityTitle: string;
  activityProjectId: string;
  projectId: string;
  projectName: string;
  studentId: string;
  studentName: string;
  attemptNumber: number;
  scoreStorageType: ScoreStorageType;
  displayValue: string;
  scoreUnit: string | null;
  scoreBusinessTime: string | null;
  timeSource: string | null;
  status: ScoreAttemptStatus;
  currentEffective: boolean;
  enteredBy: string;
  enteredByName: string;
  submittedAt: string | null;
  createdAt: string;
  effectiveScoreRule: EffectiveScoreRule;
  comparisonDirection: string;
}

export interface SchoolAdminScoreAttemptDetail extends SchoolAdminScoreAttemptItem {
  integerValue: number | null;
  decimalValue: number | null;
  durationMs: number | null;
  grade: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  allowTie: boolean;
  reviewHistory: ScoreReviewHistoryItem[];
}

export interface ScoreReviewListFilter {
  status?: ScoreAttemptStatus;
  activityId?: string;
  projectId?: string;
  keyword?: string;
}

export interface ApproveScorePayload {
  reviewComment?: string | null;
  makeCurrentEffective?: boolean | null;
}

export interface RejectScorePayload {
  rejectReason: string;
  reviewComment?: string | null;
}
