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

export interface ActivityProjectItem { id: string; activityId: string; projectId: string; }
export interface ResponsibleTeacherItem { activityProjectId: string; userId: string; }

export interface ActivityMutationResponse { activityId: string; executionStatus: string; publicStatus: string; }
export interface CreateActivityPayload { title: string; description?: string; startTime?: string; endTime?: string; location?: string; }
export interface UpdateActivityPayload { title?: string; description?: string; startTime?: string; endTime?: string; location?: string; }

export interface ActivityListFilter { executionStatus?: string; publicStatus?: string; keyword?: string; }

