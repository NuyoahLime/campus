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
  fetchTeacherProjectParticipants,
  fetchTeacherResponsibleProject,
  fetchTeacherResponsibleProjects,
} from '@/api/teacher-responsible-project';
import {
  createTeacherScoreAttempt,
  fetchMyTeacherScoreAttempts,
  fetchTeacherScoreAttemptDetail,
  submitTeacherScoreDraft,
  updateTeacherScoreDraft,
} from '@/api/teacher-score-entry';

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockPatch.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
  mockPost.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
  mockPatch.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
});

describe('teacher responsible project and score entry API', () => {
  it('loads responsible projects with filters and pagination', async () => {
    await fetchTeacherResponsibleProjects(
      { executionStatus: 'IN_PROGRESS', keyword: '  校运会  ' },
      2,
      30,
    );
    expect(mockGet).toHaveBeenCalledWith('/v1/teacher/responsible-projects', {
      params: {
        executionStatus: 'IN_PROGRESS',
        keyword: '校运会',
        page: 2,
        size: 30,
      },
    });
  });

  it('loads a responsible project and its assigned participants', async () => {
    await fetchTeacherResponsibleProject('activity-project-1');
    await fetchTeacherProjectParticipants(
      'activity-project-1',
      { status: 'REJECTED', keyword: ' Alice ' },
      1,
      25,
    );
    expect(mockGet).toHaveBeenNthCalledWith(
      1,
      '/v1/teacher/responsible-projects/activity-project-1',
    );
    expect(mockGet).toHaveBeenNthCalledWith(
      2,
      '/v1/teacher/responsible-projects/activity-project-1/participants',
      { params: { status: 'REJECTED', keyword: 'Alice', page: 1, size: 25 } },
    );
  });

  it('posts a server-owned create-and-submit score payload', async () => {
    const payload = {
      activityProjectId: 'activity-project-1',
      studentId: 'student-1',
      integerValue: 100,
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'ON_SITE_RECORD',
    };
    await createTeacherScoreAttempt(payload);
    expect(mockPost).toHaveBeenCalledWith('/v1/teacher/score-attempts', payload);
  });

  it('loads current teacher entries with filters', async () => {
    await fetchMyTeacherScoreAttempts(
      {
        status: 'REJECTED',
        activityProjectId: 'activity-project-1',
        keyword: '  Alice  ',
      },
      3,
      15,
    );
    expect(mockGet).toHaveBeenCalledWith('/v1/teacher/score-attempts/mine', {
      params: {
        status: 'REJECTED',
        activityProjectId: 'activity-project-1',
        keyword: 'Alice',
        page: 3,
        size: 15,
      },
    });
  });

  it('loads score detail', async () => {
    await fetchTeacherScoreAttemptDetail('attempt-1');
    expect(mockGet).toHaveBeenCalledWith('/v1/teacher/score-attempts/attempt-1');
  });

  it('updates mutable fields and resubmits the draft', async () => {
    const payload = {
      durationMs: 0,
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'TEACHER_CONFIRMED',
    };
    await updateTeacherScoreDraft('attempt-1', payload);
    await submitTeacherScoreDraft('attempt-1');
    expect(mockPatch).toHaveBeenCalledWith(
      '/v1/teacher/score-attempts/attempt-1/draft',
      payload,
    );
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/teacher/score-attempts/attempt-1/submit',
    );
  });

  it('omits blank optional query parameters', async () => {
    await fetchTeacherResponsibleProjects({ keyword: '   ' });
    await fetchTeacherProjectParticipants('activity-project-1', {
      keyword: '   ',
    });
    await fetchMyTeacherScoreAttempts({ keyword: '   ' });
    expect(mockGet.mock.calls.map((call) => call[1]?.params)).toEqual([
      { page: 0, size: 20 },
      { page: 0, size: 20 },
      { page: 0, size: 20 },
    ]);
  });

  it('never sends server-owned identity, numbering, type, or lifecycle fields', async () => {
    await createTeacherScoreAttempt({
      activityProjectId: 'activity-project-1',
      studentId: 'student-1',
      grade: 'A',
      scoreBusinessTime: '2026-07-30T08:00:00Z',
      timeSource: 'OTHER',
    });
    await updateTeacherScoreDraft('attempt-1', {
      grade: 'B',
      scoreBusinessTime: '2026-07-30T09:00:00Z',
      timeSource: 'OTHER',
    });
    const serialized = JSON.stringify([mockPost.mock.calls, mockPatch.mock.calls]);
    for (const forbidden of [
      'schoolId',
      'teacherId',
      'enteredBy',
      'attemptNumber',
      'scoreStorageType',
      'status',
      'currentEffective',
    ]) {
      expect(serialized).not.toContain(forbidden);
    }
  });
});
