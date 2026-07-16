# TASK-PERSISTENCE-HARDENING-001

> **开始日期**: 2026-07-15 | **分支**: design/v1.2-technical-stack

---

## 任务目标

核实并强化持久化层，确认：
- 所有业务表有明确持久化策略
- Entity覆盖与数据库结构一致
- Repository与聚合边界一致
- 未映射表有明确延期依据
- 持久化专项测试真实存在
- Entity可在PostgreSQL 18.4中完成真实读写

---

## 原始状态

| 项目 | 旧矩阵报告值 | 实际值 |
|------|:--:|:--:|
| 业务表 | 33 | **33** |
| Entity | 16 | **16** |
| Repository | 8 | **13** |
| 已映射表 | 16 | **16** (通过Entity) |
| UNMAPPED_BLOCKER | 5 | **0** |
| 聚合根无Repository | 5 | **0** |

**关键发现**: 旧矩阵报告"8个Repository、5个UNMAPPED_BLOCKER"已过时。5个被标记为缺失的Repository（L3AuthorizationRepository、ScoreAppealRepository、MediaRepository、ActivityResultRepository、FeedbackRepository）全部存在。

---

## 阶段A：只读审计

### A.1 工作区安全检查

```text
Branch: design/v1.2-technical-stack
HEAD: aac6087 docs: accept v1.2 aggregate boundary decisions
工作区: 全部文件未跟踪(untracked)，无未提交修改
git diff: 空
```

### A.2 Migration完整性

| 文件 | 大小 | 状态 |
|------|------|:--:|
| V001__create_identity_users.sql | ~ | UNCHANGED |
| V002__create_school_domain.sql | ~ | UNCHANGED |
| V003__create_identity_memberships_and_profiles.sql | ~ | UNCHANGED |
| V004__create_project_domain.sql | ~ | UNCHANGED |
| V005__create_activity_domain.sql | ~ | UNCHANGED |
| V006__create_score_domain.sql | ~ | UNCHANGED |
| V007__create_ranking_domain.sql | ~ | UNCHANGED |
| V008__create_appeal_domain.sql | ~ | UNCHANGED |
| V009__create_media_domain.sql | ~ | UNCHANGED |
| V010__create_result_domain.sql | ~ | UNCHANGED |
| V011__create_feedback_domain.sql | ~ | UNCHANGED |
| V012__create_notification_domain.sql | ~ | UNCHANGED |
| V013__create_audit_domain.sql | ~ | UNCHANGED |
| V014__create_async_task_infrastructure.sql | ~ | UNCHANGED |
| V015__create_spring_session_tables.sql | ~ | UNCHANGED |

**结论**: V001-V015未修改、未新增、未删除、未重命名。✅

### A.3 数据库表枚举

**业务表 (33)**:

| # | 表名 | Migration | 模块 |
|:-:|------|-----------|------|
| 1 | users | V001 | identity |
| 2 | schools | V002 | school |
| 3 | school_registrations | V002 | school |
| 4 | school_memberships | V003 | identity |
| 5 | student_profiles | V003 | identity |
| 6 | teacher_profiles | V003 | identity |
| 7 | challenge_projects | V004 | project |
| 8 | project_rule_versions | V004 | project |
| 9 | project_rule_compatibilities | V004 | project |
| 10 | activity_applications | V005 | activity |
| 11 | activities | V005 | activity |
| 12 | activity_projects | V005 | activity |
| 13 | responsible_teachers | V005 | activity |
| 14 | activity_participants | V005 | activity |
| 15 | score_attempts | V006 | score |
| 16 | score_review_records | V006 | score |
| 17 | score_correction_records | V006 | score |
| 18 | abnormal_score_entries | V006 | score |
| 19 | ranking_definitions | V007 | ranking |
| 20 | ranking_versions | V007 | ranking |
| 21 | ranking_entries | V007 | ranking |
| 22 | ranking_entry_score_sources | V007 | ranking |
| 23 | l3_authorizations | V007 | ranking |
| 24 | score_appeals | V008 | appeal |
| 25 | appeal_records | V008 | appeal |
| 26 | media | V009 | media |
| 27 | media_review_records | V009 | media |
| 28 | activity_results | V010 | result |
| 29 | result_versions | V010 | result |
| 30 | feedbacks | V011 | feedback |
| 31 | notifications | V012 | notification |
| 32 | audit_records | V013 | audit |
| 33 | task_records | V014 | infrastructure |

**技术基础设施表 (3)**:
- spring_session (V015)
- spring_session_attributes (V015)
- flyway_schema_history (Flyway自动)

### A.4 Entity枚举

| # | Entity | 模块 | 表 | 主键 | 聚合根 |
|:-:|------|------|------|:--:|:--:|
| 1 | UserEntity | identity | users | UUID | ✅ |
| 2 | SchoolMembershipEntity | identity | school_memberships | UUID | — |
| 3 | SchoolEntity | school | schools | UUID | ✅ |
| 4 | SchoolRegistrationEntity | school | school_registrations | UUID | ✅ |
| 5 | ChallengeProjectEntity | project | challenge_projects | UUID | ✅ |
| 6 | ActivityEntity | activity | activities | UUID | ✅ |
| 7 | ActivityApplicationEntity | activity | activity_applications | UUID | ✅ |
| 8 | ScoreAttemptEntity | score | score_attempts | UUID | ✅ |
| 9 | RankingDefinitionEntity | ranking | ranking_definitions | UUID | ✅ |
| 10 | L3AuthorizationEntity | ranking | l3_authorizations | UUID | ✅ |
| 11 | ScoreAppealEntity | appeal | score_appeals | UUID | ✅ |
| 12 | MediaEntity | media | media | UUID | ✅ |
| 13 | ActivityResultEntity | result | activity_results | UUID | ✅ |
| 14 | FeedbackEntity | feedback | feedbacks | UUID | ✅ |
| 15 | NotificationEntity | notification | notifications | UUID | — |
| 16 | AuditRecordEntity | audit | audit_records | UUID | — |

**实体质量检查**:
- ✅ 所有Entity显式声明 @Entity + @Table(name = "actual_table_name")
- ✅ 所有Entity使用 UUID 主键
- ✅ 所有时间字段使用 Instant (对应 timestamptz)
- ✅ 精确数值使用 BigDecimal + precision/scale
- ✅ 无 EnumType.ORDINAL
- ✅ 无 FetchType.EAGER
- ✅ 无 CascadeType.ALL
- ✅ 无跨模块JPA实体关联（全部使用标量UUID引用）
- ✅ 无 Lombok @Data

### A.5 Repository枚举

| # | Repository | 模块 | Entity | ID类型 |
|:-:|------|------|------|:--:|
| 1 | UserRepository | identity | UserEntity | UUID |
| 2 | SchoolRepository | school | SchoolEntity | UUID |
| 3 | SchoolRegistrationRepository | school | SchoolRegistrationEntity | UUID |
| 4 | ChallengeProjectRepository | project | ChallengeProjectEntity | UUID |
| 5 | ActivityApplicationRepository | activity | ActivityApplicationEntity | UUID |
| 6 | ActivityRepository | activity | ActivityEntity | UUID |
| 7 | ScoreAttemptRepository | score | ScoreAttemptEntity | UUID |
| 8 | RankingDefinitionRepository | ranking | RankingDefinitionEntity | UUID |
| 9 | L3AuthorizationRepository | ranking | L3AuthorizationEntity | UUID |
| 10 | ScoreAppealRepository | appeal | ScoreAppealEntity | UUID |
| 11 | MediaRepository | media | MediaEntity | UUID |
| 12 | ActivityResultRepository | result | ActivityResultEntity | UUID |
| 13 | FeedbackRepository | feedback | FeedbackEntity | UUID |

**无Repository的Entity**:
- SchoolMembershipEntity → 内部实体，通过User/School聚合访问
- NotificationEntity → 基础设施表，系统写入，查询按需
- AuditRecordEntity → 审计追加表，拦截器写入

### A.6 Entity-Migration字段一致性

**发现1个不一致**:

| Entity | 缺失字段 | 数量 |
|------|------|:--:|
| ChallengeProjectEntity | venue_requirements, equipment_requirements, rules_text, grade_order, allow_tie | 5 |

所有其他Entity字段与对应表完全一致。

### A.7 聚合根与Repository一致性

旧报告标记5个"聚合根无Repository" → **全部已修复**。当前0个冲突。

NotificationEntity / AuditRecordEntity 分析:
- NotificationEntity: 非聚合根。由事件处理器创建，由收件人查询。归类 INFRASTRUCTURE_TABLE。
- AuditRecordEntity: 非聚合根。由审计拦截器追加写入。归类 AUDIT_APPEND_ONLY_TABLE。

### A.8 模块覆盖

| # | 模块 | 业务表 | Entity | Repository | 状态 |
|:-:|------|:--:|:--:|:--:|:--:|
| 1 | identity | 3 | 2 | 1 | ✅ (内部实体无独立Repo) |
| 2 | school | 2 | 2 | 2 | ✅ |
| 3 | project | 3 | 1 | 1 | ⚠️ (内部实体延期) |
| 4 | activity | 5 | 2 | 2 | ⚠️ (内部实体延期) |
| 5 | score | 4 | 1 | 1 | ⚠️ (内部实体延期) |
| 6 | ranking | 5 | 2 | 2 | ⚠️ (内部实体延期) |
| 7 | appeal | 2 | 1 | 1 | ⚠️ (内部实体延期) |
| 8 | media | 2 | 1 | 1 | ⚠️ (内部实体延期) |
| 9 | result | 2 | 1 | 1 | ⚠️ (内部实体延期) |
| 10 | feedback | 1 | 1 | 1 | ✅ |
| 11 | notification | 1 | 1 | 0 | ✅ (基础设施表) |
| 12 | platform | 0 | 0 | 0 | ✅ (无独立数据) |
| 13 | audit | 1 | 1 | 0 | ✅ (审计追加表) |

**覆盖率**: 10/13模块有Repository，3个无Repository有明确设计依据。

---

## 阶段A审计结果输出

```text
实际业务表数量 = 33
实际Entity数量 = 16
实际Repository数量 = 13
已映射表数量 = 16 (通过Entity)
未映射表数量 = 17
明确可延期表数量 = 17
阻塞表数量 = 0 (旧5个UNMAPPED_BLOCKER已全部修复)
聚合根与Repository冲突数量 = 0 (旧5个冲突已全部修复)
未覆盖模块 = 3 (notification/platform/audit，均有设计依据)
Entity字段不一致 = 1 (ChallengeProjectEntity缺失5个字段)
```

---

## 阶段B最小修改计划

### 批次1: 文档更新
- 更新 table-entity-repository-matrix.md
- 本任务记录文件

### 批次2: ChallengeProjectEntity字段补充
- 添加 venue_requirements, equipment_requirements, rules_text, grade_order, allow_tie
- 对应表: challenge_projects
- 聚合: ChallengeProject

### 批次3: 新增持久化测试
- PersistenceSchemaCoverageTest (新建)
- AggregateRootRepositoryConsistencyTest (新建)
- PersistenceRoundTripTest (新建 - 覆盖13个Repository)
- AggregateBoundaryPersistenceTest (新建)
- 增强 JpaMappingContextTest

---

## 执行命令记录

```bash
# 阶段A：只读审计
git status --short                           # 全部文件未跟踪
git branch --show-current                    # design/v1.2-technical-stack
git log -5 --oneline                         # 最近4次提交
git diff --stat                              # 空

# 阶段B：最小修复
# 批次2：ChallengeProjectEntity补充5个字段
./mvnw compile                               # BUILD SUCCESS
./mvnw -Dtest=ModularityTest test            # 1 test, BUILD SUCCESS

# 批次3：新增持久化测试后全量回归
./mvnw test                                  # 60 tests, BUILD SUCCESS
./mvnw verify                                # 60 tests, BUILD SUCCESS

# 业务代码边界检查
grep -r "@RestController\|@Controller\|@Service\|Dto\|Request\|Response\|UseCase\|CommandHandler\|QueryHandler" src/main/java
# 结果：0 matches（无业务代码）
```

---

## 修改文件列表

| 文件 | 操作 | 说明 |
|------|:--:|------|
| `docs/persistence/TASK-PERSISTENCE-HARDENING-001.md` | 新建 | 本任务记录 |
| `docs/persistence/table-entity-repository-matrix.md` | 更新 | 更正Repository数量8→13，BLOCKER 5→0 |
| `src/main/java/.../project/internal/persistence/ChallengeProjectEntity.java` | 修改 | 补充venue_requirements等5个字段 |
| `src/test/java/.../PostgreSqlIntegrationTestSupport.java` | 新建 | 共享测试基类 |
| `src/test/java/.../PersistenceSchemaCoverageTest.java` | 新建 | Schema覆盖测试（7项） |
| `src/test/java/.../AggregateRootRepositoryConsistencyTest.java` | 新建 | 聚合根一致性测试（5项） |
| `src/test/java/.../PersistenceRoundTripTest.java` | 新建 | 13个Repository真实往返测试（13项） |
| `src/test/java/.../AggregateBoundaryPersistenceTest.java` | 新建 | 聚合边界测试（4项） |
| `src/test/java/.../JpaMappingContextTest.java` | 增强 | 从1项扩展为6项 |
| `mvnw` | 修改 | 修复JVM选项格式和堆大小 |

**未修改**: V001-V015 Migration、pom.xml依赖、模块结构、Entity（除ChallengeProjectEntity外）。

---

## 未解决风险

无阻塞性风险。

已识别需要后续关注的项：
- 17个内部实体待TASK-DOMAIN-MODEL-001中映射（均为聚合内部实体，有明确生命周期归属）
- NotificationEntity和AuditRecordEntity无Repository（设计决策，由系统写入/拦截器管理）
- challenge_projects表与project_rule_versions之间存在循环外键（数据库设计已知，由RESTRICT保证一致性）

---

## 最终结论

**TASK-PERSISTENCE-HARDENING-001 = COMPLETED**

所有检查项通过：
- ✅ 33张业务表全部有明确持久化处理状态
- ✅ 17张延期映射均有聚合和生命周期依据
- ✅ UNMAPPED_BLOCKER = 0
- ✅ 13个聚合根与13个Repository一致
- ✅ 13/13模块覆盖均有明确设计原因
- ✅ 16个Entity与PostgreSQL Schema一致
- ✅ Entity字段不正确问题已修复（ChallengeProjectEntity补充5字段）
- ✅ 无EnumType.ORDINAL、FetchType.EAGER、CascadeType.ALL
- ✅ 无跨模块JPA实体关联
- ✅ 新增持久化专项测试（4个新测试类，JpaMappingContextTest增强）
- ✅ 13个Repository全覆盖真实往返测试
- ✅ mvn verify BUILD SUCCESS
- ✅ 60 tests, 0 failures, 0 errors, 0 skipped
- ✅ V001-V015未修改
- ✅ 业务代码仍为0
