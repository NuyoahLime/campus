# ActivityApplication 规则包

> **聚合**: ActivityApplication | **模块**: activity | **状态**: 本轮实现

---

## 聚合标识

- 聚合根: ActivityApplication
- 聚合ID: ActivityApplicationId (UUID)
- 外部聚合ID: schoolId (→ School), applicantId (→ User), reviewedBy (→ User), createdActivityId (→ Activity)
- 无内部子实体（V1）
- ADR-001: ActivityApplication是独立聚合根，与Activity分离

---

## 创建入口

- 普通老师(Teacher)提交活动申请
- 初始状态: DRAFT
- 关联学校(school_id)和申请人(applicant_id)

---

## 状态机 (source: registry §activity_application)

```
DRAFT ──→ SUBMITTED ──→ APPROVED (terminal)
              │    └──→ REJECTED ──→ DRAFT (revise)
              │    └──→ WITHDRAWN (terminal)
```

| 状态 | 前驱 | 后继 | 终态 |
|------|------|------|:--:|
| DRAFT | (initial), REJECTED | SUBMITTED | — |
| SUBMITTED | DRAFT | APPROVED, REJECTED, WITHDRAWN | — |
| APPROVED | SUBMITTED | — | ✅ |
| REJECTED | SUBMITTED | DRAFT | — |
| WITHDRAWN | SUBMITTED | — | ✅ |

---

## 状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-ACT-APP-001 | DRAFT → SUBMITTED | 所有必填字段完整 | Teacher |
| CG-ACT-APP-002 | SUBMITTED → APPROVED | 校管审批通过，传入Activity ID | SchoolAdmin |
| CG-ACT-APP-003 | SUBMITTED → REJECTED | 校管驳回，驳回原因必填 | SchoolAdmin |
| CG-ACT-APP-004 | SUBMITTED → WITHDRAWN | 申请人主动撤回 | Teacher |
| CG-ACT-APP-005 | REJECTED → DRAFT | 申请人修改后重回草稿，版本号递增 | Teacher |

---

## 非法转换（必须拒绝）

| 规则ID | 禁止操作 | 原因 |
|------|------|------|
| CG-ACT-APP-006 | DRAFT → APPROVED (直接) | 必须先提交 |
| CG-ACT-APP-007 | DRAFT → REJECTED | 必须从已提交状态 |
| CG-ACT-APP-008 | APPROVED → 任何状态 | 终态不可逆 |
| CG-ACT-APP-009 | WITHDRAWN → 任何状态 | 终态不可逆 |
| CG-ACT-APP-010 | REJECTED → SUBMITTED (跳过DRAFT) | 必须先回到DRAFT |

---

## 业务不变量

| 规则ID | 不变量 | 来源 |
|------|------|------|
| CG-ACT-APP-011 | title ≤ 200字符，非空 | schema, spec-04 |
| CG-ACT-APP-012 | schoolId, applicantId非空 | schema |
| CG-ACT-APP-013 | rejectReason驳回时必填 | physical-schema-design, spec-11 |
| CG-ACT-APP-014 | applicationVersion从1开始，驳回重提时递增 | physical-schema-design |
| CG-ACT-APP-015 | createdActivityId批准时设置，每条申请最多一个Activity | ADR-001 |
| CG-ACT-APP-016 | SUBMITTED后不可修改基本信息（仅DRAFT可修改） | spec-04 |
| CG-ACT-APP-017 | DRAFT可物理删除；SUBMITTED后不可删除 | spec-11 |

---

## 领域事件

| 事件 | 触发时机 |
|------|------|
| ActivityApplicationSubmitted | DRAFT → SUBMITTED |
| ActivityApplicationApproved | SUBMITTED → APPROVED |
| ActivityApplicationRejected | SUBMITTED → REJECTED |
| ActivityApplicationWithdrawn | SUBMITTED → WITHDRAWN |
| ActivityApplicationReturnedToDraft | REJECTED → DRAFT |

---

## APPLICATION_LAYER_PENDING规则

| 规则ID | 描述 |
|------|------|
| CG-ACT-APP-APP-001 | 重复申请检查（同教师同活动概念） |
| CG-ACT-APP-APP-002 | 原子批准+活动创建（ADR-001 §9） |
| CG-ACT-APP-APP-003 | 学校状态检查（暂停学校不得处理申请） |
| CG-ACT-APP-APP-004 | 申请人角色检查（必须为Teacher） |
| CG-ACT-APP-APP-005 | 审批结果通知发送 |

---

## 不明确规则 (UNRESOLVED)

- CG-ACT-APP-UNRESOLVED-001: 驳回重提是否有次数限制？

---

## 来源

- `docs/validation/business-spec-registry.yaml` §activity_application
- `docs/business-spec/04-活动管理规格.md`
- `docs/business-spec/11-生命周期与数据保留规格.md`
- `docs/adr/ADR-001-activity-application-boundary.md`
- `docs/decision/业务决策记录-v1.0.md` DEC-ACTIVITY-001
- `src/main/resources/db/migration/V005__create_activity_domain.sql`
