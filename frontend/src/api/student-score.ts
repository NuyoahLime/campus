import http from './http';
import type { PageResponse } from '@/types/api';
import type { StudentScoreItem, StudentScoreDetail } from '@/types/student-score';

export async function fetchMyScores(
  params: {
    status?: string;
    activityId?: string;
    projectId?: string;
    page?: number;
    size?: number;
  } = {},
): Promise<PageResponse<StudentScoreItem>> {
  const res = await http.get<PageResponse<StudentScoreItem>>('/v1/student/scores', { params });
  return res.data;
}

export async function fetchMyScoreById(attemptId: string): Promise<StudentScoreDetail> {
  const res = await http.get<StudentScoreDetail>(`/v1/student/scores/${attemptId}`);
  return res.data;
}
