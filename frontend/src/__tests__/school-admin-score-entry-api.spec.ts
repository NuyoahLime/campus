import { beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPatch = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
  },
}));

import {
  createSchoolAdminScoreDraft,
  fetchMySchoolAdminScoreEntries,
  fetchScoreEntryParticipants,
  fetchScoreEntryProjects,
  submitSchoolAdminScoreDraft,
  updateSchoolAdminScoreDraft,
} from '@/api/school-admin-score-entry';

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockPatch.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
  mockPost.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
  mockPatch.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
});

describe('school admin score entry API', () => {
  it('loads project options with trimmed keyword and pagination', async () => {
    await fetchScoreEntryProjects('  跳绳  ', 2, 30);
    expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/score-entry/projects', {
      params: { keyword: '跳绳', page: 2, size: 30 },
    });
  });

  it('loads assigned participants for the selected activity project', async () => {
    await fetchScoreEntryParticipants('activity-project-1', '  Alice  ', 1, 25);
    expect(mockGet).toHaveBeenCalledWith(
      '/v1/school-admin/score-entry/projects/activity-project-1/participants',
      { params: { keyword: 'Alice', page: 1, size: 25 } },
    );
  });

  it('posts the create draft payload', async () => {
    const payload = {
      activityProjectId: 'activity-project-1',
      studentId: 'student-1',
      integerValue: 100,
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'ON_SITE_RECORD',
    };
    await createSchoolAdminScoreDraft(payload);
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/score-attempts/drafts',
      payload,
    );
  });

  it('patches only the mutable score value fields', async () => {
    const payload = {
      durationMs: 0,
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'TEACHER_CONFIRMED',
    };
    await updateSchoolAdminScoreDraft('attempt-1', payload);
    expect(mockPatch).toHaveBeenCalledWith(
      '/v1/school-admin/score-attempts/attempt-1/draft',
      payload,
    );
  });

  it('submits the existing draft by id', async () => {
    await submitSchoolAdminScoreDraft('attempt-1');
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/score-attempts/attempt-1/submit',
    );
  });

  it('loads current admin entries with filters and pagination', async () => {
    await fetchMySchoolAdminScoreEntries(
      {
        status: 'REJECTED',
        activityId: 'activity-1',
        projectId: 'project-1',
        keyword: '  Alice  ',
      },
      3,
      15,
    );
    expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/score-attempts/mine', {
      params: {
        status: 'REJECTED',
        activityId: 'activity-1',
        projectId: 'project-1',
        keyword: 'Alice',
        page: 3,
        size: 15,
      },
    });
  });

  it('omits blank optional query parameters', async () => {
    await fetchScoreEntryProjects('   ');
    await fetchScoreEntryParticipants('activity-project-1', '   ');
    await fetchMySchoolAdminScoreEntries({ keyword: '   ' });
    expect(mockGet.mock.calls.map(call => call[1]?.params)).toEqual([
      { page: 0, size: 20 },
      { page: 0, size: 20 },
      { page: 0, size: 20 },
    ]);
  });

  it('never sends server-owned identity or lifecycle fields', async () => {
    await createSchoolAdminScoreDraft({
      activityProjectId: 'activity-project-1',
      studentId: 'student-1',
      grade: 'A',
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'OTHER',
    });
    await updateSchoolAdminScoreDraft('attempt-1', {
      grade: 'B',
      scoreBusinessTime: '2026-07-30T09:00:00Z',
      timeSource: 'OTHER',
    });
    const serialized = JSON.stringify([mockPost.mock.calls, mockPatch.mock.calls]);
    for (const forbidden of [
      'schoolId',
      'enteredBy',
      'reviewerId',
      'attemptNumber',
      'scoreStorageType',
      'status',
      'currentEffective',
    ]) {
      expect(serialized).not.toContain(forbidden);
    }
  });
});
