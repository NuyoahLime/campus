\set ON_ERROR_STOP on
\set school_a '10000000-0000-0000-0000-000000000001'
\set school_b '10000000-0000-0000-0000-000000000002'
\set admin_a '20000000-0000-0000-0000-000000000001'
\set admin_b '20000000-0000-0000-0000-000000000002'
\set student_a '20000000-0000-0000-0000-000000000003'
\set student_b '20000000-0000-0000-0000-000000000004'
\set student_other '20000000-0000-0000-0000-000000000005'
\set super_admin '20000000-0000-0000-0000-000000000006'
\set teacher '20000000-0000-0000-0000-000000000007'
\set admin_a_membership '30000000-0000-0000-0000-000000000001'
\set admin_b_membership '30000000-0000-0000-0000-000000000002'
\set student_a_membership '30000000-0000-0000-0000-000000000003'
\set student_b_membership '30000000-0000-0000-0000-000000000004'
\set student_other_membership '30000000-0000-0000-0000-000000000005'
\set teacher_membership '30000000-0000-0000-0000-000000000006'
\set lifecycle_project '40000000-0000-0000-0000-000000000001'
\set empty_project '40000000-0000-0000-0000-000000000002'
\set best_project '40000000-0000-0000-0000-000000000003'
\set last_project '40000000-0000-0000-0000-000000000004'
\set designated_project '40000000-0000-0000-0000-000000000005'
\set api_project '40000000-0000-0000-0000-000000000006'
\set other_project '40000000-0000-0000-0000-000000000007'
\set lifecycle_rule '50000000-0000-0000-0000-000000000001'
\set empty_rule '50000000-0000-0000-0000-000000000002'
\set best_rule '50000000-0000-0000-0000-000000000003'
\set last_rule '50000000-0000-0000-0000-000000000004'
\set designated_rule '50000000-0000-0000-0000-000000000005'
\set api_rule '50000000-0000-0000-0000-000000000006'
\set other_rule '50000000-0000-0000-0000-000000000007'
\set activity_lifecycle '60000000-0000-0000-0000-000000000001'
\set activity_best '60000000-0000-0000-0000-000000000002'
\set activity_last '60000000-0000-0000-0000-000000000003'
\set activity_designated '60000000-0000-0000-0000-000000000004'
\set activity_api '60000000-0000-0000-0000-000000000005'
\set activity_other '60000000-0000-0000-0000-000000000006'
\set lifecycle_ap '70000000-0000-0000-0000-000000000001'
\set empty_ap '70000000-0000-0000-0000-000000000002'
\set best_ap '70000000-0000-0000-0000-000000000003'
\set last_ap '70000000-0000-0000-0000-000000000004'
\set designated_ap '70000000-0000-0000-0000-000000000005'
\set api_ap '70000000-0000-0000-0000-000000000006'
\set other_ap '70000000-0000-0000-0000-000000000007'
\set best_old '80000000-0000-0000-0000-000000000001'
\set best_current '80000000-0000-0000-0000-000000000002'
\set last_old '80000000-0000-0000-0000-000000000003'
\set last_current '80000000-0000-0000-0000-000000000004'
\set designated_first '80000000-0000-0000-0000-000000000005'
\set designated_second '80000000-0000-0000-0000-000000000006'
\set api_pending '80000000-0000-0000-0000-000000000007'
\set other_attempt '80000000-0000-0000-0000-000000000008'

INSERT INTO users(id, username, password_hash, account_status, platform_role) VALUES
(:'admin_a', 'e2e-admin-a', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL),
(:'admin_b', 'e2e-admin-b', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL),
(:'student_a', 'e2e-student-a', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL),
(:'student_b', 'e2e-student-b', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL),
(:'student_other', 'e2e-student-other-school', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL),
(:'super_admin', 'e2e-super-admin', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', 'SUPER_ADMIN'),
(:'teacher', 'e2e-teacher', '$2b$12$uE94zT9u/dVTn08W8Zu2.u0NOffjc5TyXh4qsauPzXV2duiKgsJsq', 'NORMAL', NULL);

INSERT INTO schools(id, name, unified_code_type, unified_code, internal_code, school_type, region, address, contact_name, contact_phone, contact_email, school_status) VALUES
(:'school_a', 'E2E School A', 'USCC', 'E2E-SCHOOL-A', 'E2E-A', 'UNIVERSITY', 'E2E', 'E2E', 'E2E', '10000000000', 'a@example.test', 'NORMAL'),
(:'school_b', 'E2E School B', 'USCC', 'E2E-SCHOOL-B', 'E2E-B', 'UNIVERSITY', 'E2E', 'E2E', 'E2E', '10000000001', 'b@example.test', 'NORMAL');

INSERT INTO school_memberships(id, user_id, school_id, role_in_school, status) VALUES
(:'admin_a_membership', :'admin_a', :'school_a', 'SCHOOL_ADMIN', 'ACTIVE'),
(:'admin_b_membership', :'admin_b', :'school_b', 'SCHOOL_ADMIN', 'ACTIVE'),
(:'student_a_membership', :'student_a', :'school_a', 'STUDENT', 'ACTIVE'),
(:'student_b_membership', :'student_b', :'school_a', 'STUDENT', 'ACTIVE'),
(:'student_other_membership', :'student_other', :'school_b', 'STUDENT', 'ACTIVE'),
(:'teacher_membership', :'teacher', :'school_a', 'TEACHER', 'ACTIVE');

INSERT INTO student_profiles(id, membership_id, grade, class_name, student_number) VALUES
('31000000-0000-0000-0000-000000000001', :'student_a_membership', '2026', 'E2E-A', 'E2E-STUDENT-A'),
('31000000-0000-0000-0000-000000000002', :'student_b_membership', '2026', 'E2E-B', 'E2E-STUDENT-B'),
('31000000-0000-0000-0000-000000000003', :'student_other_membership', '2026', 'E2E-OTHER', 'E2E-STUDENT-OTHER');

INSERT INTO challenge_projects(id, name, category, score_storage_type, score_indicator_type, comparison_direction, score_unit, effective_score_rule, project_status, current_rule_version_id) VALUES
(:'lifecycle_project', 'E2E Lifecycle Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'PUBLISHED', NULL),
(:'empty_project', 'E2E Empty History Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'PUBLISHED', NULL),
(:'best_project', 'E2E Best Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'PUBLISHED', NULL),
(:'last_project', 'E2E Last Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'LAST', 'PUBLISHED', NULL),
(:'designated_project', 'E2E Designated Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'ADMIN_DESIGNATED', 'PUBLISHED', NULL),
(:'api_project', 'E2E API Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'ADMIN_DESIGNATED', 'PUBLISHED', NULL),
(:'other_project', 'E2E Other School Project', 'SPORTS', 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'PUBLISHED', NULL);

INSERT INTO project_rule_versions(id, project_id, version_number, score_storage_type, score_indicator_type, comparison_direction, score_unit, effective_score_rule, rules_text, created_by) VALUES
(:'lifecycle_rule', :'lifecycle_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'E2E lifecycle rules', :'admin_a'),
(:'empty_rule', :'empty_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'E2E empty history rules', :'admin_a'),
(:'best_rule', :'best_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'E2E best rules', :'admin_a'),
(:'last_rule', :'last_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'LAST', 'E2E last rules', :'admin_a'),
(:'designated_rule', :'designated_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'ADMIN_DESIGNATED', 'E2E designated rules', :'admin_a'),
(:'api_rule', :'api_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'ADMIN_DESIGNATED', 'E2E api rules', :'admin_a'),
(:'other_rule', :'other_project', 1, 'INTEGER', 'NUMERIC', 'HIGHER_BETTER', 'points', 'BEST', 'E2E other rules', :'admin_b');

UPDATE challenge_projects SET current_rule_version_id = CASE id
  WHEN :'lifecycle_project'::uuid THEN :'lifecycle_rule'::uuid
  WHEN :'empty_project'::uuid THEN :'empty_rule'::uuid
  WHEN :'best_project'::uuid THEN :'best_rule'::uuid
  WHEN :'last_project'::uuid THEN :'last_rule'::uuid
  WHEN :'designated_project'::uuid THEN :'designated_rule'::uuid
  WHEN :'api_project'::uuid THEN :'api_rule'::uuid
  WHEN :'other_project'::uuid THEN :'other_rule'::uuid
END
WHERE id IN (:'lifecycle_project', :'empty_project', :'best_project', :'last_project',
             :'designated_project', :'api_project', :'other_project');

INSERT INTO activities(id, school_id, title, execution_status, public_status, created_by) VALUES
(:'activity_lifecycle', :'school_a', 'ACTIVITY_LIFECYCLE', 'PUBLISHED', 'PUBLIC', :'admin_a'),
(:'activity_best', :'school_a', 'ACTIVITY_BEST', 'PUBLISHED', 'PUBLIC', :'admin_a'),
(:'activity_last', :'school_a', 'ACTIVITY_LAST', 'PUBLISHED', 'PUBLIC', :'admin_a'),
(:'activity_designated', :'school_a', 'ACTIVITY_ADMIN_DESIGNATED', 'PUBLISHED', 'PUBLIC', :'admin_a'),
(:'activity_api', :'school_a', 'ACTIVITY_API', 'PUBLISHED', 'PUBLIC', :'admin_a'),
(:'activity_other', :'school_b', 'ACTIVITY_OTHER_SCHOOL', 'PUBLISHED', 'PUBLIC', :'admin_b');

INSERT INTO activity_projects(id, activity_id, project_id, rule_version_id) VALUES
(:'lifecycle_ap', :'activity_lifecycle', :'lifecycle_project', :'lifecycle_rule'),
(:'empty_ap', :'activity_lifecycle', :'empty_project', :'empty_rule'),
(:'best_ap', :'activity_best', :'best_project', :'best_rule'),
(:'last_ap', :'activity_last', :'last_project', :'last_rule'),
(:'designated_ap', :'activity_designated', :'designated_project', :'designated_rule'),
(:'api_ap', :'activity_api', :'api_project', :'api_rule'),
(:'other_ap', :'activity_other', :'other_project', :'other_rule');

INSERT INTO activity_participants(id, activity_id, student_membership_id) VALUES
('71000000-0000-0000-0000-000000000101', :'activity_lifecycle', :'student_a_membership'),
('71000000-0000-0000-0000-000000000102', :'activity_best', :'student_a_membership'),
('71000000-0000-0000-0000-000000000103', :'activity_last', :'student_a_membership'),
('71000000-0000-0000-0000-000000000104', :'activity_designated', :'student_a_membership'),
('71000000-0000-0000-0000-000000000105', :'activity_api', :'student_a_membership'),
('71000000-0000-0000-0000-000000000106', :'activity_other', :'student_other_membership');

INSERT INTO score_attempts(id, school_id, activity_project_id, student_id, attempt_number, score_storage_type, score_value, score_status, is_current_effective, entered_by, score_business_time) VALUES
(:'best_old', :'school_a', :'best_ap', :'student_a', 1, 'INTEGER', 10, 'APPROVED', false, :'admin_a', now() - interval '2 minutes'),
(:'best_current', :'school_a', :'best_ap', :'student_a', 2, 'INTEGER', 20, 'APPROVED', true, :'admin_a', now() - interval '1 minutes'),
(:'last_old', :'school_a', :'last_ap', :'student_a', 1, 'INTEGER', 5, 'APPROVED', false, :'admin_a', now() - interval '2 minutes'),
(:'last_current', :'school_a', :'last_ap', :'student_a', 2, 'INTEGER', 7, 'APPROVED', true, :'admin_a', now() - interval '1 minutes'),
(:'designated_first', :'school_a', :'designated_ap', :'student_a', 1, 'INTEGER', 30, 'APPROVED', false, :'admin_a', now() - interval '2 minutes'),
(:'designated_second', :'school_a', :'designated_ap', :'student_a', 2, 'INTEGER', 40, 'APPROVED', false, :'admin_a', now() - interval '1 minutes'),
(:'api_pending', :'school_a', :'api_ap', :'student_a', 1, 'INTEGER', 12, 'PENDING_REVIEW', false, :'admin_a', now()),
(:'other_attempt', :'school_b', :'other_ap', :'student_other', 1, 'INTEGER', 99, 'APPROVED', true, :'admin_b', now());

SELECT 'FIXTURE_STATE=' || json_build_object(
  'schoolA', :'school_a', 'schoolB', :'school_b',
  'activityLifecycle', :'activity_lifecycle', 'activityBest', :'activity_best',
  'activityLast', :'activity_last', 'activityAdminDesignated', :'activity_designated',
  'activityOtherSchool', :'activity_other', 'activityApi', :'activity_api',
  'lifecycleProject', :'lifecycle_project', 'emptyHistoryProject', :'empty_project',
  'bestProject', :'best_project', 'lastProject', :'last_project', 'designatedProject', :'designated_project',
  'apiProject', :'api_project', 'lifecycleActivityProject', :'lifecycle_ap',
  'emptyHistoryActivityProject', :'empty_ap', 'bestActivityProject', :'best_ap',
  'lastActivityProject', :'last_ap', 'designatedActivityProject', :'designated_ap',
  'apiActivityProject', :'api_ap', 'otherSchoolActivityProject', :'other_ap',
  'bestOldAttempt', :'best_old', 'bestCurrentAttempt', :'best_current',
  'lastOldAttempt', :'last_old', 'lastCurrentAttempt', :'last_current',
  'designatedFirstAttempt', :'designated_first', 'designatedSecondAttempt', :'designated_second',
  'apiPendingAttempt', :'api_pending', 'otherSchoolAttempt', :'other_attempt'
)::text;
