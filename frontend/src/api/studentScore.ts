import { apiRequest } from './http';
import type { StudentScoreDetail, StudentScorePage } from '../types/studentScore';

export function listStudentScores(page = 0, size = 20) {
  return apiRequest<StudentScorePage>(`/student/scores?page=${page}&size=${size}`);
}

export function getStudentScore(id: string) {
  return apiRequest<StudentScoreDetail>(`/student/scores/${encodeURIComponent(id)}`);
}
