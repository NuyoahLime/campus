import { apiRequest } from './http';
import type { StudentFeedback, StudentFeedbackPage, SubmitStudentFeedbackRequest } from '../types/studentFeedback';

interface FeedbackMutationResponse {
  id: string;
  status: string;
}

export function listStudentFeedback(page = 0, size = 20) {
  return apiRequest<StudentFeedbackPage>(`/student/feedback?page=${page}&size=${size}`);
}

export function getStudentFeedback(id: string) {
  return apiRequest<StudentFeedback>(`/student/feedback/${encodeURIComponent(id)}`);
}

export function submitStudentFeedback(payload: SubmitStudentFeedbackRequest) {
  return apiRequest<FeedbackMutationResponse>('/student/feedback', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

export function closeStudentFeedback(id: string, reason: string) {
  return apiRequest<FeedbackMutationResponse>(`/student/feedback/${encodeURIComponent(id)}/close`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}
