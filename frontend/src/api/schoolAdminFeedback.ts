import { apiRequest } from './http';
import type { StudentFeedback, StudentFeedbackPage } from '../types/studentFeedback';

interface FeedbackMutationResponse {
  id: string;
  status: string;
}

export function listSchoolAdminFeedback(page = 0, size = 20) {
  return apiRequest<StudentFeedbackPage>(`/school-admin/feedback?page=${page}&size=${size}`);
}

export function getSchoolAdminFeedback(id: string) {
  return apiRequest<StudentFeedback>(`/school-admin/feedback/${encodeURIComponent(id)}`);
}

export function beginSchoolAdminFeedback(id: string) {
  return apiRequest<FeedbackMutationResponse>(`/school-admin/feedback/${encodeURIComponent(id)}/begin-processing`, {
    method: 'POST'
  });
}

export function resolveSchoolAdminFeedback(id: string, reply: string) {
  return apiRequest<FeedbackMutationResponse>(`/school-admin/feedback/${encodeURIComponent(id)}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ reply })
  });
}
