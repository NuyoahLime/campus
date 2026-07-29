export interface SchoolAdminActivityItem {
  id: string; schoolId: string; title: string;
  startTime: string | null; endTime: string | null; location: string | null;
  executionStatus: string; publicStatus: string;
}

export interface SchoolAdminActivityDetail {
  activityId: string; schoolId: string; title: string; description: string | null;
  startTime: string | null; endTime: string | null; location: string | null;
  executionStatus: string; publicStatus: string; createdBy: string;
  projects: ActivityProjectItem[]; responsibleTeachers: ResponsibleTeacherItem[];
}

export interface ActivityProjectItem { id: string; activityId: string; projectId: string; _teachers?: ResponsibleTeacherItem[]; }
export interface ResponsibleTeacherItem { id: string; activityProjectId: string; teacherMembershipId: string; userId: string; username: string; subject: string; title: string; membershipStatus: string; accountStatus: string; }

export interface SchoolTeacherItem { userId: string; membershipId: string; username: string; subject: string; title: string; }

export interface ActivityParticipantItem {
  studentId: string;
  displayName: string | null;
  grade: string | null;
  className: string | null;
  studentNumber: string | null;
  assignedProjectCount: number;
  hasScoreAttempt: boolean;
  joinedAt: string;
}

export interface ProjectParticipantItem {
  activityProjectParticipantId: string;
  activityProjectId: string;
  participantId: string;
  studentId: string;
  displayName: string | null;
  attemptCount: number;
  hasScoreAttempt: boolean;
  latestAttemptId: string | null;
  latestAttemptStatus: string | null;
  latestScoreValue: string | null;
  hasApprovedScore: boolean;
  assignedAt: string;
}

export interface SchoolStudentAccountItem {
  userId: string;
  username: string;
  role: 'STUDENT';
  accountStatus: string;
  schoolName: string;
  createdAt: string;
}

export interface ActivityMutationResponse { activityId: string; executionStatus: string; publicStatus: string; }
export interface CreateActivityPayload { title: string; description?: string; startTime?: string; endTime?: string; location?: string; }
export interface UpdateActivityPayload { title?: string; description?: string; startTime?: string; endTime?: string; location?: string; }

export interface ActivityListFilter { executionStatus?: string; publicStatus?: string; keyword?: string; }

