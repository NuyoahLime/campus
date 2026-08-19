import { apiRequest } from './http';
import type { StudentAppeal, StudentAppealPage, SubmitStudentAppealRequest } from '../types/studentAppeal';

interface AppealMutationResponse {
  id: string;
  status: string;
}

export function listStudentAppeals(page = 0, size = 20) {
  return apiRequest<StudentAppealPage>(`/student/appeals?page=${page}&size=${size}`);
}

export function getStudentAppeal(id: string) {
  return apiRequest<StudentAppeal>(`/student/appeals/${encodeURIComponent(id)}`);
}

export function submitStudentAppeal(payload: SubmitStudentAppealRequest) {
  return apiRequest<AppealMutationResponse>('/student/appeals', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function withdrawStudentAppeal(id: string) {
  return apiRequest<AppealMutationResponse>(`/student/appeals/${encodeURIComponent(id)}/withdraw`, {
    method: 'POST'
  });
}
