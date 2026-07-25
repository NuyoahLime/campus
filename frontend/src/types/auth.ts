export interface SchoolMembershipItem {
  schoolId: string;
  roleInSchool: string;
}

export interface AuthContextResponse {
  userId: string;
  username: string;
  accountStatus: string;
  platformRole: string | null;
  roles: string[];
  schoolMemberships: SchoolMembershipItem[];
  primaryRole: string | null;
  primarySchoolId: string | null;
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
