import { apiRequest } from './http';
import type {
  PageResponse,
  StudentIdentityApplicationDetail,
  StudentIdentityApplicationReviewResult,
  StudentIdentityApplicationSummary
} from '../types/studentReview';

function applicationPath(schoolId: string): string {
  return `/schools/${encodeURIComponent(schoolId)}/student-identity-applications`;
}

export async function listPendingStudentApplications(
  schoolId: string,
  page = 0,
  size = 20
): Promise<PageResponse<StudentIdentityApplicationSummary>> {
  const query = new URLSearchParams({
    status: 'PENDING',
    page: String(page),
    size: String(size)
  });
  return apiRequest<PageResponse<StudentIdentityApplicationSummary>>(
    `${applicationPath(schoolId)}?${query.toString()}`
  );
}

export async function getStudentApplicationDetail(
  schoolId: string,
  applicationId: string
): Promise<StudentIdentityApplicationDetail> {
  return apiRequest<StudentIdentityApplicationDetail>(
    `${applicationPath(schoolId)}/${encodeURIComponent(applicationId)}`
  );
}

export async function approveStudentApplication(
  schoolId: string,
  applicationId: string
): Promise<StudentIdentityApplicationReviewResult> {
  return apiRequest<StudentIdentityApplicationReviewResult>(
    `${applicationPath(schoolId)}/${encodeURIComponent(applicationId)}/approve`,
    { method: 'POST' }
  );
}

export async function rejectStudentApplication(
  schoolId: string,
  applicationId: string,
  reason: string
): Promise<StudentIdentityApplicationReviewResult> {
  return apiRequest<StudentIdentityApplicationReviewResult>(
    `${applicationPath(schoolId)}/${encodeURIComponent(applicationId)}/reject`,
    {
      method: 'POST',
      body: JSON.stringify({ reason })
    }
  );
}
