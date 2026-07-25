export type ApplicationStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface AdminApplicationItem {
  applicationId: string; schoolId: string; schoolName: string | null;
  applicantUserId: string; applicantName: string | null;
  title: string; descriptionSummary: string | null;
  status: ApplicationStatus; applicationVersion: number;
  createdAt: string; updatedAt: string; reviewedAt: string | null;
  createdActivityId: string | null;
}

export interface AdminApplicationDetail extends AdminApplicationItem {
  description: string | null; reviewComment: string | null; rejectReason: string | null;
}

export interface AdminApplicationStats {
  total: number; draft: number; submitted: number; approved: number; rejected: number; withdrawn: number; createdToday: number;
}

export interface AdminSchoolOption { schoolId: string; schoolName: string; }

export interface AdminListParams {
  status?: string; schoolId?: string; keyword?: string;
  createdFrom?: string; createdTo?: string; sort?: string;
  page?: number; size?: number;
}
