export type ApplicationStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface TeacherActivityApplicationItem {
  applicationId: string;
  schoolId: string;
  schoolName: string | null;
  title: string;
  description: string | null;
  status: ApplicationStatus;
  applicationVersion: number;
  createdActivityId: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
  rejectReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateActivityApplicationRequest {
  schoolId: string;
  title: string;
  description?: string;
}

export interface UpdateActivityApplicationRequest {
  title: string;
  description?: string;
}
