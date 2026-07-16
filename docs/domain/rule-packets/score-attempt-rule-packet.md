# ScoreAttempt 规则包

> **聚合**: ScoreAttempt | **模块**: score | **状态**: 本轮实现

---

## 聚合标识

- 聚合根: ScoreAttempt
- 聚合ID: ScoreAttemptId (UUID)
- 外部聚合ID: schoolId (→ School), activityProjectId (→ ActivityProject), studentId (→ User), enteredBy (→ User), replacesId (→ ScoreAttempt自身)
- 无V1领域子实体
- ADR-002: ScoreAttempt是独立聚合根，每次尝试为一个聚合实例

---

## 创建入口

- 录入者(Teacher或SchoolAdmin)手动录入成绩
- 异常补录(AbnormalScoreEntry)审批通过后由应用层创建
- 初始状态: DRAFT, isCurrentEffective=false

---

## 状态机 (source: registry §score)

```
DRAFT → PENDING_REVIEW → APPROVED → INVALIDATED (terminal)
  ↑         ↓
  └─ REJECTED ←┘
```

| 状态 | 前驱 | 后继 | 终态 |
|------|------|------|:--:|
| DRAFT | (initial), REJECTED | PENDING_REVIEW | — |
| PENDING_REVIEW | DRAFT | APPROVED, REJECTED | — |
| APPROVED | PENDING_REVIEW | INVALIDATED | — |
| REJECTED | PENDING_REVIEW | DRAFT | — |
| INVALIDATED | APPROVED | — | ✅ |

注: registry中DRAFT.prev=[]与REJECTED.next=[DRAFT]存在符号矛盾，与ActivityApplication DRAFT.prev/REJECTED.next矛盾同源。REJECTED→DRAFT为有效转换(已由spec和spec表确认)。

---

## 状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-SCORE-001 | DRAFT → PENDING_REVIEW | 成绩已录入，录入者手动提交 | 录入者(Teacher/SchoolAdmin) |
| CG-SCORE-002 | PENDING_REVIEW → APPROVED | 审核者≠录入者，设置isCurrentEffective=true | SchoolAdmin |
| CG-SCORE-003 | PENDING_REVIEW → REJECTED | 驳回原因必填 | SchoolAdmin |
| CG-SCORE-004 | REJECTED → DRAFT | 录入者修改后重新编辑 | 录入者 |
| CG-SCORE-005 | APPROVED → INVALIDATED | 被更正后的新成绩替代，设置isCurrentEffective=false | 系统(应用层协调) |

---

## 非法转换（必须拒绝）

| 规则ID | 禁止操作 | 原因 |
|------|------|------|
| CG-SCORE-006 | DRAFT → APPROVED (跳过审核) | 必须先提交审核 |
| CG-SCORE-007 | DRAFT → REJECTED | 必须从待审核状态 |
| CG-SCORE-008 | INVALIDATED → 任何状态 | 终态不可逆 |
| CG-SCORE-009 | APPROVED → REJECTED | 审核通过不可驳回，只能通过更正 |

---

## 业务不变量

| 规则ID | 不变量 | 来源 |
|------|------|------|
| CG-SCORE-010 | schoolId, activityProjectId, studentId, enteredBy非空 | schema |
| CG-SCORE-011 | scoreStorageType与ScoreValue类型必须一致 | V006 CHECK约束 |
| CG-SCORE-012 | attemptNumber>0，同一(activityProjectId, studentId)内唯一 | V006 UNIQUE |
| CG-SCORE-013 | isCurrentEffective=true仅当status=APPROVED | spec-05 §3.2 |
| CG-SCORE-014 | DRAFT可修改分数值；PENDING_REVIEW后不可修改 | spec-05 §6 |
| CG-SCORE-015 | 审核通过后不得直接覆盖（只能通过更正流程） | spec-05 §6, DEC-SCORE-004 |

---

## 分数类型与值对象

| ScoreStorageType | ScoreValue实现 | 存储字段 | DB约束 |
|------|------|------|------|
| INTEGER | IntegerScore(long) | score_value | = floor(score_value) |
| DECIMAL | DecimalScore(BigDecimal) | score_value | IS NOT NULL |
| DURATION | DurationScore(long ms) | score_duration_ms | >= 0 |
| GRADE | GradeScore(String) | score_grade | IS NOT NULL |

ScoreValue使用sealed interface实现，构造时保证合法。ScoreStorageType定义在score模块域（镜像project模块定义，避免跨模块internal依赖）。

---

## 领域事件

| 事件 | 触发时机 |
|------|------|
| ScoreAttemptSubmitted | DRAFT → PENDING_REVIEW |
| ScoreAttemptApproved | PENDING_REVIEW → APPROVED |
| ScoreAttemptRejected | PENDING_REVIEW → REJECTED |
| ScoreAttemptReturnedToDraft | REJECTED → DRAFT |
| ScoreAttemptInvalidated | APPROVED → INVALIDATED |

---

## 延期内部表分类

| 表 | 分类 | 理由 |
|------|------|------|
| score_review_records | IMMUTABLE_HISTORY_RECORD | 仅追加审核记录，无version列，不属于聚合一致性边界 |
| score_correction_records | APPLICATION_LAYER_RECORD | 关联两个ScoreAttempt，由应用层在原子操作中创建，不在聚合内 |
| abnormal_score_entries | NOT_REQUIRED_IN_CURRENT_BATCH | 独立7状态机，可能为独立聚合或子聚合，V1领域模型不实现 |

---

## APPLICATION_LAYER_PENDING规则

| 规则ID | 描述 |
|------|------|
| CG-SCORE-APP-001 | 录入者≠审核者(自审限制) — 跨用户检查 |
| CG-SCORE-APP-002 | 有效成绩选择规则(BEST/LAST/ADMIN_DESIGNATED) — 需读取ScoreConfig |
| CG-SCORE-APP-003 | 更正操作原子协调(旧失效+新有效+CorrectionRecord) |
| CG-SCORE-APP-004 | 触发排行榜重算(领域事件→最终一致) |
| CG-SCORE-APP-005 | 学校状态检查(暂停学校不得录入新成绩) |
| CG-SCORE-APP-006 | 活动状态检查(已结束活动不得新增普通成绩) |
| CG-SCORE-APP-007 | attemptNumber自动递增分配 |

---

## 不明确规则 (UNRESOLVED)

- CG-SCORE-UNRESOLVED-001: V1是否需要使用score_business_time打破并列？
- CG-SCORE-UNRESOLVED-002: 无可靠业务发生时间的成绩，在并列时是否跳过排名？

---

## 来源

- `docs/validation/business-spec-registry.yaml` §score
- `docs/business-spec/05-成绩管理规格.md`
- `docs/adr/ADR-002-score-boundary.md`
- `docs/decision/业务决策记录-v1.0.md` DEC-SCORE-001/002/003/004
- `src/main/resources/db/migration/V006__create_score_domain.sql`
