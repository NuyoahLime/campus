import http from './http';
import type {
  StudentCurrentRankingDetail,
  StudentOwnRanking,
  StudentRankingFilter,
  StudentRankingProjectPage,
} from '@/types/student-ranking';

export async function fetchStudentRankingProjects(
  filter: StudentRankingFilter,
  page = 0,
  size = 20,
): Promise<StudentRankingProjectPage> {
  const params: Record<string, string | number> = { page, size };
  if (filter.executionStatus) params.executionStatus = filter.executionStatus;
  if (filter.rankingAvailability) {
    params.rankingAvailability = filter.rankingAvailability;
  }
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<StudentRankingProjectPage>(
    '/v1/student/rankings',
    { params },
  );
  return response.data;
}

export async function fetchStudentCurrentRanking(
  activityProjectId: string,
): Promise<StudentCurrentRankingDetail> {
  const response = await http.get<StudentCurrentRankingDetail>(
    `/v1/student/rankings/${activityProjectId}`,
  );
  return response.data;
}

export async function fetchStudentOwnRanking(
  activityProjectId: string,
): Promise<StudentOwnRanking> {
  const response = await http.get<StudentOwnRanking>(
    `/v1/student/rankings/${activityProjectId}/mine`,
  );
  return response.data;
}
