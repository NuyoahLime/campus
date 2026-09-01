import { apiRequest } from './http';
import type {
  RankingDefinitionCreateForm,
  RankingGenerationResult,
  RankingManagementDefinition,
  RankingManagementPage,
  RankingPublicationResult
} from '../types/rankingManagement';

export function listManagedRankingDefinitions(page = 0, size = 20) {
  return apiRequest<RankingManagementPage>(`/school-admin/ranking-definitions?page=${page}&size=${size}`);
}

export function getManagedRankingDefinition(id: string) {
  return apiRequest<RankingManagementDefinition>(`/school-admin/ranking-definitions/${encodeURIComponent(id)}`);
}

export function createRankingDefinition(form: RankingDefinitionCreateForm) {
  return apiRequest<{ id: string; enabled: boolean }>('/ranking-definitions', {
    method: 'POST',
    body: JSON.stringify({
      layer: 'L1',
      name: form.name,
      projectId: form.projectId,
      activityProjectId: form.activityProjectId
    })
  });
}

export function enableRankingDefinition(id: string) {
  return apiRequest<{ id: string; enabled: boolean }>(`/ranking-definitions/${encodeURIComponent(id)}/enable`, {
    method: 'POST'
  });
}

export function disableRankingDefinition(id: string) {
  return apiRequest<{ id: string; enabled: boolean }>(`/ranking-definitions/${encodeURIComponent(id)}/disable`, {
    method: 'POST'
  });
}

export function generateRankingDefinition(id: string) {
  return apiRequest<RankingGenerationResult>(`/ranking-definitions/${encodeURIComponent(id)}/generate`, {
    method: 'POST'
  });
}

export function publishRankingVersion(definitionId: string, versionId: string) {
  return apiRequest<RankingPublicationResult>(
    `/ranking-definitions/${encodeURIComponent(definitionId)}/versions/${encodeURIComponent(versionId)}/publish`,
    { method: 'POST' }
  );
}
