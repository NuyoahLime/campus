import type { PageResponse } from './schoolGovernance';

export interface StudentFeedback {
  feedbackId: string;
  feedbackType: string;
  content: string | null;
  status: string;
  reply: string | null;
  closeReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitStudentFeedbackRequest {
  feedbackType: string;
  content: string;
}

export type StudentFeedbackPage = PageResponse<StudentFeedback>;
