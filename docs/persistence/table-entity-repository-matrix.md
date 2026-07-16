# 表-Entity-Repository 完整映射矩阵

> **审计日期**: 2026-07-15 | **任务**: TASK-PERSISTENCE-HARDENING-001 | **状态**: COMPLETED

---

## 最终统计

| 分类 | 数量 |
|------|:--:|
| 业务表（Migration创建） | **33** |
| 技术基础设施表 | **3** (spring_session, spring_session_attributes, flyway_schema_history) |
| 总计（public schema） | **36** |
| Entity映射 | **16** |
| Repository | **13** |
| 未映射业务表（有意延期） | **17** |
| UNMAPPED_BLOCKER | **0** |
| 聚合根无Repository | **0** |

---

## 完整矩阵（33张业务表）

| # | 数据库表 | 模块 | Migration | 聚合根 | 当前映射方式 | Entity | Repository | 状态 |
|:-:|------|------|-----------|:--:|------|------|------|------|
| 1 | users | identity | V001 | ✅ | @Entity | UserEntity | UserRepository | MAPPED_ENTITY |
| 2 | schools | school | V002 | ✅ | @Entity | SchoolEntity | SchoolRepository | MAPPED_ENTITY |
| 3 | school_registrations | school | V002 | ✅ | @Entity | SchoolRegistrationEntity | SchoolRegistrationRepository | MAPPED_ENTITY |
| 4 | school_memberships | identity | V003 | — | @Entity | SchoolMembershipEntity | — | MAPPED_ENTITY (内部实体) |
| 5 | student_profiles | identity | V003 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 6 | teacher_profiles | identity | V003 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 7 | challenge_projects | project | V004 | ✅ | @Entity | ChallengeProjectEntity | ChallengeProjectRepository | MAPPED_ENTITY |
| 8 | project_rule_versions | project | V004 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 9 | project_rule_compatibilities | project | V004 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 10 | activity_applications | activity | V005 | ✅ | @Entity | ActivityApplicationEntity | ActivityApplicationRepository | MAPPED_ENTITY |
| 11 | activities | activity | V005 | ✅ | @Entity | ActivityEntity | ActivityRepository | MAPPED_ENTITY |
| 12 | activity_projects | activity | V005 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 13 | responsible_teachers | activity | V005 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 14 | activity_participants | activity | V005 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 15 | score_attempts | score | V006 | ✅ | @Entity | ScoreAttemptEntity | ScoreAttemptRepository | MAPPED_ENTITY |
| 16 | score_review_records | score | V006 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 17 | score_correction_records | score | V006 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 18 | abnormal_score_entries | score | V006 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 19 | ranking_definitions | ranking | V007 | ✅ | @Entity | RankingDefinitionEntity | RankingDefinitionRepository | MAPPED_ENTITY |
| 20 | ranking_versions | ranking | V007 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 21 | ranking_entries | ranking | V007 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 22 | ranking_entry_score_sources | ranking | V007 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 23 | l3_authorizations | ranking | V007 | ✅ | @Entity | L3AuthorizationEntity | L3AuthorizationRepository | MAPPED_ENTITY |
| 24 | score_appeals | appeal | V008 | ✅ | @Entity | ScoreAppealEntity | ScoreAppealRepository | MAPPED_ENTITY |
| 25 | appeal_records | appeal | V008 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 26 | media | media | V009 | ✅ | @Entity | MediaEntity | MediaRepository | MAPPED_ENTITY |
| 27 | media_review_records | media | V009 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 28 | activity_results | result | V010 | ✅ | @Entity | ActivityResultEntity | ActivityResultRepository | MAPPED_ENTITY |
| 29 | result_versions | result | V010 | — | — | — | — | AGGREGATE_INTERNAL_ENTITY_PENDING |
| 30 | feedbacks | feedback | V011 | ✅ | @Entity | FeedbackEntity | FeedbackRepository | MAPPED_ENTITY |
| 31 | notifications | notification | V012 | — | @Entity | NotificationEntity | — | INFRASTRUCTURE_TABLE |
| 32 | audit_records | audit | V013 | — | @Entity | AuditRecordEntity | — | AUDIT_APPEND_ONLY_TABLE |
| 33 | task_records | infrastructure | V014 | — | — | — | — | INFRASTRUCTURE_TABLE |

---

## 聚合根Entity清单（13个，均有Repository）

| # | Entity | 模块 | Repository | 聚合根 |
|:-:|------|------|------|:--:|
| 1 | UserEntity | identity | UserRepository | ✅ |
| 2 | SchoolEntity | school | SchoolRepository | ✅ |
| 3 | SchoolRegistrationEntity | school | SchoolRegistrationRepository | ✅ |
| 4 | ChallengeProjectEntity | project | ChallengeProjectRepository | ✅ |
| 5 | ActivityApplicationEntity | activity | ActivityApplicationRepository | ✅ |
| 6 | ActivityEntity | activity | ActivityRepository | ✅ |
| 7 | ScoreAttemptEntity | score | ScoreAttemptRepository | ✅ |
| 8 | RankingDefinitionEntity | ranking | RankingDefinitionRepository | ✅ |
| 9 | L3AuthorizationEntity | ranking | L3AuthorizationRepository | ✅ |
| 10 | ScoreAppealEntity | appeal | ScoreAppealRepository | ✅ |
| 11 | MediaEntity | media | MediaRepository | ✅ |
| 12 | ActivityResultEntity | result | ActivityResultRepository | ✅ |
| 13 | FeedbackEntity | feedback | FeedbackRepository | ✅ |

---

## 非聚合根Entity（3个，无Repository）

| Entity | 理由 |
|------|------|
| SchoolMembershipEntity | User/School聚合内部实体 |
| NotificationEntity | 基础设施表，系统事件写入，查询按需使用JdbcTemplate |
| AuditRecordEntity | 追加式审计日志，拦截器写入 |

---

## 延期内部实体（17个）

均为聚合内部实体，属于各自的聚合根管理。在`TASK-DOMAIN-MODEL-001`中实现业务逻辑时映射为JPA内部关联。

| 表 | 所属聚合根 | 首次需要该表的用例 | 当前不映射是否阻塞 |
|------|------|------|:--:|
| student_profiles | User | 学生档案管理 | 否 |
| teacher_profiles | User | 教师档案管理 | 否 |
| project_rule_versions | ChallengeProject | 规则版本发布 | 否 |
| project_rule_compatibilities | ChallengeProject | 规则兼容性管理 | 否 |
| activity_projects | Activity | 活动项目配置 | 否 |
| responsible_teachers | Activity | 教师分配 | 否 |
| activity_participants | Activity | 参与者管理 | 否 |
| score_review_records | ScoreAttempt | 成绩审核 | 否 |
| score_correction_records | ScoreAttempt | 成绩纠正 | 否 |
| abnormal_score_entries | ScoreAttempt | 异常成绩录入 | 否 |
| ranking_versions | RankingDefinition | 排行榜版本生成 | 否 |
| ranking_entries | RankingDefinition | 排行榜快照 | 否 |
| ranking_entry_score_sources | RankingDefinition | 排行榜来源追溯 | 否 |
| appeal_records | ScoreAppeal | 申诉处理 | 否 |
| media_review_records | Media | 媒体审核 | 否 |
| result_versions | ActivityResult | 成果版本管理 | 否 |
| task_records | — (基础设施) | 异步任务 | 否 |

---

## 模块覆盖（13/13，均有明确设计决策）

| 模块 | 业务表 | Entity | Repository | 状态 |
|------|:--:|:--:|:--:|:--:|
| identity | 3 | 2 | 1 | ✅ (内部实体无独立Repo) |
| school | 2 | 2 | 2 | ✅ |
| project | 3 | 1 | 1 | ✅ (内部实体延期) |
| activity | 5 | 2 | 2 | ✅ (内部实体延期) |
| score | 4 | 1 | 1 | ✅ (内部实体延期) |
| ranking | 5 | 2 | 2 | ✅ (内部实体延期) |
| appeal | 2 | 1 | 1 | ✅ (内部实体延期) |
| media | 2 | 1 | 1 | ✅ (内部实体延期) |
| result | 2 | 1 | 1 | ✅ (内部实体延期) |
| feedback | 1 | 1 | 1 | ✅ |
| notification | 1 | 1 | 0 | ✅ (基础设施表) |
| platform | 0 | 0 | 0 | ✅ (无独立数据) |
| audit | 1 | 1 | 0 | ✅ (审计追加表) |

---

## UNMAPPED_BLOCKER: 0

旧矩阵报告的5个UNMAPPED_BLOCKER（L3Authorization、ScoreAppeal、Media、ActivityResult、Feedback）的Repository已在前期创建，审计确认全部存在并通过了13项真实往返测试。

---

## 变更记录

| 日期 | 变更 | 原因 |
|------|------|------|
| 2026-07-15 | 更正Repository数量：8→13 | 5个Repository在旧矩阵编写后已创建 |
| 2026-07-15 | 更正UNMAPPED_BLOCKER：5→0 | 同上 |
| 2026-07-15 | ChallengeProjectEntity补充5个字段 | Entity与表schema不一致 |
| 2026-07-15 | 新增5个持久化测试类 | 阶段B测试补充 |
