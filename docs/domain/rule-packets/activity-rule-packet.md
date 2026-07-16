# Activity 规则包

> **聚合**: Activity | **模块**: activity | **状态**: 本轮实现

---

## 聚合标识

- 聚合根: Activity
- 聚合ID: ActivityId (UUID)
- 外部聚合ID: schoolId (→ School), createdBy (→ User)
- 内部子实体（V1延期）: ActivityProject, ResponsibleTeacher, Participant
- ADR-001: Activity是独立聚合根，与ActivityApplication分离

---

## 创建入口

- 两条路径: (a) ActivityApplication审批通过后由应用层创建; (b) SchoolAdmin直接创建
- 初始状态: execution_status=DRAFT, public_status=NOT_SUBMITTED
- 校管直接创建的活动没有关联ActivityApplication

---

## 双状态机

### execution_status (source: registry §activity_execution)

```
DRAFT → PUBLISHED → IN_PROGRESS → ENDED (terminal)
  |         |
  └──→ CANCELLED (terminal) ←────┘
```

| 状态 | 前驱 | 后继 | 终态 |
|------|------|------|:--:|
| DRAFT | (initial) | PUBLISHED, CANCELLED | — |
| PUBLISHED | DRAFT | IN_PROGRESS, CANCELLED | — |
| IN_PROGRESS | PUBLISHED | ENDED | — |
| ENDED | IN_PROGRESS | — | ✅ |
| CANCELLED | DRAFT, PUBLISHED | — | ✅ |

### public_status (source: registry §activity_public)

```
NOT_SUBMITTED → PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED → PUBLIC
                                        → PLATFORM_REJECTED → NOT_SUBMITTED
                                                      PUBLIC → SCHOOL_WITHDRAWN → NOT_SUBMITTED
                                                      PUBLIC → PLATFORM_TAKEDOWN → NOT_SUBMITTED
```

| 状态 | 前驱 | 后继 |
|------|------|------|
| NOT_SUBMITTED | (initial), PLATFORM_REJECTED, SCHOOL_WITHDRAWN, PLATFORM_TAKEDOWN | PENDING_PLATFORM_REVIEW |
| PENDING_PLATFORM_REVIEW | NOT_SUBMITTED | PLATFORM_APPROVED, PLATFORM_REJECTED |
| PLATFORM_APPROVED | PENDING_PLATFORM_REVIEW | PUBLIC |
| PLATFORM_REJECTED | PENDING_PLATFORM_REVIEW | NOT_SUBMITTED |
| PUBLIC | PLATFORM_APPROVED | SCHOOL_WITHDRAWN, PLATFORM_TAKEDOWN |
| SCHOOL_WITHDRAWN | PUBLIC | NOT_SUBMITTED |
| PLATFORM_TAKEDOWN | PUBLIC | NOT_SUBMITTED |

---

## 执行状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-ACT-002 | DRAFT → PUBLISHED | 活动配置完整 | SchoolAdmin |
| CG-ACT-003 | PUBLISHED → IN_PROGRESS | 开始时间到达或手动触发 | 系统/SchoolAdmin |
| CG-ACT-004 | IN_PROGRESS → ENDED | 手动确认结束 | SchoolAdmin |
| CG-ACT-005 | DRAFT → CANCELLED | — | SchoolAdmin |
| CG-ACT-006 | PUBLISHED → CANCELLED | 活动未开始 | SchoolAdmin |

## 公开状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-ACT-007 | NOT_SUBMITTED → PENDING_PLATFORM_REVIEW | execution_status ∈ {PUBLISHED, IN_PROGRESS, ENDED} | SchoolAdmin |
| CG-ACT-008 | PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED | — | SuperAdmin |
| CG-ACT-009 | PENDING_PLATFORM_REVIEW → PLATFORM_REJECTED | 驳回原因必填 | SuperAdmin |
| CG-ACT-010 | PLATFORM_APPROVED → PUBLIC | — | SchoolAdmin |
| CG-ACT-011 | PUBLIC → SCHOOL_WITHDRAWN | — | SchoolAdmin |
| CG-ACT-012 | PUBLIC → PLATFORM_TAKEDOWN | 下架原因必填 | SuperAdmin |
| CG-ACT-013 | REJECTED/WITHDRAWN/TAKEDOWN → NOT_SUBMITTED | — | — |

---

## 跨状态机规则

| 规则ID | 规则 | 来源 |
|------|------|------|
| CG-ACT-014 | DRAFT不可提交公开审核 | spec-04 §4 |
| CG-ACT-015 | CANCELLED不可提交公开审核 | spec-04 §4 |
| CG-ACT-016 | 取消时自动停止公开(public_status→NOT_SUBMITTED) | spec-04 §4 |

---

## 非法转换

| 规则ID | 禁止操作 |
|------|------|
| CG-ACT-019 | ENDED → 任何状态（终态不可逆） |
| CG-ACT-020 | CANCELLED → 任何状态（终态不可逆） |
| CG-ACT-021 | IN_PROGRESS → CANCELLED（进行中只能结束，不能取消） |
| CG-ACT-022 | DRAFT → IN_PROGRESS（必须先发布） |

---

## 业务不变量

| 规则ID | 不变量 | 来源 |
|------|------|------|
| CG-ACT-017 | title ≤ 200字符，非空 | schema, spec-04 |
| CG-ACT-018 | schoolId, createdBy非空 | schema |
| CG-ACT-023 | DRAFT可完整编辑；PUBLISHED后不可修改核心字段 | DEC-ACTIVITY-002 |
| CG-ACT-024 | 已取消/已结束的活动不得新增普通成绩 | spec-04 §3 |
| CG-ACT-025 | 取消不删除既有成绩、素材、成果和审核记录 | spec-04 §3 |
| CG-ACT-026 | V1不支持将已结束活动恢复为进行中 | spec-04 §3 |

---

## 领域事件

| 事件 | 触发时机 |
|------|------|
| ActivityPublished | DRAFT → PUBLISHED |
| ActivityExecutionStarted | PUBLISHED → IN_PROGRESS |
| ActivityEnded | IN_PROGRESS → ENDED |
| ActivityCancelled | DRAFT/PUBLISHED → CANCELLED |
| ActivitySubmittedForReview | NOT_SUBMITTED → PENDING_PLATFORM_REVIEW |
| ActivityPlatformApproved | PENDING_PLATFORM_REVIEW → PLATFORM_APPROVED |
| ActivityPlatformRejected | PENDING_PLATFORM_REVIEW → PLATFORM_REJECTED |
| ActivityMadePublic | PLATFORM_APPROVED → PUBLIC |
| ActivityWithdrawnBySchool | PUBLIC → SCHOOL_WITHDRAWN |
| ActivityTakenDownByPlatform | PUBLIC → PLATFORM_TAKEDOWN |
| ActivityPublicReviewReset | REJECTED/WITHDRAWN/TAKEDOWN → NOT_SUBMITTED |

---

## ActivityProject分类结论

- ActivityProject是**Activity聚合的内部子实体**（非独立聚合）
- 无独立状态机，无独立生命周期
- V1阶段延期实现（`docs/domain/aggregate-model-matrix.md` 分类为AGGREGATE_CHILD_ENTITY，本阶段=否）
- 外部聚合（如ScoreAttempt）通过ActivityProject.id引用
- 当前V1领域模型仅用ID集合表示：`Set<ChallengeProjectId>`

---

## APPLICATION_LAYER_PENDING规则

| 规则ID | 描述 |
|------|------|
| CG-ACT-APP-001 | 原子批准+活动创建（ADR-001 §9） |
| CG-ACT-APP-002 | 学校状态检查（暂停学校不得创建新活动） |
| CG-ACT-APP-003 | 取消时自动停止公开的具体执行 |
| CG-ACT-APP-004 | 已公开活动修改核心字段后重新审核 |
| CG-ACT-APP-005 | 仅格式修改不触发重新审核的变更日志 |

---

## 不明确规则 (UNRESOLVED)

- CG-ACT-UNRESOLVED-001: PUBLISHED已过start_time但未手动切换到IN_PROGRESS时可否取消？

---

## 来源

- `docs/validation/business-spec-registry.yaml` §activity_execution, §activity_public
- `docs/business-spec/04-活动管理规格.md`
- `docs/business-spec/11-生命周期与数据保留规格.md`
- `docs/adr/ADR-001-activity-application-boundary.md`
- `docs/decision/业务决策记录-v1.0.md` DEC-ACTIVITY-001/002/003
- `docs/decision/业务决策记录-v1.1.md` DEC-ACTIVITY-STATE-003
- `src/main/resources/db/migration/V005__create_activity_domain.sql`
