import { apiRequest } from './http';
import type { AssignedActivityDetail, AssignedActivityPage, ActivityParticipant } from '../types/activityParticipant';

export function listActivityParticipants(activityId: string) {
  return apiRequest<ActivityParticipant[]>(`/school-admin/activities/${encodeURIComponent(activityId)}/participants`);
}

export function listParticipantCandidates(activityId: string, query = '') {
  const suffix = query.trim() ? `?q=${encodeURIComponent(query.trim())}` : '';
  return apiRequest<ActivityParticipant[]>(
    `/school-admin/activities/${encodeURIComponent(activityId)}/participant-candidates${suffix}`
  );
}

export function assignActivityParticipant(activityId: string, studentId: string) {
  return apiRequest<void>(`/school-admin/activities/${encodeURIComponent(activityId)}/participants`, {
    method: 'POST',
    body: JSON.stringify({ studentId })
  });
}

export function removeActivityParticipant(activityId: string, studentId: string) {
  return apiRequest<void>(
    `/school-admin/activities/${encodeURIComponent(activityId)}/participants/${encodeURIComponent(studentId)}`,
    { method: 'DELETE' }
  );
}

export function listAssignedActivities(page = 0, size = 20) {
  return apiRequest<AssignedActivityPage>(`/student/activities?page=${page}&size=${size}`);
}

export function getAssignedActivity(id: string) {
  return apiRequest<AssignedActivityDetail>(`/student/activities/${encodeURIComponent(id)}`);
}
