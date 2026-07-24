export interface StudentActivityItem {
  activityId: string;
  title: string;
  descriptionSummary: string | null;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  executionStatus: string;
  assignedProjectCount: number;
}

export interface StudentActivityDetail {
  activityId: string;
  title: string;
  description: string | null;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  executionStatus: string;
  projects: StudentAssignedProject[];
}

export interface StudentAssignedProject {
  activityProjectId: string;
  projectId: string;
  projectName: string;
  category: string;
  scoreStorageType: string;
  scoreUnit: string | null;
  latestAttemptStatus: string | null;
  hasApprovedScore: boolean;
}
