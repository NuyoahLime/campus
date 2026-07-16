# 物理数据库设计 v1.2

> **任务**: TASK-DATABASE-001-C
> **状态**: PROPOSED PHYSICAL SCHEMA DESIGN
> **日期**: 2026-07-14
> **输入**: `01-logical-data-model.md`, ADR-008, ADR-010, ADR-013
> **数据库**: PostgreSQL 18 | **迁移**: Flyway | **写模型**: JPA | **查询**: JdbcClient
> **注意**: 不包含SQL/DDL文件、Flyway migration文件、JPA Entity代码。

---

## 一、Schema策略

**决策: 单Schema (`public`)，不采用PostgreSQL schema隔离。**

理由: 模块化单体共享同一数据库实例；13模块之间schema隔离增加跨模块查询复杂度(JdbcClient跨schema查询)而V1无多租户需求。模块边界由Flyway迁移文件命名前缀和JPA包结构表达。

---

## 二、主键策略

**决策: UUID v7（时间有序UUID）。**

| 维度 | UUID v7 | Long自增 | 混合 |
|------|:--:|:--:|:--:|
| JPA兼容 | ✅ | ✅ | ⚠️ |
| 分布式未来 | ✅ 无冲突 | ❌ 需序列服务 | ⚠️ |
| 索引性能 | ✅ 时间有序B-tree友好 | ✅ | ⚠️ |
| ID暴露安全 | ✅ 不可枚举 | ❌ 可猜测 | ⚠️ |
| 大小 | 16 bytes | 8 bytes | — |

UUID v7保持时间有序性（B-tree插入友好），同时避免Long自增的ID可枚举问题。`UUID`类型PostgreSQL原生支持。

---

## 三、表清单（32张表）

| 表名 | 模块 | 类型 | 对应实体 |
|------|------|------|------|
| users | identity | 聚合根 | User |
| student_profiles | identity | DERIVED | StudentProfile |
| teacher_profiles | identity | DERIVED | TeacherProfile |
| school_memberships | identity | DERIVED | SchoolMembership |
| schools | school | 聚合根 | School |
| school_registrations | school | 聚合根 | SchoolRegistration |
| challenge_projects | project | 聚合根 | ChallengeProject |
| project_rule_versions | project | 内部实体 | ProjectRuleVersion |
| project_rule_compatibilities | project | 关联记录 | ProjectRuleCompatibility |
| activity_applications | activity | 聚合根 | ActivityApplication |
| activities | activity | 聚合根 | Activity |
| activity_projects | activity | 内部实体 | ActivityProject |
| responsible_teachers | activity | 关联记录 | ResponsibleTeacher |
| activity_participants | activity | 关联记录 | ActivityParticipant |
| score_attempts | score | 聚合根 | ScoreAttempt |
| score_review_records | score | 审核记录 | ScoreReviewRecord |
| score_correction_records | score | 关联记录 | ScoreCorrectionRecord |
| abnormal_score_entries | score | 聚合根 | AbnormalScoreEntry |
| ranking_definitions | ranking | 聚合根 | RankingDefinition |
| ranking_versions | ranking | 内部实体 | RankingVersion |
| ranking_entries | ranking | 快照实体 | RankingEntry |
| l3_authorizations | ranking | 聚合根 | L3Authorization |
| score_appeals | appeal | 聚合根 | ScoreAppeal |
| appeal_records | appeal | 审核记录 | AppealRecord |
| media | media | 聚合根 | Media |
| media_review_records | media | 审核记录 | MediaReviewRecord |
| activity_results | result | 聚合根 | ActivityResult |
| result_versions | result | 内部实体 | ResultVersion |
| feedbacks | feedback | 聚合根 | Feedback |
| notifications | notification | 聚合根 | Notification |
| audit_records | audit | 基础设施 | AuditRecord |
| task_records | (infra) | 基础设施 | TaskRecord |

> SessionRecord由Spring Session JDBC自动管理，不纳入本设计。

---

## 四、核心表字段设计

### 4.1 identity模块

**users**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | UUID v7 |
| username | VARCHAR(100) | UNIQUE NOT NULL | 登录用户名 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt/Argon2哈希 |
| account_status | VARCHAR(32) | NOT NULL DEFAULT 'PENDING_ACTIVATION' | 待激活/正常/锁定/停用 |
| platform_role | VARCHAR(32) | NULL | 超管标识(NULL=普通用户) |
| locked_until | TIMESTAMPTZ | NULL | 锁定到期时间 |
| login_failures | INT | NOT NULL DEFAULT 0 | 连续登录失败次数 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | 乐观锁 |

**school_memberships**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| user_id | UUID | FK→users NOT NULL | — |
| school_id | UUID | FK→schools NOT NULL | — |
| role_in_school | VARCHAR(32) | NOT NULL | STUDENT/TEACHER/SCHOOL_ADMIN |
| status | VARCHAR(32) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE/ENDED |
| started_at | TIMESTAMPTZ | NOT NULL | — |
| ended_at | TIMESTAMPTZ | NULL | 离职/毕业时设置 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | 乐观锁 |

UNIQUE: (user_id, school_id) WHERE status='ACTIVE' — 部分唯一索引保证每用户最多一个有效Membership。

**student_profiles**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| membership_id | UUID | FK→school_memberships UNIQUE NOT NULL | 1:1关联 |
| grade | VARCHAR(32) | NULL | 年级 |
| class_name | VARCHAR(64) | NULL | 班级 |
| student_number | VARCHAR(64) | NULL | 学号 |

**teacher_profiles**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| membership_id | UUID | FK→school_memberships UNIQUE NOT NULL | 1:1关联 |
| subject | VARCHAR(64) | NULL | 科目 |
| title | VARCHAR(64) | NULL | 职称 |

### 4.2 school模块

**schools**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| name | VARCHAR(200) | NOT NULL | 学校名称 |
| unified_code_type | VARCHAR(32) | NOT NULL | 统一社会信用代码/学校标识码/其他 |
| unified_code | VARCHAR(64) | NOT NULL | 编码值 |
| internal_code | VARCHAR(32) | UNIQUE NOT NULL | 平台内部唯一编码(生成后不复用) |
| school_type | VARCHAR(32) | NOT NULL | 学校类型 |
| region | VARCHAR(128) | NOT NULL | 所在地区 |
| address | TEXT | NOT NULL | 学校地址 |
| contact_name | VARCHAR(100) | NOT NULL | 联系人 |
| contact_phone | VARCHAR(32) | NOT NULL | 联系电话 |
| contact_email | VARCHAR(200) | NOT NULL | 联系邮箱 |
| school_status | VARCHAR(32) | NOT NULL DEFAULT 'PENDING_ENABLE' | 待启用/正常/暂停/停用 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | 乐观锁 |

UNIQUE: (unified_code_type, unified_code) — 全局唯一。UNIQUE: internal_code。

**school_registrations**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_name | VARCHAR(200) | NOT NULL | — |
| unified_code_type | VARCHAR(32) | NOT NULL | — |
| unified_code | VARCHAR(64) | NULL | 条件必填 |
| school_type | VARCHAR(32) | NOT NULL | — |
| region | VARCHAR(128) | NOT NULL | — |
| address | TEXT | NOT NULL | — |
| contact_name | VARCHAR(100) | NOT NULL | — |
| contact_phone | VARCHAR(32) | NOT NULL | — |
| contact_email | VARCHAR(200) | NOT NULL | — |
| description | TEXT | NULL | 申请说明 |
| evidence_file_key | VARCHAR(500) | NULL | 证明材料S3 key |
| registration_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/已提交/需补充/已通过/已驳回/已撤回 |
| created_school_id | UUID | FK→schools NULL | 审批通过后关联 |
| reviewed_by | UUID | FK→users NULL | 审核人 |
| reviewed_at | TIMESTAMPTZ | NULL | 审核时间 |
| review_comment | TEXT | NULL | 审核意见 |
| reject_reason | TEXT | NULL | 驳回原因 |
| withdrawn_by | VARCHAR(100) | NULL | 撤回人 |
| withdrawn_at | TIMESTAMPTZ | NULL | 撤回时间 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | 乐观锁 |

### 4.3 project模块

**challenge_projects**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| name | VARCHAR(200) | NOT NULL | 项目名称 |
| category | VARCHAR(64) | NOT NULL | 分类 |
| description | TEXT | NULL | 项目说明 |
| venue_requirements | TEXT | NULL | 场地要求 |
| equipment_requirements | TEXT | NULL | 器材要求 |
| rules_text | TEXT | NULL | 比赛规则 |
| score_storage_type | VARCHAR(32) | NOT NULL | INTEGER/DECIMAL/DURATION/GRADE |
| score_indicator_type | VARCHAR(32) | NOT NULL | 次数/距离/时长/分数/等级 |
| comparison_direction | VARCHAR(32) | NOT NULL | HIGHER_BETTER/LOWER_BETTER/GRADE_ORDER/NO_RANKING |
| score_unit | VARCHAR(32) | NULL | 单位 |
| decimal_places | INT | NULL | 小数位数(仅DECIMAL型时必填) |
| grade_order | TEXT | NULL | 等级顺序JSON(仅GRADE型) |
| allow_tie | BOOLEAN | NOT NULL DEFAULT TRUE | — |
| effective_score_rule | VARCHAR(32) | NOT NULL DEFAULT 'BEST' | BEST/LAST/ADMIN_DESIGNATED |
| project_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/已上架/已下架 |
| current_rule_version_id | UUID | FK→project_rule_versions NULL | 当前规则版本 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**project_rule_versions**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| project_id | UUID | FK→challenge_projects NOT NULL | — |
| version_number | INT | NOT NULL | 项目内递增 |
| score_storage_type | VARCHAR(32) | NOT NULL | (快照) |
| score_indicator_type | VARCHAR(32) | NOT NULL | (快照) |
| comparison_direction | VARCHAR(32) | NOT NULL | (快照) |
| score_unit | VARCHAR(32) | NULL | (快照) |
| decimal_places | INT | NULL | (快照) |
| grade_order | TEXT | NULL | (快照) |
| rules_text | TEXT | NULL | (快照) |
| venue_requirements | TEXT | NULL | (快照) |
| equipment_requirements | TEXT | NULL | (快照) |
| effective_score_rule | VARCHAR(32) | NOT NULL | (快照) |
| change_reason | TEXT | NULL | — |
| created_by | UUID | FK→users NOT NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

UNIQUE: (project_id, version_number)。

### 4.4 activity模块

**activity_applications**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| applicant_id | UUID | FK→users NOT NULL | 申请人(老师) |
| title | VARCHAR(200) | NOT NULL | 活动名称 |
| description | TEXT | NULL | — |
| application_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/已提交/已通过/已驳回/已撤回 |
| created_activity_id | UUID | FK→activities NULL UNIQUE | 审批通过后关联 |
| reviewed_by | UUID | FK→users NULL | — |
| reviewed_at | TIMESTAMPTZ | NULL | — |
| review_comment | TEXT | NULL | — |
| reject_reason | TEXT | NULL | — |
| application_version | INT | NOT NULL DEFAULT 1 | 驳回重提时递增 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

UNIQUE: created_activity_id (幂等: 一个申请最多一个Activity)。

**activities**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| title | VARCHAR(200) | NOT NULL | — |
| description | TEXT | NULL | — |
| start_time | TIMESTAMPTZ | NULL | — |
| end_time | TIMESTAMPTZ | NULL | — |
| location | VARCHAR(300) | NULL | — |
| execution_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/已发布/进行中/已结束/已取消 |
| public_status | VARCHAR(32) | NOT NULL DEFAULT 'NOT_SUBMITTED' | 未提交/待平台审核/平台审核通过/平台审核驳回/已公开/学校已撤回/平台已下架 |
| created_by | UUID | FK→users NOT NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**activity_projects**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| activity_id | UUID | FK→activities NOT NULL | — |
| project_id | UUID | FK→challenge_projects NOT NULL | — |
| rule_version_id | UUID | FK→project_rule_versions NOT NULL | 规则快照 |
| config | JSONB | NULL | 项目特定配置 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

UNIQUE: (activity_id, project_id)。

### 4.5 score模块

**score_attempts**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | 冗余(隔离+历史固定) |
| activity_project_id | UUID | FK→activity_projects NOT NULL | — |
| student_id | UUID | FK→users NOT NULL | — |
| attempt_number | INT | NOT NULL | (student, activity_project)内递增 |
| score_value | DECIMAL(18,4) | NULL | 数值型/小数型 |
| score_duration_ms | BIGINT | NULL | 时长型(64位整数毫秒) |
| score_grade | VARCHAR(32) | NULL | 等级型 |
| score_business_time | TIMESTAMPTZ | NULL | 成绩业务发生时间 |
| time_source | VARCHAR(32) | NULL | 时间来源 |
| is_current_effective | BOOLEAN | NOT NULL DEFAULT FALSE | 当前有效成绩标记 |
| replaces_id | UUID | FK→score_attempts NULL | 更正: 替代的旧成绩 |
| score_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/待审核/审核通过/审核驳回/已失效 |
| entered_by | UUID | FK→users NOT NULL | 录入人 |
| submitted_at | TIMESTAMPTZ | NULL | 提交审核时间 |
| is_manual_makeup | BOOLEAN | NOT NULL DEFAULT FALSE | 是否异常补录 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | 乐观锁 |

**EffectiveScore物理实现**: 方案A — `is_current_effective`布尔标记 + 部分唯一索引:
```sql
CREATE UNIQUE INDEX uq_effective_score 
  ON score_attempts (student_id, activity_project_id) 
  WHERE is_current_effective = true;
```
并发安全: 更新旧成绩`is_current_effective=false`+新成绩`is_current_effective=true`在同一事务内。唯一索引保证同(student,project)最多一行true。

**score_review_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| score_attempt_id | UUID | FK→score_attempts NOT NULL | — |
| reviewer_id | UUID | FK→users NOT NULL | 审核人 |
| review_result | VARCHAR(32) | NOT NULL | APPROVED/REJECTED |
| review_comment | TEXT | NULL | 审核意见 |
| reject_reason | TEXT | NULL | 驳回原因(驳回时必填) |
| reviewed_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

**score_correction_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| original_score_id | UUID | FK→score_attempts NOT NULL | 原成绩 |
| new_score_id | UUID | FK→score_attempts NOT NULL UNIQUE | 新成绩 |
| correction_reason | TEXT | NOT NULL | 更正原因 |
| corrected_by | UUID | FK→users NOT NULL | — |
| corrected_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.6 ranking模块

**ranking_definitions**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| layer | VARCHAR(8) | NOT NULL | L1/L2/L3 |
| name | VARCHAR(200) | NOT NULL | — |
| school_id | UUID | FK→schools NULL | L1/L2必填, L3为NULL |
| project_id | UUID | FK→challenge_projects NOT NULL | — |
| dimension_filters | JSONB | NULL | 筛选维度(年级/班级/性别等) |
| tie_break_rule | VARCHAR(32) | NULL | 并列打破规则 |
| is_enabled | BOOLEAN | NOT NULL DEFAULT TRUE | — |
| current_version_id | UUID | FK→ranking_versions NULL | 当前发布版本 |
| created_by | UUID | FK→users NOT NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**ranking_versions**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| definition_id | UUID | FK→ranking_definitions NOT NULL | — |
| version_number | INT | NOT NULL | 定义内递增 |
| previous_version_id | UUID | FK→ranking_versions NULL | 上一版本 |
| version_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT_CALC' | 草稿计算/已生成/已发布/已撤回/已过期/已被替换/已作废 |
| calculation_params | JSONB | NULL | 计算参数快照 |
| data_scope_snapshot | JSONB | NULL | 数据范围快照 |
| authorization_ids_snapshot | JSONB | NULL | L3:使用的授权ID列表 |
| generated_at | TIMESTAMPTZ | NULL | 生成时间 |
| published_at | TIMESTAMPTZ | NULL | 发布时间 |
| withdrawn_at | TIMESTAMPTZ | NULL | 撤回时间 |
| created_reason | VARCHAR(64) | NULL | 创建原因(MANUAL/SCORE_CORRECTED/SCHEDULED) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

**ranking_entries**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| version_id | UUID | FK→ranking_versions NOT NULL | — |
| rank_position | INT | NOT NULL | 排名位置 |
| score_attempt_id | UUID | FK→score_attempts NOT NULL | 来源成绩引用 |
| rule_version_id | UUID | FK→project_rule_versions NOT NULL | 规则版本引用 |
| student_display_name | VARCHAR(200) | NOT NULL | 脱敏展示名 |
| school_name | VARCHAR(200) | NULL | 学校名(L3时必填) |
| score_display_value | VARCHAR(100) | NOT NULL | 展示用成绩值 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

**l3_authorizations**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| project_id | UUID | FK→challenge_projects NOT NULL | — |
| rule_version_id | UUID | FK→project_rule_versions NOT NULL | — |
| data_scope | JSONB | NULL | 活动范围/时间范围/年级范围 |
| allow_school_name | BOOLEAN | NOT NULL DEFAULT TRUE | — |
| allow_student_name | BOOLEAN | NOT NULL DEFAULT FALSE | — |
| authorization_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/待平台审核/审核通过/审核驳回/已暂停/已撤回 |
| submitted_at | TIMESTAMPTZ | NULL | — |
| reviewed_by | UUID | FK→users NULL | — |
| reviewed_at | TIMESTAMPTZ | NULL | — |
| review_comment | TEXT | NULL | — |
| reject_reason | TEXT | NULL | — |
| paused_at | TIMESTAMPTZ | NULL | — |
| withdrawn_at | TIMESTAMPTZ | NULL | — |
| withdraw_reason | TEXT | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

### 4.7 appeal模块

**score_appeals**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| score_attempt_id | UUID | FK→score_attempts NOT NULL | 引用明确成绩事实 |
| student_id | UUID | FK→users NOT NULL | — |
| appeal_type | VARCHAR(32) | NOT NULL | SCORE/RANKING |
| appeal_reason | TEXT | NOT NULL | 申诉理由 |
| evidence_file_keys | JSONB | NULL | 证据文件S3 key数组 |
| appeal_status | VARCHAR(32) | NOT NULL DEFAULT 'SUBMITTED' | 13状态完整 |
| handler_id | UUID | FK→users NULL | 当前处理人 |
| escalated_to | UUID | FK→users NULL | 升级目标(超管) |
| resolution | TEXT | NULL | 处理结果说明 |
| resolved_at | TIMESTAMPTZ | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**appeal_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| appeal_id | UUID | FK→score_appeals NOT NULL | — |
| from_status | VARCHAR(32) | NULL | — |
| to_status | VARCHAR(32) | NOT NULL | — |
| operator_id | UUID | FK→users NOT NULL | — |
| comment | TEXT | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.8 media模块

**media**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| activity_id | UUID | FK→activities NOT NULL | V1必填 |
| uploader_id | UUID | FK→users NOT NULL | — |
| file_key | VARCHAR(500) | NOT NULL | S3对象键 |
| file_name | VARCHAR(300) | NOT NULL | 原始文件名 |
| file_type | VARCHAR(16) | NOT NULL | IMAGE/VIDEO |
| file_format | VARCHAR(16) | NOT NULL | JPG/JPEG/PNG/MP4 |
| file_size_bytes | BIGINT | NOT NULL | — |
| checksum | VARCHAR(128) | NULL | 文件校验值 |
| internal_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/待校内审核/校内审核通过/校内审核驳回/校内已停用 |
| public_status | VARCHAR(32) | NOT NULL DEFAULT 'NOT_SUBMITTED' | 未提交/待平台公开审核/平台公开审核通过/平台公开审核驳回/已公开/平台已下架 |
| description | TEXT | NULL | 可选描述 |
| uploaded_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**media_review_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| media_id | UUID | FK→media NOT NULL | — |
| review_level | VARCHAR(16) | NOT NULL | INTERNAL/PUBLIC |
| reviewer_id | UUID | FK→users NOT NULL | — |
| review_result | VARCHAR(32) | NOT NULL | APPROVED/REJECTED |
| review_comment | TEXT | NULL | — |
| reject_reason | TEXT | NULL | — |
| reviewed_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.9 result模块

**activity_results**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NOT NULL | — |
| activity_id | UUID | FK→activities NOT NULL UNIQUE | 1:0..1 |
| result_internal_status | VARCHAR(32) | NOT NULL DEFAULT 'DRAFT' | 草稿/校内已发布/校内已撤回 |
| result_public_status | VARCHAR(32) | NOT NULL DEFAULT 'NOT_SUBMITTED' | 未提交/待平台公开审核/平台公开审核通过/平台公开审核驳回/已公开/公开异常待处理/平台已下架 |
| current_internal_version_id | UUID | FK→result_versions NULL | 当前校内版本 |
| current_public_version_id | UUID | FK→result_versions NULL | 当前公开版本 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

**result_versions**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| result_id | UUID | FK→activity_results NOT NULL | — |
| version_number | INT | NOT NULL | result内递增 |
| title | VARCHAR(200) | NOT NULL | — |
| summary_text | TEXT | NOT NULL | 文字总结 |
| score_highlights | JSONB | NULL | 成绩亮点 |
| media_refs | JSONB | NULL | 引用Media ID数组+展示顺序 |
| is_core_content_modified | BOOLEAN | NOT NULL DEFAULT TRUE | 是否核心修改(决定是否需要重新审核) |
| format_change_log | TEXT | NULL | 格式修改日志(仅非核心修改时) |
| published_internally_at | TIMESTAMPTZ | NULL | — |
| published_publicly_at | TIMESTAMPTZ | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.10 feedback模块

**feedbacks**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NULL | 校内反馈必填 |
| submitter_id | UUID | FK→users NULL | 匿名访客为NULL |
| feedback_type | VARCHAR(32) | NOT NULL | GENERAL/SCORE_PROBLEM/RANKING_PROBLEM |
| content | TEXT | NOT NULL | — |
| feedback_status | VARCHAR(32) | NOT NULL DEFAULT 'SUBMITTED' | 已提交/处理中/已处理/已升级/已关闭 |
| handler_id | UUID | FK→users NULL | — |
| handler_level | VARCHAR(32) | NULL | SCHOOL/PLATFORM |
| reply | TEXT | NULL | — |
| close_reason | TEXT | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

### 4.11 notification模块

**notifications**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| recipient_id | UUID | FK→users NOT NULL | — |
| event_type | VARCHAR(64) | NOT NULL | 触发事件类型 |
| title | VARCHAR(300) | NOT NULL | — |
| content | TEXT | NULL | — |
| reference_type | VARCHAR(32) | NULL | 关联业务对象类型 |
| reference_id | UUID | NULL | 关联业务对象ID |
| is_read | BOOLEAN | NOT NULL DEFAULT FALSE | — |
| read_at | TIMESTAMPTZ | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.12 audit模块

**audit_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| school_id | UUID | FK→schools NULL | 目标学校(可空) |
| actor_id | UUID | FK→users NOT NULL | 操作者 |
| action | VARCHAR(64) | NOT NULL | 操作类型 |
| target_type | VARCHAR(32) | NOT NULL | 目标对象类型 |
| target_id | UUID | NOT NULL | 目标对象ID |
| detail | JSONB | NULL | 操作详情上下文 |
| ip_address | VARCHAR(64) | NULL | — |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |

### 4.13 基础设施

**task_records**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | UUID | PK | — |
| task_type | VARCHAR(64) | NOT NULL | RANKING_RECALC/NOTIFY/SCHOOL_STATE_SYNC/MEDIA_CLEANUP |
| reference_type | VARCHAR(32) | NULL | — |
| reference_id | UUID | NULL | — |
| task_status | VARCHAR(32) | NOT NULL DEFAULT 'PENDING' | PENDING/PROCESSING/COMPLETED/FAILED/DEAD |
| payload | JSONB | NULL | 任务参数 |
| retry_count | INT | NOT NULL DEFAULT 0 | — |
| max_retries | INT | NOT NULL DEFAULT 3 | — |
| next_retry_at | TIMESTAMPTZ | NULL | — |
| last_error | TEXT | NULL | — |
| idempotency_key | VARCHAR(128) | NOT NULL | 幂等键 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | — |
| version | INT | NOT NULL DEFAULT 1 | — |

UNIQUE: idempotency_key。

---

## 五、索引策略

| 表 | 索引 | 类型 | 解决的问题 |
|------|------|:--:|------|
| users | username | UNIQUE | 登录唯一 |
| school_memberships | (user_id, school_id) WHERE status='ACTIVE' | PARTIAL UNIQUE | 每用户最多一个有效Membership |
| schools | (unified_code_type, unified_code) | UNIQUE | 学校去重 |
| schools | internal_code | UNIQUE | 平台内部编码 |
| activity_applications | created_activity_id | UNIQUE | 幂等(一个申请一个Activity) |
| activity_projects | (activity_id, project_id) | UNIQUE | 活动内项目不重复 |
| **score_attempts** | **(student_id, activity_project_id) WHERE is_current_effective=true** | **PARTIAL UNIQUE** | **EffectiveScore唯一** |
| score_attempts | (activity_project_id, student_id, attempt_number) | UNIQUE | 尝试号递增不重复 |
| score_attempts | replaces_id | INDEX | 更正链查询 |
| score_correction_records | new_score_id | UNIQUE | 一个更正对应一个新成绩 |
| ranking_definitions | (school_id, project_id) WHERE layer='L2' | PARTIAL UNIQUE | 同校同项目一个L2定义 |
| ranking_versions | (definition_id, version_number) | UNIQUE | 版本号不重复 |
| l3_authorizations | (school_id, project_id) | INDEX | 按学校+项目查询授权 |
| media | activity_id | INDEX | 按活动查询素材 |
| media | uploader_id | INDEX | 按上传者查询 |
| activity_results | activity_id | UNIQUE | 1:0..1 |
| notifications | (recipient_id, is_read, created_at) | INDEX | 未读通知列表 |
| task_records | idempotency_key | UNIQUE | 幂等 |
| task_records | (task_status, next_retry_at) | INDEX | Worker轮询待处理任务 |

---

## 六、并发控制

| 表 | 控制方式 | 说明 |
|------|------|------|
| score_attempts | version列乐观锁 + 部分唯一索引 | 成绩更正: 检查旧成绩version; 唯一索引防止两个有效成绩 |
| activity_applications | version列 + created_activity_id UNIQUE | 审批幂等 |
| ranking_definitions | version列 | 发布版本时乐观锁 |
| activity_results | version列 | 发布结果时乐观锁 |
| task_records | version列 + idempotency_key UNIQUE | 任务幂等 |
| schools | version列 | 状态变更 |
| 其余 | version列 | 通用乐观锁 |

事务隔离: READ COMMITTED (PostgreSQL默认)。部分唯一索引在READ COMMITTED下正确工作。

---

## 七、Flyway迁移顺序

```
V001__identity.sql          — users
V002__school.sql            — schools, school_registrations
V003__identity_membership.sql — school_memberships, student_profiles, teacher_profiles
V004__project.sql           — challenge_projects, project_rule_versions, project_rule_compatibilities
V005__activity.sql          — activity_applications, activities, activity_projects, responsible_teachers, activity_participants
V006__score.sql             — score_attempts, score_review_records, score_correction_records, abnormal_score_entries
V007__ranking.sql           — ranking_definitions, ranking_versions, ranking_entries, l3_authorizations
V008__appeal.sql            — score_appeals, appeal_records
V009__media.sql             — media, media_review_records
V010__result.sql            — activity_results, result_versions
V011__feedback.sql          — feedbacks
V012__notification.sql      — notifications
V013__audit.sql             — audit_records
V014__task.sql              — task_records
```

依赖顺序: identity → school → project → activity → score → ranking/appeal/media/result → feedback/notification → audit/task。

---

## 八、数据保留策略（物理）

| 实体 | 物理删除 | 机制 |
|------|:--:|------|
| users | ❌ | account_status=DISABLED |
| score_attempts | ❌ | score_status=INVALIDATED |
| ranking_versions(已发布) | ❌ | 终态保留 |
| audit_records | ❌ | 追加不可变 |
| notifications | 保留期后清理 | 按created_at+保留策略 |
| task_records | COMPLETED后保留30天 | 按updated_at清理 |
| session(Spring管理) | 过期自动清理 | Spring Session JDBC |

---

## 九、DATABASE_BLOCKER: 0
## 十、NON_BLOCKING_CHOICE

UUID v7 vs Long | 部分索引 vs 独立关联表 | JSONB vs 关系表(RankingEntry明细) | 审核历史分表策略 | 物化视图(排行榜缓存) | jOOQ引入

---

> **状态**: PROPOSED PHYSICAL SCHEMA DESIGN
> **下一阶段**: TASK-DATABASE-001-D (Flyway migration文件编写) — 需本设计验收批准后进入
