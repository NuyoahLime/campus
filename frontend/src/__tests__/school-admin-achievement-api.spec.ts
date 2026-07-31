import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPut = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    put: (...args: unknown[]) => mockPut(...args),
  },
}));

import {
  fetchRankingVersionAchievementStatuses,
  fetchSchoolAchievementRecord,
  issueAchievementForRankingEntry,
} from '@/api/school-admin-achievement';

beforeEach(() => {
  mockGet.mockReset();
  mockPut.mockReset();
  mockGet.mockResolvedValue({ data: [] });
  mockPut.mockResolvedValue({ data: {} });
});

describe('school admin achievement API', () => {
  it('issuesByRankingEntryIdWithNoPayload', async () => {
    await issueAchievementForRankingEntry('entry-1');

    expect(mockPut).toHaveBeenCalledTimes(1);
    expect(mockPut).toHaveBeenCalledWith(
      '/v1/school-admin/achievement-records/ranking-entries/entry-1',
    );
    expect(mockPut.mock.calls[0]).toHaveLength(1);
  });

  it('loadsAllVersionStatusesOnce', async () => {
    await fetchRankingVersionAchievementStatuses('version-1');

    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/achievement-records/ranking-versions/version-1/statuses',
    );
  });

  it('loadsSchoolScopedRecord', async () => {
    await fetchSchoolAchievementRecord('record-1');

    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/achievement-records/record-1',
    );
  });

  it('requestCannotContainOwnedOrSnapshotFields', async () => {
    await issueAchievementForRankingEntry('entry-1');

    const serialized = JSON.stringify(
      mockPut.mock.calls.map(call => call.slice(1)),
    );
    for (const field of [
      'studentId',
      'schoolId',
      'rank',
      'scoreValue',
      'issuedBy',
      'verificationCode',
      'status',
    ]) {
      expect(serialized).not.toContain(field);
    }
  });
});
