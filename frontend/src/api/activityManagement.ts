import { apiRequest } from './http';
import type { PageResponse } from '../types/schoolGovernance';
import type { ActivityManagementDetail, ActivityManagementForm, ActivityManagementListItem } from '../types/activityManagement';

export function listManagedActivities(page = 0, size = 20, status = '', query = '') {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  if (query.trim()) params.set('q', query.trim());
  return apiRequest<PageResponse<ActivityManagementListItem>>(`/activities/management?${params}`);
}

export function getManagedActivity(id: string) {
  return apiRequest<ActivityManagementDetail>(`/activities/management/${encodeURIComponent(id)}`);
}

export function createManagedActivity(form: ActivityManagementForm) {
  return apiRequest<{ id: string; executionStatus: string; publicStatus: string }>('/activities', {
    method: 'POST', body: JSON.stringify(toPayload(form))
  });
}

export function updateManagedActivity(id: string, form: ActivityManagementForm) {
  return apiRequest<{ id: string; executionStatus: string; publicStatus: string }>(`/activities/${encodeURIComponent(id)}`, {
    method: 'PUT', body: JSON.stringify(toPayload(form, false))
  });
}

export function publishManagedActivity(id: string) {
  return apiRequest<{ id: string; executionStatus: string; publicStatus: string }>(`/activities/${encodeURIComponent(id)}/publish`, { method: 'POST' });
}

export function cancelManagedActivity(id: string) {
  return apiRequest<{ id: string; executionStatus: string; publicStatus: string }>(`/activities/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
}

function toPayload(form: ActivityManagementForm, includeProject = true) {
  return {
    ...(includeProject ? { projectId: form.projectId } : {}),
    title: form.title,
    description: form.description || null,
    startTime: form.startTime ? new Date(form.startTime).toISOString() : null,
    endTime: form.endTime ? new Date(form.endTime).toISOString() : null,
    location: form.location || null
  };
}
