import type { ScoreAttemptStatus, ScoreStorageType } from './school-admin-score-review';

export type ActivityExecutionStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'IN_PROGRESS'
  | 'ENDED'
  | 'CANCELLED';

export type TeacherParticipantScoreStatus = ScoreAttemptStatus | 'NO_SCORE';

export interface TeacherResponsibleProjectItem {
  activityProjectId: string;
  activityId: string;
  activityTitle: string;
  schoolId: string;
  schoolName: string;
  executionStatus: ActivityExecutionStatus;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  projectId: string;
  projectName: string;
  category: string;
  scoreStorageType: ScoreStorageType;
  scoreUnit: string | null;
  decimalPlaces: number | null;
  gradeOrder: string | null;
  comparisonDirection: string;
  effectiveScoreRule: string;
  participantCount: number;
  enteredAttemptCount: number;
  pendingReviewCount: number;
  rejectedCount: number;
}

export interface TeacherResponsibleTeacher {
  userId: string;
  username: string;
  subject: string | null;
  title: string | null;
}

export interface TeacherResponsibleProjectDetail extends TeacherResponsibleProjectItem {
  activityDescription: string | null;
  projectDescription: string | null;
  rulesText: string | null;
  venueRequirements: string | null;
  equipmentRequirements: string | null;
  allowTie: boolean;
  responsibleTeachers: TeacherResponsibleTeacher[];
}

export interface TeacherProjectParticipantItem {
  studentId: string;
  displayName: string;
  studentNumber: string | null;
  grade: string | null;
  className: string | null;
  attemptCount: number;
  latestAttemptId: string | null;
  latestAttemptNumber: number | null;
  latestAttemptStatus: ScoreAttemptStatus | null;
  latestScoreValue: string | null;
  hasApprovedScore: boolean;
  assignedAt: string;
}

export interface TeacherResponsibleProjectFilter {
  executionStatus?: ActivityExecutionStatus;
  keyword?: string;
}

export interface TeacherProjectParticipantFilter {
  keyword?: string;
  status?: TeacherParticipantScoreStatus;
}
