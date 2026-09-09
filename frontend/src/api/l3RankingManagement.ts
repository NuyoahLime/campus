import { apiRequest } from './http';
import type {
  L3RankingDefinitionCreateForm,
  L3RankingManagementDefinition,
  L3RankingManagementPage,
  RankingGenerationResult,
  RankingPublicationResult
} from '../types/rankingManagement';

export function listL3RankingDefinitions(page = 0, size = 20) {
  return apiRequest<L3RankingManagementPage>(`/super-admin/ranking-definitions?page=${page}&size=${size}`);
}

export function getL3RankingDefinition(id: string) {
  return apiRequest<L3RankingManagementDefinition>(
    `/super-admin/ranking-definitions/${encodeURIComponent(id)}`
  );
}

export function createL3RankingDefinition(form: L3RankingDefinitionCreateForm) {
  return apiRequest<{ id: string; enabled: boolean }>('/super-admin/ranking-definitions', {
    method: 'POST',
    body: JSON.stringify({
      name: form.name,
      projectId: form.projectId,
      ruleVersionId: form.ruleVersionId
    })
  });
}

export function enableL3RankingDefinition(id: string) {
  return apiRequest<{ id: string; enabled: boolean }>(
    `/super-admin/ranking-definitions/${encodeURIComponent(id)}/enable`,
    { method: 'POST' }
  );
}

export function disableL3RankingDefinition(id: string) {
  return apiRequest<{ id: string; enabled: boolean }>(
    `/super-admin/ranking-definitions/${encodeURIComponent(id)}/disable`,
    { method: 'POST' }
  );
}

export function generateL3RankingDefinition(id: string) {
  return apiRequest<RankingGenerationResult>(
    `/super-admin/ranking-definitions/${encodeURIComponent(id)}/generate`,
    { method: 'POST' }
  );
}

export function publishL3RankingVersion(definitionId: string, versionId: string) {
  return apiRequest<RankingPublicationResult>(
    `/super-admin/ranking-definitions/${encodeURIComponent(definitionId)}/versions/${encodeURIComponent(versionId)}/publish`,
    { method: 'POST' }
  );
}
