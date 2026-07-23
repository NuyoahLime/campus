export interface PublicActivityItem {
  id: string;
  title: string;
  startTime: string | null;
  endTime: string | null;
  location: string | null;
  status: string;
}

export interface PublicActivityDetail {
  id: string;
  title: string;
  description: string | null;
  status: string;
  projects: PublicActivityProject[];
}

export interface PublicActivityProject {
  projectId: string;
}
