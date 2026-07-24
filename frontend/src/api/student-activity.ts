import http from './http';
import type { PageResponse } from '@/types/api';
import type { StudentActivityItem, StudentActivityDetail } from '@/types/student-activity';

export async function fetchMyActivities(page = 0, size = 20): Promise<PageResponse<StudentActivityItem>> {
  const res = await http.get<PageResponse<StudentActivityItem>>('/v1/student/activities/mine', {
    params: { page, size },
  });
  return res.data;
}

export async function fetchMyActivityById(activityId: string): Promise<StudentActivityDetail> {
  const res = await http.get<StudentActivityDetail>(`/v1/student/activities/mine/${activityId}`);
  return res.data;
}
