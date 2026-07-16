# 聚合模型矩阵

> **审计日期**: 2026-07-15 | **任务**: TASK-DOMAIN-MODEL-001

---

## 13个聚合根概览

| # | 模块 | 聚合根 | 状态机 | 内部实体 | 值对象 | ADR | 实现状态 |
|:-:|------|------|------|------|------|:--:|:--:|
| 1 | identity | User | account (4) | — | — | — | PENDING |
| 2 | school | School | school (4) | — | — | — | PENDING |
| 3 | school | SchoolRegistration | school_registration (6) | — | — | — | PENDING |
| 4 | project | ChallengeProject | challenge_project (3) | ProjectRuleVersion, ProjectRuleCompatibility | ProjectName, ProjectCategory, ScoreConfig, ChallengeProjectId | — | **PILOT_COMPLETED** |
| 5 | activity | ActivityApplication | activity_application (5) | 申请版本记录 | — | ADR-001 | PENDING |
| 6 | activity | Activity | execution (5) + public (7) | ActivityProject, ResponsibleTeacher, Participant | — | — | PENDING |
| 7 | score | ScoreAttempt | score (5) | ScoreReviewRecord | — | ADR-002 | PENDING |
| 8 | ranking | RankingDefinition | — (ranking_version: 7) | RankingVersion | — | — | PENDING |
| 9 | ranking | L3Authorization | l3_authorization (6) | 授权范围配置 | — | ADR-005 | PENDING |
| 10 | appeal | ScoreAppeal | appeal (13) | AppealRecord | — | — | PENDING |
| 11 | media | Media | internal (5) + public (6) | MediaReviewRecord | — | ADR-003 | PENDING |
| 12 | result | ActivityResult | internal (3) + public (7) | ResultVersion | — | ADR-004 | PENDING |
| 13 | feedback | Feedback | feedback (5) | — | — | — | PENDING |

---

## 试点聚合: ChallengeProject

### 值对象

| 值对象 | 类型 | 约束 |
|------|------|------|
| ChallengeProjectId | `record(UUID)` | 非空 |
| ProjectName | `record(String)` | 非空, ≤200字符 |
| ProjectCategory | `record(String)` | 非空, ≤64字符 |
| ScoreConfig | `record(...)` | storageType/indicatorType/comparisonDirection/effectiveScoreRule非空 |
| ScoreStorageType | enum | INTEGER, DECIMAL, DURATION, GRADE |
| ScoreIndicatorType | enum | NUMERIC, DURATION_MS, GRADE_LEVEL |
| ComparisonDirection | enum | HIGHER_BETTER, LOWER_BETTER, GRADE_ORDER, NO_RANKING |
| ProjectStatus | enum | DRAFT, PUBLISHED, ARCHIVED |

### 状态转换

| 当前状态 | 操作 | 目标状态 | 前置条件 |
|------|------|------|------|
| DRAFT | publish() | PUBLISHED | — |
| PUBLISHED | archive() | ARCHIVED | — |
| ARCHIVED | publish() | PUBLISHED | — |

### 领域事件

| 事件 | 触发时机 |
|------|------|
| ChallengeProjectCreated | 创建时 |
| ProjectPublished | publish()成功时 |
| ProjectArchived | archive()成功时 |

### 领域异常

| 异常 | 触发条件 |
|------|------|
| InvalidProjectStateTransitionException | 非法状态转换 |

### 测试覆盖(17项)

- ProjectName: 5项(有效/空/空串/超长/值相等)
- ScoreConfig: 4项(有效INTEGER/DURATION/DECIMAL/拒绝null)
- 创建: 1项(DRAFT状态+Created事件)
- 状态转换: 6项(发布/归档/重新发布/拒绝直接归档/Published事件/Archived事件)
- 集合保护: 1项(domainEvents不可修改)

---

## 17个延期表分类

| 表 | 分类 | 所属聚合根 | 本阶段是否需要领域模型 |
|------|------|------|:--:|
| student_profiles | AGGREGATE_CHILD_ENTITY | User | 否 |
| teacher_profiles | AGGREGATE_CHILD_ENTITY | User | 否 |
| project_rule_versions | AGGREGATE_CHILD_ENTITY | ChallengeProject | 是(下批次) |
| project_rule_compatibilities | RELATION_ENTITY | ChallengeProject | 是(下批次) |
| activity_projects | AGGREGATE_CHILD_ENTITY | Activity | 否 |
| responsible_teachers | RELATION_ENTITY | Activity | 否 |
| activity_participants | RELATION_ENTITY | Activity | 否 |
| score_review_records | HISTORY_RECORD | ScoreAttempt | 否 |
| score_correction_records | HISTORY_RECORD | ScoreAttempt | 否 |
| abnormal_score_entries | AGGREGATE_CHILD_ENTITY | ScoreAttempt | 否 |
| ranking_versions | VERSION_SNAPSHOT | RankingDefinition | 否 |
| ranking_entries | VERSION_SNAPSHOT | RankingDefinition | 否 |
| ranking_entry_score_sources | RELATION_ENTITY | RankingDefinition | 否 |
| appeal_records | HISTORY_RECORD | ScoreAppeal | 否 |
| media_review_records | HISTORY_RECORD | Media | 否 |
| result_versions | VERSION_SNAPSHOT | ActivityResult | 否 |
| task_records | TECHNICAL_RECORD | — | 否 |
