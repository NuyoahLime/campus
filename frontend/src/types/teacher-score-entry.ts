import type {
  ScoreAttemptStatus,
  ScoreReviewHistoryItem,
  ScoreStorageType,
} from './school-admin-score-review';

export interface TeacherScoreAttemptItem {
  attemptId: string;
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  schoolId: string;
  schoolName: string;
  projectId: string;
  projectName: string;
  studentId: string;
  studentName: string;
  attemptNumber: number;
  scoreStorageType: ScoreStorageType;
  displayValue: string;
  scoreUnit: string | null;
  scoreBusinessTime: string;
  timeSource: string;
  status: ScoreAttemptStatus;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
  currentEffective: boolean;
}

export interface TeacherScoreAttemptDetail extends TeacherScoreAttemptItem {
  integerValue: number | null;
  decimalValue: number | null;
  durationMs: number | null;
  grade: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  reviewHistory: ScoreReviewHistoryItem[];
}

export interface TeacherScoreValuePayload {
  integerValue?: number | null;
  decimalValue?: number | null;
  durationMs?: number | null;
  grade?: string | null;
  scoreBusinessTime: string;
  timeSource: string;
}

export interface CreateTeacherScorePayload extends TeacherScoreValuePayload {
  activityProjectId: string;
  studentId: string;
}

export type UpdateTeacherScorePayload = TeacherScoreValuePayload;

export interface TeacherScoreFilter {
  status?: ScoreAttemptStatus;
  activityProjectId?: string;
  keyword?: string;
}
