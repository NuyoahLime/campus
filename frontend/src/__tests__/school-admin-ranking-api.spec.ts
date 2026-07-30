import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

import {
  fetchCurrentRanking,
  fetchRankingProject,
  fetchRankingProjects,
  fetchRankingVersion,
  fetchRankingVersions,
  previewRanking,
  publishRanking,
  withdrawRanking,
} from '@/api/school-admin-ranking';

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
  mockPost.mockResolvedValue({ data: { versionId: 'version-1' } });
});

describe('school admin ranking API', () => {
  it('uses the project list GET path and pagination', async () => {
    await fetchRankingProjects({}, 2, 25);
    expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/rankings/projects', {
      params: { page: 2, size: 25 },
    });
  });

  it('sends executionStatus', async () => {
    await fetchRankingProjects({ executionStatus: 'ENDED' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.executionStatus).toBe('ENDED');
  });

  it('sends rankingStatus', async () => {
    await fetchRankingProjects({ rankingStatus: 'CURRENT' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.rankingStatus).toBe('CURRENT');
  });

  it('trims keyword', async () => {
    await fetchRankingProjects({ keyword: '  relay  ' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.keyword).toBe('relay');
  });

  it('uses the project detail path', async () => {
    await fetchRankingProject('project-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1',
    );
  });

  it('uses the preview path', async () => {
    await previewRanking('project-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1/preview',
    );
  });

  it('posts only the preview fingerprint to publish', async () => {
    await publishRanking('project-1', { expectedSourceFingerprint: 'a'.repeat(64) });
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1/publish',
      { expectedSourceFingerprint: 'a'.repeat(64) },
    );
  });

  it('uses the current version path', async () => {
    await fetchCurrentRanking('project-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1/current',
    );
  });

  it('uses the version history path and pagination', async () => {
    await fetchRankingVersions('project-1', 3, 10);
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1/versions',
      { params: { page: 3, size: 10 } },
    );
  });

  it('uses the version detail path', async () => {
    await fetchRankingVersion('version-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/versions/version-1',
    );
  });

  it('posts only the trimmed withdrawal reason supplied by the caller', async () => {
    await withdrawRanking('project-1', { reason: 'correction' });
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/rankings/projects/project-1/withdraw',
      { reason: 'correction' },
    );
  });

  it('never sends server-owned fields in project requests', async () => {
    await fetchRankingProjects({ keyword: 'relay' }, 0, 20);
    await fetchRankingProject('project-1');
    await previewRanking('project-1');
    const serialized = JSON.stringify(mockGet.mock.calls);
    for (const field of [
      'schoolId',
      'publishedBy',
      'withdrawnBy',
      'versionNumber',
      'comparisonDirection',
      'tiePolicy',
      'entryCount',
    ]) {
      expect(serialized).not.toContain(field);
    }
  });

  it('never sends derived publication or entry fields', async () => {
    await publishRanking('project-1', { expectedSourceFingerprint: 'b'.repeat(64) });
    await withdrawRanking('project-1', { reason: 'correction' });
    const serialized = JSON.stringify(mockPost.mock.calls);
    for (const field of [
      'schoolId',
      'publishedBy',
      'withdrawnBy',
      'versionNumber',
      'comparisonDirection',
      'tiePolicy',
      'entries',
    ]) {
      expect(serialized).not.toContain(field);
    }
  });
});
