import { apiRequest } from './http';
import type {
  ChallengeProjectDetail,
  ChallengeProjectListItem,
  GovernanceProjectDetail,
  GovernanceProjectListItem,
  ProjectForm,
  ProjectPage,
  ProjectStatus
} from '../types/challengeProject';

function queryString(params: Record<string, string | number | null | undefined>): string {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value).trim()) query.set(key, String(value));
  });
  return query.toString();
}

export function listPublicProjects(page = 0, size = 20, category = '', search = '') {
  const query = queryString({ page, size, category, q: search });
  return apiRequest<ProjectPage<ChallengeProjectListItem>>(`/challenge-projects?${query}`);
}

export function getPublicProject(id: string) {
  return apiRequest<ChallengeProjectDetail>(`/challenge-projects/${encodeURIComponent(id)}`);
}

export function listGovernanceProjects(
  page = 0, size = 20, status: ProjectStatus | '' = '', category = '', search = ''
) {
  const query = queryString({ page, size, status, category, q: search });
  return apiRequest<ProjectPage<GovernanceProjectListItem>>(`/challenge-projects/governance?${query}`);
}

export function getGovernanceProject(id: string) {
  return apiRequest<GovernanceProjectDetail>(`/challenge-projects/governance/${encodeURIComponent(id)}`);
}

export function createProject(form: ProjectForm) {
  return apiRequest<{ id: string; name: string; status: string }>('/challenge-projects', {
    method: 'POST',
    body: JSON.stringify(form)
  });
}

export function updateProject(id: string, form: ProjectForm) {
  return apiRequest<{ id: string; name: string; status: string }>(`/challenge-projects/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(form)
  });
}

export function publishProject(id: string, reason: string) {
  return apiRequest<{ id: string; name: string; status: string }>(`/challenge-projects/${encodeURIComponent(id)}/publish`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}

export function archiveProject(id: string, reason: string) {
  return apiRequest<{ id: string; name: string; status: string }>(`/challenge-projects/${encodeURIComponent(id)}/archive`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
}
