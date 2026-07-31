import type { PageResponse } from '@/types/api';

export type AchievementStatus = 'ACTIVE' | 'REVOKED';

export interface StudentAchievementItem {
  recordId: string;
  recordTitle: string;
  schoolName: string;
  activityTitle: string;
  projectName: string;
  rankingVersionNumber: number;
  rankPosition: number;
  scoreDisplayValue: string;
  scoreStorageType: string;
  verificationCode: string;
  status: AchievementStatus;
  issuedAt: string;
  revokedAt: string | null;
}

export interface StudentAchievementDetail extends StudentAchievementItem {
  rankingVersionId: string;
  activityProjectId: string;
  revocationReason: string | null;
}

export interface StudentAchievementFilter {
  status?: AchievementStatus | '';
  keyword?: string;
}

export interface PublicAchievementVerification {
  valid: boolean;
  status: AchievementStatus;
  recordTitle: string;
  schoolName: string;
  activityTitle: string;
  projectName: string;
  rankingVersionNumber: number;
  rankPosition: number;
  scoreDisplayValue: string;
  scoreStorageType: string;
  issuedAt: string;
  revokedAt: string | null;
}

export interface SchoolAdminAchievementStatus {
  rankingEntryId: string;
  achievementRecordId: string | null;
  achievementStatus: AchievementStatus | null;
  verificationCode: string | null;
  issuedAt: string | null;
}

export interface SchoolAdminAchievementDetail {
  recordId: string;
  activityProjectId: string;
  rankingVersionId: string;
  rankingVersionNumber: number;
  rankingEntryId: string;
  studentId: string;
  studentDisplayName: string;
  schoolName: string;
  activityTitle: string;
  projectName: string;
  rankPosition: number;
  scoreDisplayValue: string;
  scoreStorageType: string;
  recordTitle: string;
  verificationCode: string;
  status: AchievementStatus;
  issuedAt: string;
  issuedBy: string;
  issuedByName: string;
  revokedAt: string | null;
  revokedBy: string | null;
  revocationReason: string | null;
  created: boolean;
}

export type StudentAchievementPage = PageResponse<StudentAchievementItem>;
