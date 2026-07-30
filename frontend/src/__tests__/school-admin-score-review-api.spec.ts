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
  approveSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempt,
  fetchSchoolAdminScoreAttempts,
  rejectSchoolAdminScoreAttempt,
} from '@/api/school-admin-score-review';

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockGet.mockResolvedValue({ data: { items: [], totalElements: 0 } });
  mockPost.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
});

describe('school admin score review API', () => {
  it('uses the list path with default status and pagination', async () => {
    await fetchSchoolAdminScoreAttempts({}, 2, 25);
    expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/score-attempts', {
      params: { status: 'PENDING_REVIEW', page: 2, size: 25 },
    });
  });

  it('passes the status filter', async () => {
    await fetchSchoolAdminScoreAttempts({ status: 'APPROVED' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.status).toBe('APPROVED');
  });

  it('passes activityId', async () => {
    await fetchSchoolAdminScoreAttempts({ activityId: 'activity-1' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.activityId).toBe('activity-1');
  });

  it('passes projectId', async () => {
    await fetchSchoolAdminScoreAttempts({ projectId: 'project-1' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.projectId).toBe('project-1');
  });

  it('trims keyword', async () => {
    await fetchSchoolAdminScoreAttempts({ keyword: '  alice  ' }, 0, 20);
    expect(mockGet.mock.calls[0][1].params.keyword).toBe('alice');
  });

  it('uses the detail path', async () => {
    mockGet.mockResolvedValue({ data: { attemptId: 'attempt-1' } });
    await fetchSchoolAdminScoreAttempt('attempt-1');
    expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/score-attempts/attempt-1');
  });

  it('posts approve payload', async () => {
    const payload = { reviewComment: 'ok', makeCurrentEffective: true };
    await approveSchoolAdminScoreAttempt('attempt-1', payload);
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/score-attempts/attempt-1/approve',
      payload,
    );
  });

  it('posts reject payload', async () => {
    const payload = { rejectReason: 'wrong', reviewComment: 'retry' };
    await rejectSchoolAdminScoreAttempt('attempt-1', payload);
    expect(mockPost).toHaveBeenCalledWith(
      '/v1/school-admin/score-attempts/attempt-1/reject',
      payload,
    );
  });

  it('never sends schoolId or reviewerId in list requests', async () => {
    await fetchSchoolAdminScoreAttempts({ keyword: 'alice' }, 0, 20);
    const serialized = JSON.stringify(mockGet.mock.calls[0]);
    expect(serialized).not.toContain('schoolId');
    expect(serialized).not.toContain('reviewerId');
  });

  it('never sends schoolId or reviewerId in review requests', async () => {
    await approveSchoolAdminScoreAttempt('attempt-1', { reviewComment: 'ok' });
    await rejectSchoolAdminScoreAttempt('attempt-1', { rejectReason: 'wrong' });
    const serialized = JSON.stringify(mockPost.mock.calls);
    expect(serialized).not.toContain('schoolId');
    expect(serialized).not.toContain('reviewerId');
  });
});
