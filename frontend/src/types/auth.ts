export interface AuthenticatedSchoolMembership {
  membershipId: string;
  schoolId: string;
  roleInSchool: 'STUDENT' | 'SCHOOL_ADMIN' | string;
}

export interface CurrentUser {
  userId: string;
  username: string;
  accountStatus: string;
  authorities: string[];
  schoolMemberships: AuthenticatedSchoolMembership[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface CsrfTokenResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  path: string;
  timestamp: string;
  details: unknown[];
}
