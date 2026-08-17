import { apiRequest } from './http';
import type { ActivityDetail, ActivityPage } from '../types/activity';

export function listPublicActivities(page = 0, size = 20) {
  return apiRequest<ActivityPage>(`/activities?page=${page}&size=${size}`);
}

export function getPublicActivity(id: string) {
  return apiRequest<ActivityDetail>(`/activities/${encodeURIComponent(id)}`);
}
