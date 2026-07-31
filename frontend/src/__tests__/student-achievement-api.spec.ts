import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}));

import {
  fetchMyAchievementRecord,
  fetchMyAchievementRecords,
  verifyAchievementRecord,
} from '@/api/student-achievement';

beforeEach(() => {
  mockGet.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
});

describe('student achievement API', () => {
  it('listsCurrentStudentsRecordsAndTrimsKeyword', async () => {
    await fetchMyAchievementRecords(
      { status: 'REVOKED', keyword: '  relay  ' },
      2,
      25,
    );

    expect(mockGet).toHaveBeenCalledWith(
      '/v1/student/achievement-records',
      {
        params: {
          page: 2,
          size: 25,
          status: 'REVOKED',
          keyword: 'relay',
        },
      },
    );
  });

  it('omitsEmptyFilters', async () => {
    await fetchMyAchievementRecords(
      { status: '', keyword: '  ' },
      0,
      20,
    );

    expect(mockGet).toHaveBeenCalledWith(
      '/v1/student/achievement-records',
      { params: { page: 0, size: 20 } },
    );
  });

  it('loadsOwnedDetail', async () => {
    await fetchMyAchievementRecord('record-1');

    expect(mockGet).toHaveBeenCalledWith(
      '/v1/student/achievement-records/record-1',
    );
  });

  it('normalizesPublicVerificationCode', async () => {
    await verifyAchievementRecord(`  ${'A'.repeat(32)}  `);

    expect(mockGet).toHaveBeenCalledWith(
      `/v1/public/achievement-records/${'a'.repeat(32)}`,
    );
  });

  it('doesNotSendStudentOrSchoolOwnedIds', async () => {
    await fetchMyAchievementRecords({ keyword: 'relay' }, 0, 20);
    await fetchMyAchievementRecord('record-1');
    await verifyAchievementRecord('a'.repeat(32));

    const serialized = JSON.stringify(mockGet.mock.calls);
    expect(serialized).not.toContain('studentId');
    expect(serialized).not.toContain('schoolId');
  });
});
