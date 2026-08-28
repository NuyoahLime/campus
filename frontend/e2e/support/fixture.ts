export const E2E_PASSWORD = 'password';

export const actors = {
  schoolAdminA: 'e2e-admin-a',
  schoolAdminB: 'e2e-admin-b',
  inactiveSchoolAdmin: 'e2e-admin-inactive',
  ambiguousSchoolAdmin: 'e2e-admin-ambiguous',
  studentA: 'e2e-student-a',
  studentB: 'e2e-student-b',
  studentOtherSchool: 'e2e-student-other-school',
  superAdmin: 'e2e-super-admin',
  teacher: 'e2e-teacher'
} as const;

export type FixtureState = {
  schoolA: string;
  schoolB: string;
  studentA: string;
  activityLifecycle: string;
  activityBest: string;
  activityLast: string;
  activityAdminDesignated: string;
  activityOtherSchool: string;
  activityApi: string;
  lifecycleProject: string;
  emptyHistoryProject: string;
  bestProject: string;
  lastProject: string;
  designatedProject: string;
  apiProject: string;
  lifecycleActivityProject: string;
  emptyHistoryActivityProject: string;
  bestActivityProject: string;
  lastActivityProject: string;
  designatedActivityProject: string;
  apiActivityProject: string;
  otherSchoolActivityProject: string;
  designatedFirstAttempt: string;
  designatedSecondAttempt: string;
  apiPendingAttempt: string;
  otherSchoolAttempt: string;
};
