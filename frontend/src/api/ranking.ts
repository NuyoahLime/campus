import { apiRequest } from './http';
import type { RankingDetail, RankingPage, RankingViewMode } from '../types/ranking';

function basePath(mode: RankingViewMode) {
  return mode === 'public'
    ? '/public/rankings'
    : mode === 'student'
      ? '/student/rankings'
      : '/school-admin/rankings';
}

export function listRankings(mode: RankingViewMode, page = 0, size = 20) {
  return apiRequest<RankingPage>(`${basePath(mode)}?page=${page}&size=${size}`);
}

export function getRanking(mode: RankingViewMode, id: string) {
  return apiRequest<RankingDetail>(`${basePath(mode)}/${encodeURIComponent(id)}`);
}
