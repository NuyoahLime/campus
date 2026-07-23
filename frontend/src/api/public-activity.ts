import http from './http';
import type { PageResponse } from '@/types/api';
import type { PublicActivityItem, PublicActivityDetail } from '@/types/activity';

export async function fetchPublicActivities(
  page = 0,
  size = 20,
): Promise<PageResponse<PublicActivityItem>> {
  const response = await http.get<PageResponse<PublicActivityItem>>('/v1/public/activities', {
    params: { page, size },
  });
  return response.data;
}

export async function fetchPublicActivityById(
  activityId: string,
): Promise<PublicActivityDetail> {
  const response = await http.get<PublicActivityDetail>(
    `/v1/public/activities/${activityId}`,
  );
  return response.data;
}
