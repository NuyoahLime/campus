export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface PublicSchoolSummary {
  id: string;
  name: string;
  schoolType: string;
  region: string;
}

export interface StudentRegistrationRequest {
  username: string;
  password: string;
  confirmPassword: string;
  realName: string;
  schoolId: string;
  studentNumber: string;
  grade: string;
  className: string;
  proofFileKeys: string[];
}

export interface StudentRegistrationResponse {
  userId: string;
  applicationId: string;
  username: string;
  schoolId: string;
  accountStatus: string;
  applicationStatus: string;
  submittedAt: string;
}
