import { expect, request as playwrightRequest, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { actors, apiRequest, loginApi } from '../support/auth';
import type { FixtureState } from '../support/fixture';

async function fixture(): Promise<FixtureState> {
  return JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime/fixture-state.json'),
    'utf8'
  )) as FixtureState;
}

type RankingDetail = {
  id: string;
  versionNumber: number;
  publishedAt: string | null;
  entries: Array<{
    rankPosition: number;
    studentDisplayName: string;
    schoolName: string | null;
    scoreDisplayValue: string;
  }>;
};

async function queryDb(sql: string): Promise<string> {
  const runtime = JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime.json'),
    'utf8'
  )) as { dbContainer: string };
  return execFileSync('docker', [
    'exec', '-i', runtime.dbContainer, 'psql',
    '-At', '-U', 'postgres', '-d', 'campus_e2e', '-c', sql
  ], { encoding: 'utf8' }).trim();
}

async function execDb(sql: string): Promise<void> {
  const runtime = JSON.parse(await fs.readFile(
    path.resolve(process.cwd(), 'test-results/stage26-runtime.json'),
    'utf8'
  )) as { dbContainer: string };
  execFileSync('docker', [
    'exec', '-i', runtime.dbContainer, 'psql',
    '-U', 'postgres', '-d', 'campus_e2e', '-c', sql
  ], { encoding: 'utf8' });
}

async function createSubmittedAndApproved(
  request: Parameters<typeof apiRequest>[0],
  activityProjectId: string,
  studentId: string,
  integerValue: number,
  scoreBusinessTime: string
): Promise<string> {
  const created = await apiRequest(request, 'POST',
    `/api/v1/school-admin/activity-projects/${activityProjectId}/score-attempts`,
    { studentId, integerValue, scoreBusinessTime });
  expect(created.status()).toBe(201);
  const draft = await created.json() as { scoreAttemptId: string };

  const submitted = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/submit`);
  expect(submitted.status()).toBe(200);

  const approved = await apiRequest(request, 'POST',
    `/api/v1/school-admin/score-attempts/${draft.scoreAttemptId}/approve`);
  expect(approved.status()).toBe(200);
  return draft.scoreAttemptId;
}

async function createDraftAuthorization(
  request: Parameters<typeof apiRequest>[0],
  projectId: string,
  ruleVersionId: string,
  grades: string[],
  classNames: string[]
): Promise<string> {
  const response = await apiRequest(request, 'POST', '/api/v1/school-admin/l3-authorizations', {
    projectId,
    ruleVersionId,
    dataScope: { grades, classNames },
    allowSchoolName: false,
    allowStudentName: true
  });
  expect(response.status()).toBe(201);
  return (await response.json() as { id: string }).id;
}

async function createL3Definition(
  request: Parameters<typeof apiRequest>[0],
  projectId: string,
  ruleVersionId: string
): Promise<string> {
  const response = await apiRequest(request, 'POST', '/api/v1/super-admin/ranking-definitions', {
    name: `L3 Publication E2E ${Date.now()}`,
    projectId,
    ruleVersionId
  });
  expect(response.status()).toBe(201);
  return (await response.json() as { id: string }).id;
}

async function generatedVersionId(request: Parameters<typeof apiRequest>[0], definitionId: string): Promise<string> {
  const response = await apiRequest(request, 'POST', `/api/v1/super-admin/ranking-definitions/${definitionId}/generate`);
  expect(response.status()).toBe(200);
  const body = await response.json() as { rankingVersionId: string; status: string; entryCount: number };
  expect(body.status).toBe('GENERATED');
  expect(body.entryCount).toBe(1);
  return body.rankingVersionId;
}

async function snapshotCounts(versionId: string): Promise<{ entries: number; sources: number }> {
  const entries = Number(await queryDb(`
    SELECT COUNT(*) FROM ranking_entries WHERE version_id = '${versionId}'
  `));
  const sources = Number(await queryDb(`
    SELECT COUNT(*)
    FROM ranking_entry_score_sources s
    JOIN ranking_entries e ON e.id = s.entry_id
    WHERE e.version_id = '${versionId}'
  `));
  return { entries, sources };
}

async function selectedScoreAttempt(versionId: string): Promise<string> {
  return queryDb(`
    SELECT s.score_attempt_id
    FROM ranking_entry_score_sources s
    JOIN ranking_entries e ON e.id = s.entry_id
    WHERE e.version_id = '${versionId}'
    ORDER BY e.rank_position ASC, e.id ASC
    LIMIT 1
  `);
}

test.describe.configure({ mode: 'serial' });

test('L3 publication API publishes generated snapshots and preserves public visibility', async ({ request }) => {
  const ids = await fixture();
  const superAdminContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  const studentContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });
  const anonymousContext = await playwrightRequest.newContext({ baseURL: 'http://127.0.0.1:5173' });

  let authorizationId = '';
  let definitionId = '';
  let versionOneId = '';
  let versionTwoId = '';
  let scoreAttemptOne = '';
  let scoreAttemptTwo = '';
  let scoreAttemptThree = '';

  try {
    expect((await loginApi(request, actors.schoolAdminA)).status()).toBe(200);
    expect((await loginApi(superAdminContext, actors.superAdmin)).status()).toBe(200);
    expect((await loginApi(studentContext, actors.studentA)).status()).toBe(200);

    scoreAttemptOne = await createSubmittedAndApproved(
      request,
      ids.l2BestActivityProject,
      ids.studentA,
      90,
      '2026-08-28T12:00:00Z'
    );
    scoreAttemptTwo = await createSubmittedAndApproved(
      request,
      ids.l2BestActivityProject,
      ids.studentA,
      95,
      '2026-08-28T12:05:00Z'
    );

    const projectResponse = await apiRequest(request, 'GET', `/api/v1/challenge-projects/${ids.l2BestProject}`);
    expect(projectResponse.status()).toBe(200);
    const project = await projectResponse.json() as { currentRuleVersionId: string };

    authorizationId = await createDraftAuthorization(
      request,
      ids.l2BestProject,
      project.currentRuleVersionId,
      ['2026'],
      ['E2E-A']
    );

    expect((await apiRequest(request, 'POST',
      `/api/v1/school-admin/l3-authorizations/${authorizationId}/submit`)).status()).toBe(200);
    expect((await apiRequest(superAdminContext, 'POST',
      `/api/v1/super-admin/l3-authorizations/${authorizationId}/approve`,
      { comment: 'approved for L3 publication E2E' })).status()).toBe(200);

    definitionId = await createL3Definition(superAdminContext, ids.l2BestProject, project.currentRuleVersionId);
    versionOneId = await generatedVersionId(superAdminContext, definitionId);
    const versionOneCounts = await snapshotCounts(versionOneId);

    expect((await apiRequest(anonymousContext, 'GET', `/api/v1/public/rankings/${definitionId}`)).status()).toBe(404);
    expect((await apiRequest(studentContext, 'POST',
      `/api/v1/super-admin/ranking-definitions/${definitionId}/versions/${versionOneId}/publish`)).status()).toBe(403);
    expect((await apiRequest(request, 'POST',
      `/api/v1/super-admin/ranking-definitions/${definitionId}/versions/${versionOneId}/publish`)).status()).toBe(403);

    const publishOne = await apiRequest(superAdminContext, 'POST',
      `/api/v1/super-admin/ranking-definitions/${definitionId}/versions/${versionOneId}/publish`);
    expect(publishOne.status()).toBe(200);
    const publishedOne = await publishOne.json() as {
      currentVersionId: string;
      previousCurrentVersionId: string | null;
      status: string;
      publishedAt: string;
    };
    expect(publishedOne.status).toBe('PUBLISHED');
    expect(publishedOne.previousCurrentVersionId).toBeNull();
    expect(publishedOne.currentVersionId).toBe(versionOneId);
    expect(publishedOne.publishedAt).not.toBeNull();

    expect(await queryDb(`SELECT version_status FROM ranking_versions WHERE id = '${versionOneId}'`)).toBe('PUBLISHED');
    expect(await queryDb(`
      SELECT current_version_id FROM ranking_definitions WHERE id = '${definitionId}'
    `)).toBe(versionOneId);
    expect(await snapshotCounts(versionOneId)).toEqual(versionOneCounts);
    expect(await selectedScoreAttempt(versionOneId)).toBe(scoreAttemptTwo);

    const publicDetailOne = await apiRequest(anonymousContext, 'GET', `/api/v1/public/rankings/${definitionId}`);
    expect(publicDetailOne.status()).toBe(200);
    const detailOne = await publicDetailOne.json() as RankingDetail;
    expect(detailOne.versionNumber).toBe(1);
    expect(detailOne.publishedAt).not.toBeNull();
    expect(detailOne.entries).toHaveLength(1);
    expect(detailOne.entries[0].scoreDisplayValue).toBe('95');
    expect(detailOne.entries[0].schoolName).toBeNull();
    expect(detailOne.entries[0].studentDisplayName).toMatch(/^选手-/);
    expect(detailOne.entries[0].studentDisplayName).not.toContain(ids.studentA.replace(/-/g, ''));

    const publicListOne = await apiRequest(anonymousContext, 'GET', '/api/v1/public/rankings');
    expect(publicListOne.status()).toBe(200);
    const listOne = await publicListOne.json() as { items: Array<{ id: string }> };
    expect(listOne.items.map(item => item.id)).toContain(definitionId);

    expect((await apiRequest(studentContext, 'GET', `/api/v1/student/rankings/${definitionId}`)).status()).toBe(200);
    expect((await apiRequest(request, 'GET', `/api/v1/school-admin/rankings/${definitionId}`)).status()).toBe(404);

    scoreAttemptThree = await createSubmittedAndApproved(
      request,
      ids.l2BestActivityProject,
      ids.studentA,
      99,
      '2026-08-28T12:10:00Z'
    );
    versionTwoId = await generatedVersionId(superAdminContext, definitionId);
    const versionTwoCounts = await snapshotCounts(versionTwoId);

    const publishTwo = await apiRequest(superAdminContext, 'POST',
      `/api/v1/super-admin/ranking-definitions/${definitionId}/versions/${versionTwoId}/publish`);
    expect(publishTwo.status()).toBe(200);
    const publishedTwo = await publishTwo.json() as {
      currentVersionId: string;
      previousCurrentVersionId: string | null;
      status: string;
      publishedAt: string;
    };
    expect(publishedTwo.status).toBe('PUBLISHED');
    expect(publishedTwo.previousCurrentVersionId).toBe(versionOneId);
    expect(publishedTwo.currentVersionId).toBe(versionTwoId);
    expect(publishedTwo.publishedAt).not.toBeNull();

    expect(await queryDb(`SELECT version_status FROM ranking_versions WHERE id = '${versionOneId}'`)).toBe('REPLACED');
    expect(await queryDb(`SELECT version_status FROM ranking_versions WHERE id = '${versionTwoId}'`)).toBe('PUBLISHED');
    expect(await queryDb(`
      SELECT current_version_id FROM ranking_definitions WHERE id = '${definitionId}'
    `)).toBe(versionTwoId);
    expect(await snapshotCounts(versionTwoId)).toEqual(versionTwoCounts);
    expect(await selectedScoreAttempt(versionTwoId)).toBe(scoreAttemptThree);

    const publicDetailTwo = await apiRequest(anonymousContext, 'GET', `/api/v1/public/rankings/${definitionId}`);
    expect(publicDetailTwo.status()).toBe(200);
    const detailTwo = await publicDetailTwo.json() as RankingDetail;
    expect(detailTwo.versionNumber).toBe(2);
    expect(detailTwo.publishedAt).not.toBeNull();
    expect(detailTwo.entries).toHaveLength(1);
    expect(detailTwo.entries[0].scoreDisplayValue).toBe('99');
    expect(detailTwo.entries[0].schoolName).toBeNull();
    expect(detailTwo.entries[0].studentDisplayName).toMatch(/^选手-/);
    expect(detailTwo.entries[0].studentDisplayName).not.toContain(ids.studentA.replace(/-/g, ''));

    const publicListTwo = await apiRequest(anonymousContext, 'GET', '/api/v1/public/rankings');
    expect(publicListTwo.status()).toBe(200);
    const listTwo = await publicListTwo.json() as { items: Array<{ id: string }> };
    expect(listTwo.items.map(item => item.id)).toContain(definitionId);
  } finally {
    if (definitionId) {
      await execDb(`UPDATE ranking_definitions SET current_version_id = NULL WHERE id = '${definitionId}';`);
    }
    if (versionTwoId) {
      await execDb(`
        DELETE FROM ranking_entry_score_sources
        WHERE entry_id IN (SELECT id FROM ranking_entries WHERE version_id = '${versionTwoId}');
        DELETE FROM ranking_entries WHERE version_id = '${versionTwoId}';
        DELETE FROM ranking_versions WHERE id = '${versionTwoId}';
      `);
    }
    if (versionOneId) {
      await execDb(`
        DELETE FROM ranking_entry_score_sources
        WHERE entry_id IN (SELECT id FROM ranking_entries WHERE version_id = '${versionOneId}');
        DELETE FROM ranking_entries WHERE version_id = '${versionOneId}';
        DELETE FROM ranking_versions WHERE id = '${versionOneId}';
      `);
    }
    if (definitionId) {
      await execDb(`DELETE FROM ranking_definitions WHERE id = '${definitionId}';`);
    }
    if (authorizationId) {
      await execDb(`DELETE FROM l3_authorizations WHERE id = '${authorizationId}';`);
    }
    if (scoreAttemptThree) {
      await execDb(`DELETE FROM score_review_records WHERE score_attempt_id = '${scoreAttemptThree}';`);
      await execDb(`DELETE FROM score_attempts WHERE id = '${scoreAttemptThree}';`);
    }
    if (scoreAttemptTwo) {
      await execDb(`DELETE FROM score_review_records WHERE score_attempt_id = '${scoreAttemptTwo}';`);
      await execDb(`DELETE FROM score_attempts WHERE id = '${scoreAttemptTwo}';`);
    }
    if (scoreAttemptOne) {
      await execDb(`DELETE FROM score_review_records WHERE score_attempt_id = '${scoreAttemptOne}';`);
      await execDb(`DELETE FROM score_attempts WHERE id = '${scoreAttemptOne}';`);
    }
    await superAdminContext.dispose();
    await studentContext.dispose();
    await anonymousContext.dispose();
  }
});
