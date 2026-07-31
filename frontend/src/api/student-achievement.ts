import http from './http';
import type {
  PublicAchievementVerification,
  StudentAchievementDetail,
  StudentAchievementFilter,
  StudentAchievementPage,
} from '@/types/student-achievement';

const STUDENT_PREFIX = '/v1/student/achievement-records';
const PUBLIC_PREFIX = '/v1/public/achievement-records';

export async function fetchMyAchievementRecords(
  filter: StudentAchievementFilter = {},
  page = 0,
  size = 20,
): Promise<StudentAchievementPage> {
  const params: Record<string, string | number> = { page, size };
  if (filter.status) params.status = filter.status;
  const keyword = filter.keyword?.trim();
  if (keyword) params.keyword = keyword;
  const response = await http.get<StudentAchievementPage>(STUDENT_PREFIX, { params });
  return response.data;
}

export async function fetchMyAchievementRecord(
  recordId: string,
): Promise<StudentAchievementDetail> {
  const response = await http.get<StudentAchievementDetail>(
    `${STUDENT_PREFIX}/${recordId}`,
  );
  return response.data;
}

export async function verifyAchievementRecord(
  verificationCode: string,
): Promise<PublicAchievementVerification> {
  const normalizedCode = verificationCode.trim().toLowerCase();
  const response = await http.get<PublicAchievementVerification>(
    `${PUBLIC_PREFIX}/${normalizedCode}`,
  );
  return response.data;
}
