import { apiRequest } from './http';
export type ScoreDraft = { scoreAttemptId: string; studentId: string; studentDisplay?: string; activityProjectId: string; attemptNumber: number; status: string; integerValue?: number | null; decimalValue?: string | null; durationMs?: number | null; grade?: string | null };
export function getSchoolAdminScores(activityId: string) { return apiRequest<{ activityId: string; activityTitle: string; scores: ScoreDraft[] }>(`/school-admin/activities/${encodeURIComponent(activityId)}/scores`); }
