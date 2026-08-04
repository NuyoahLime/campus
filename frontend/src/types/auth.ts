export interface SchoolMembershipItem {
  schoolId: string;
  roleInSchool: string;
}

export type PlatformRole =
  | 'SUPER_ADMIN'
  | 'REGISTERED_USER'
  | null;

export type PrimaryRole =
  | 'SUPER_ADMIN'
  | 'SCHOOL_ADMIN'
  | 'TEACHER'
  | 'STUDENT'
  | 'REGISTERED_USER';

export interface AuthContextResponse {
  userId: string;
  username: string;
  accountStatus: string;
  platformRole: PlatformRole;
  roles: string[];
  schoolMemberships: SchoolMembershipItem[];
  primaryRole: PrimaryRole;
  primarySchoolId: string | null;
  onboardingRequired: boolean;
}

export interface CsrfTokenResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}
