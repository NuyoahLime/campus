import type { SchoolAdminScoreAttemptItem, ScoreAttemptStatus, ScoreStorageType } from './school-admin-score-review';

export interface ScoreEntryProjectOption {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  executionStatus: string;
  projectId: string;
  projectName: string;
  scoreStorageType: ScoreStorageType;
  scoreUnit: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  comparisonDirection: string;
  effectiveScoreRule: string;
}

export interface ScoreEntryParticipantOption {
  studentId: string;
  displayName: string;
  studentNumber: string | null;
  grade: string | null;
  className: string | null;
  attemptCount: number;
  latestAttemptNumber: number | null;
  latestAttemptStatus: ScoreAttemptStatus | null;
  latestScoreValue: string | null;
}

export interface ScoreDraftValuePayload {
  integerValue?: number | null;
  decimalValue?: number | null;
  durationMs?: number | null;
  grade?: string | null;
  scoreBusinessTime: string;
  timeSource: string;
}

export interface CreateScoreDraftPayload extends ScoreDraftValuePayload {
  activityProjectId: string;
  studentId: string;
}

export type UpdateScoreDraftPayload = ScoreDraftValuePayload;

export interface SchoolAdminScoreEntryFilter {
  status?: ScoreAttemptStatus;
  activityId?: string;
  projectId?: string;
  keyword?: string;
}

export type SchoolAdminScoreEntryItem = SchoolAdminScoreAttemptItem;
