import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPatch = vi.fn();
const mockDelete = vi.fn();

vi.mock('@/api/http', () => ({
  default: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}));

import {
  fetchActivities,
  fetchActivity,
  createActivity,
  updateActivity,
  addProject,
  removeProject,
  publishActivity,
  fetchAvailableProjects,
  fetchActivityParticipants,
  addActivityParticipant,
  removeActivityParticipant,
  fetchProjectParticipants,
  assignProjectParticipant,
  unassignProjectParticipant,
  fetchActiveSchoolStudents,
} from '@/api/school-admin-activity';

beforeEach(() => {
  mockGet.mockReset();
  mockPost.mockReset();
  mockPatch.mockReset();
  mockDelete.mockReset();
});

describe('school-admin-activity API', () => {
  describe('fetchActivities', () => {
    it('calls GET /v1/school-admin/activities with page and size', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 0, size: 20, totalElements: 0 } });
      await fetchActivities({}, 0, 20);
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/activities', { params: { page: 0, size: 20 } });
    });

    it('passes executionStatus filter', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 0, size: 20, totalElements: 0 } });
      await fetchActivities({ executionStatus: 'DRAFT' }, 0, 20);
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/activities', {
        params: { page: 0, size: 20, executionStatus: 'DRAFT' },
      });
    });

    it('passes publicStatus filter', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 0, size: 20, totalElements: 0 } });
      await fetchActivities({ publicStatus: 'PUBLIC' }, 0, 20);
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/activities', {
        params: { page: 0, size: 20, publicStatus: 'PUBLIC' },
      });
    });

    it('passes keyword filter', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 0, size: 20, totalElements: 0 } });
      await fetchActivities({ keyword: 'math' }, 0, 20);
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/activities', {
        params: { page: 0, size: 20, keyword: 'math' },
      });
    });
  });

  describe('fetchActivity', () => {
    it('calls GET with activity ID', async () => {
      mockGet.mockResolvedValue({ data: { activityId: 'abc', title: 'Test' } });
      const result = await fetchActivity('abc');
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/activities/abc');
      expect(result.activityId).toBe('abc');
    });
  });

  describe('createActivity', () => {
    it('calls POST with payload', async () => {
      const payload = { title: 'New', description: 'desc' };
      mockPost.mockResolvedValue({ data: { activityId: 'xyz', executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED' } });
      await createActivity(payload);
      expect(mockPost).toHaveBeenCalledWith('/v1/school-admin/activities', payload);
    });
  });

  describe('updateActivity', () => {
    it('calls PATCH with payload', async () => {
      mockPatch.mockResolvedValue({ data: { activityId: 'abc', executionStatus: 'DRAFT', publicStatus: 'NOT_SUBMITTED' } });
      await updateActivity('abc', { title: 'Updated' });
      expect(mockPatch).toHaveBeenCalledWith('/v1/school-admin/activities/abc', { title: 'Updated' });
    });
  });

  describe('addProject', () => {
    it('calls POST with projectId', async () => {
      mockPost.mockResolvedValue({ data: { id: 'p1', activityId: 'abc', projectId: 'proj-1' } });
      await addProject('abc', 'proj-1');
      expect(mockPost).toHaveBeenCalledWith('/v1/school-admin/activities/abc/projects', { projectId: 'proj-1' });
    });
  });

  describe('removeProject', () => {
    it('calls DELETE on project', async () => {
      mockDelete.mockResolvedValue({});
      await removeProject('abc', 'proj-1');
      expect(mockDelete).toHaveBeenCalledWith('/v1/school-admin/activities/abc/projects/proj-1');
    });
  });

  describe('publishActivity', () => {
    it('calls POST to publish endpoint', async () => {
      mockPost.mockResolvedValue({ data: { activityId: 'abc', executionStatus: 'PUBLISHED', publicStatus: 'NOT_SUBMITTED' } });
      await publishActivity('abc');
      expect(mockPost).toHaveBeenCalledWith('/v1/school-admin/activities/abc/publish');
    });
  });

  describe('fetchAvailableProjects', () => {
    it('uses /v1/challenge-projects endpoint', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 0, size: 100, totalElements: 0 } });
      await fetchAvailableProjects();
      expect(mockGet).toHaveBeenCalledWith('/v1/challenge-projects', { params: { page: 0, size: 100 } });
    });
  });

  describe('participant roster endpoints', () => {
    it('fetches activity roster with trimmed keyword and pagination', async () => {
      mockGet.mockResolvedValue({ data: { items: [], page: 2, size: 15, totalElements: 0 } });
      await fetchActivityParticipants('activity-1', '  Alice  ', 2, 15);
      expect(mockGet).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/participants',
        { params: { page: 2, size: 15, keyword: 'Alice' } },
      );
    });

    it('adds an activity participant with studentId', async () => {
      mockPost.mockResolvedValue({ data: { studentId: 'student-1' } });
      await addActivityParticipant('activity-1', 'student-1');
      expect(mockPost).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/participants',
        { studentId: 'student-1' },
      );
    });

    it('removes an activity participant by studentId', async () => {
      mockDelete.mockResolvedValue({});
      await removeActivityParticipant('activity-1', 'student-1');
      expect(mockDelete).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/participants/student-1',
      );
    });

    it('fetches the project roster through the challenge project ID path', async () => {
      mockGet.mockResolvedValue({ data: [] });
      await fetchProjectParticipants('activity-1', 'project-1');
      expect(mockGet).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/projects/project-1/participants',
      );
    });

    it('assigns a project participant with studentId', async () => {
      mockPost.mockResolvedValue({ data: { studentId: 'student-1' } });
      await assignProjectParticipant('activity-1', 'project-1', 'student-1');
      expect(mockPost).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/projects/project-1/participants',
        { studentId: 'student-1' },
      );
    });

    it('unassigns a project participant by studentId', async () => {
      mockDelete.mockResolvedValue({});
      await unassignProjectParticipant('activity-1', 'project-1', 'student-1');
      expect(mockDelete).toHaveBeenCalledWith(
        '/v1/school-admin/activities/activity-1/projects/project-1/participants/student-1',
      );
    });

    it('fetches active school students with fixed role and status', async () => {
      mockGet.mockResolvedValue({ data: [] });
      await fetchActiveSchoolStudents('  bob  ', 1, 25);
      expect(mockGet).toHaveBeenCalledWith('/v1/school-admin/accounts', {
        params: {
          role: 'STUDENT',
          status: 'NORMAL',
          page: 1,
          size: 25,
          keyword: 'bob',
        },
      });
    });
  });
});
