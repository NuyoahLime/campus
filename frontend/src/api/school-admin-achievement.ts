import http from './http';
import type {
  SchoolAdminAchievementDetail,
  SchoolAdminAchievementStatus,
} from '@/types/student-achievement';

const PREFIX = '/v1/school-admin/achievement-records';

export async function issueAchievementForRankingEntry(
  rankingEntryId: string,
): Promise<SchoolAdminAchievementDetail> {
  const response = await http.put<SchoolAdminAchievementDetail>(
    `${PREFIX}/ranking-entries/${rankingEntryId}`,
  );
  return response.data;
}

export async function fetchRankingVersionAchievementStatuses(
  rankingVersionId: string,
): Promise<SchoolAdminAchievementStatus[]> {
  const response = await http.get<SchoolAdminAchievementStatus[]>(
    `${PREFIX}/ranking-versions/${rankingVersionId}/statuses`,
  );
  return response.data;
}

export async function fetchSchoolAchievementRecord(
  recordId: string,
): Promise<SchoolAdminAchievementDetail> {
  const response = await http.get<SchoolAdminAchievementDetail>(
    `${PREFIX}/${recordId}`,
  );
  return response.data;
}
