# SchoolRegistration 规则包

> **聚合**: SchoolRegistration | **模块**: school | **状态**: 本轮实现

---

## 聚合标识

- 聚合根: SchoolRegistration
- 聚合ID: SchoolRegistrationId (UUID)
- 外部聚合ID: createdSchoolId (→ School), reviewedBy (→ User)
- 无内部子实体（V1）

---

## 创建入口

- 访问者(Visitor)提交入驻申请
- 无需平台账号

---

## 状态机 (source: registry §school_registration)

```
DRAFT ──→ SUBMITTED ──→ APPROVED (terminal)
              │    └──→ REJECTED (terminal)
              │    └──→ WITHDRAWN (terminal)
              └──→ NEED_SUPPLEMENT ──→ SUBMITTED
                                  └──→ WITHDRAWN (terminal)
```

| 状态 | 前驱 | 后继 | 终态 |
|------|------|------|:--:|
| DRAFT | (initial) | SUBMITTED | — |
| SUBMITTED | DRAFT, NEED_SUPPLEMENT | NEED_SUPPLEMENT, APPROVED, REJECTED, WITHDRAWN | — |
| NEED_SUPPLEMENT | SUBMITTED | SUBMITTED, WITHDRAWN | — |
| APPROVED | SUBMITTED | — | ✅ |
| REJECTED | SUBMITTED | — | ✅ |
| WITHDRAWN | SUBMITTED, NEED_SUPPLEMENT | — | ✅ |

---

## 状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-SCHOOL-REG-001 | DRAFT → SUBMITTED | 所有必填字段完整 | Visitor |
| CG-SCHOOL-REG-002 | SUBMITTED → NEED_SUPPLEMENT | 超管审核要求补充材料 | SuperAdmin |
| CG-SCHOOL-REG-003 | SUBMITTED → APPROVED | 超管审核通过 | SuperAdmin |
| CG-SCHOOL-REG-004 | SUBMITTED → REJECTED | 超管审核驳回 | SuperAdmin |
| CG-SCHOOL-REG-005 | SUBMITTED → WITHDRAWN | 申请人主动撤回 | Visitor |
| CG-SCHOOL-REG-006 | NEED_SUPPLEMENT → SUBMITTED | 申请人补充后重新提交 | Visitor |
| CG-SCHOOL-REG-007 | NEED_SUPPLEMENT → WITHDRAWN | 申请人放弃 | Visitor |

---

## 非法转换（必须拒绝）

| 规则ID | 禁止操作 | 原因 |
|------|------|------|
| CG-SCHOOL-REG-008 | DRAFT → APPROVED (直接) | 必须先提交 |
| CG-SCHOOL-REG-009 | DRAFT → NEED_SUPPLEMENT | 必须从已提交状态 |
| CG-SCHOOL-REG-010 | APPROVED → 任何状态 | 终态不可逆 |
| CG-SCHOOL-REG-011 | REJECTED → 修改重提 | 驳回后可新建申请，但当前记录不可修改 |

---

## 业务不变量

| 规则ID | 不变量 | 来源 |
|------|------|------|
| CG-SCHOOL-REG-012 | 学校名称≤200字符，非空 | schema, spec-02 |
| CG-SCHOOL-REG-013 | unified_code_type非空 | schema |
| CG-SCHOOL-REG-014 | 学校类型、地区、地址、联系人姓名/电话/邮箱非空 | spec-02 |
| CG-SCHOOL-REG-015 | 已提交后不可修改（只能通过NEED_SUPPLEMENT补充） | spec-02 |
| CG-SCHOOL-REG-016 | DRAFT可物理删除；SUBMITTED后不可删除 | spec-11 |

---

## 领域事件

| 事件 | 触发时机 |
|------|------|
| SchoolRegistrationSubmitted | DRAFT → SUBMITTED |
| SchoolRegistrationApproved | SUBMITTED → APPROVED |
| SchoolRegistrationRejected | SUBMITTED → REJECTED |
| SchoolRegistrationWithdrawn | SUBMITTED/NEED_SUPPLEMENT → WITHDRAWN |
| SchoolRegistrationSupplementRequested | SUBMITTED → NEED_SUPPLEMENT |

---

## 不明确规则 (UNRESOLVED)

- CG-SCHOOL-REG-UNRESOLVED-001: rejected后新建申请时，旧申请的哪些数据可以复用？
- CG-SCHOOL-REG-UNRESOLVED-002: NEED_SUPPLEMENT是否有次数限制？

---

## 来源

- `docs/validation/business-spec-registry.yaml` §school_registration
- `docs/business-spec/02-学校入驻与账号管理规格.md`
- `docs/business-spec/11-生命周期与数据保留规格.md`
- `src/main/resources/db/migration/V002__create_school_domain.sql`
