export interface PublicSchoolRegistrationRequest {
  schoolName: string;
  unifiedCodeType: string;
  unifiedCode?: string;
  schoolType: string;
  region: string;
  address: string;
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  description?: string;
}

export interface PublicSchoolRegistrationResponse {
  id: string;
  schoolName: string;
  status: string;
  createdSchoolId: string | null;
}
