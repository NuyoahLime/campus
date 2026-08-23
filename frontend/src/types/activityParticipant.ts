import type { ActivityDetail, ActivityListItem } from './activity';
import type { PageResponse } from './schoolGovernance';

export interface ActivityParticipant {
  studentId: string;
  displayName: string | null;
  studentNumber: string | null;
  grade: string | null;
  className: string | null;
  assignedAt: string | null;
}

export type AssignedActivityPage = PageResponse<ActivityListItem>;
export type AssignedActivityDetail = ActivityDetail;
