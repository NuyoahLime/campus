import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

import {
  fetchStudentCurrentRanking,
  fetchStudentOwnRanking,
  fetchStudentRankingProjects,
} from '@/api/student-ranking';

beforeEach(() => {
  mockGet.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
});

describe('student ranking API', () => {
  it('usesStudentRankingListPathAndTrimsKeyword', async () => {
    await fetchStudentRankingProjects(
      {
        executionStatus: 'ENDED',
        rankingAvailability: 'CURRENT',
        keyword: '  relay  ',
      },
      2,
      25,
    );
    expect(mockGet).toHaveBeenCalledWith('/v1/student/rankings', {
      params: {
        page: 2,
        size: 25,
        executionStatus: 'ENDED',
        rankingAvailability: 'CURRENT',
        keyword: 'relay',
      },
    });
  });

  it('omitsEmptyStudentRankingFilters', async () => {
    await fetchStudentRankingProjects(
      { executionStatus: '', rankingAvailability: '', keyword: '  ' },
      0,
      20,
    );
    expect(mockGet).toHaveBeenCalledWith('/v1/student/rankings', {
      params: { page: 0, size: 20 },
    });
  });

  it('usesStudentCurrentRankingPath', async () => {
    await fetchStudentCurrentRanking('activity-project-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/student/rankings/activity-project-1',
    );
  });

  it('usesStudentOwnRankingPath', async () => {
    await fetchStudentOwnRanking('activity-project-1');
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/student/rankings/activity-project-1/mine',
    );
  });

  it('apiRequestsDoNotContainStudentOrSchoolOwnedIds', async () => {
    await fetchStudentRankingProjects({ keyword: 'relay' }, 0, 20);
    await fetchStudentCurrentRanking('activity-project-1');
    await fetchStudentOwnRanking('activity-project-1');
    const serialized = JSON.stringify(mockGet.mock.calls);
    for (const field of [
      'studentId',
      'schoolId',
      'membershipId',
      'versionId',
      'currentVersionId',
    ]) {
      expect(serialized).not.toContain(field);
    }
  });
});
