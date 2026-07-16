# 领域规则目录

> **版本**: v1.1 | **日期**: 2026-07-15 | **任务**: TASK-DOMAIN-MODEL-001 BATCH-02

---

## 规则类型

| 类型 | 说明 |
|------|------|
| INVARIANT | 必须始终成立的业务约束 |
| STATE_TRANSITION | 状态转换规则 |
| VALUE_CONSTRAINT | 值对象/字段约束 |
| UNIQUENESS_RULE | 唯一性约束 |
| AUTHORIZATION_PRECONDITION | 操作授权前置条件 |
| VERSION_RULE | 版本/快照规则 |
| LIFECYCLE_RULE | 生命周期规则 |
| DOMAIN_EVENT_RULE | 领域事件触发规则 |

---

## 规则清单

### ChallengeProject (project) — 试点已完成

| 规则ID | 规则描述 | 类型 | 来源 | 实现状态 | 测试 |
|------|------|------|------|:--:|------|
| CG-PROJECT-001 | 创建时状态为DRAFT | STATE_TRANSITION | registry, spec-03 | ✅ | CreationTest |
| CG-PROJECT-002 | DRAFT→PUBLISHED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-PROJECT-003 | PUBLISHED→ARCHIVED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-PROJECT-004 | ARCHIVED→PUBLISHED(重上架) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-PROJECT-005 | DRAFT↛ARCHIVED(禁止直接归档) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-PROJECT-006 | 项目名称非空且≤200字符 | VALUE_CONSTRAINT | schema, spec-03 | ✅ | ProjectNameTest |
| CG-PROJECT-007 | ScoreConfig必填字段非空 | INVARIANT | spec-03 | ✅ | ScoreConfigTest |

**统计**: IMPLEMENTED=7, UNRESOLVED=0, APPLICATION_LAYER_PENDING=0, NOT_APPLICABLE=0, SUPERSEDED=0

---

### SchoolRegistration (school) — BATCH-01

| 规则ID | 规则描述 | 类型 | 来源 | 实现状态 | 测试 |
|------|------|------|------|:--:|------|
| CG-SCHOOL-REG-001 | DRAFT→SUBMITTED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-002 | SUBMITTED→NEED_SUPPLEMENT | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-003 | SUBMITTED→APPROVED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-004 | SUBMITTED→REJECTED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-005 | SUBMITTED→WITHDRAWN | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-006 | NEED_SUPPLEMENT→SUBMITTED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-007 | NEED_SUPPLEMENT→WITHDRAWN | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-REG-008 | DRAFT↛APPROVED | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-REG-009 | DRAFT↛NEED_SUPPLEMENT | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-REG-010 | APPROVED→终态不可逆 | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-REG-011 | REJECTED→不可修改重提(终态) | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-REG-012 | 学校名称≤200字符，非空 | VALUE_CONSTRAINT | schema, spec-02 | ✅ | Creation |
| CG-SCHOOL-REG-013 | unified_code_type非空 | VALUE_CONSTRAINT | schema | ✅ | Creation |
| CG-SCHOOL-REG-014 | 学校类型、地区、地址、联系人信息非空 | INVARIANT | spec-02 | ✅ | Creation |
| CG-SCHOOL-REG-015 | 已提交后不可修改 | INVARIANT | spec-02 | ✅(final字段) | — |
| CG-SCHOOL-REG-016 | DRAFT可物理删除；SUBMITTED后不可删除 | LIFECYCLE_RULE | spec-11 | ⏸️ APP | — |

**统计**: IMPLEMENTED=15, APPLICATION_LAYER_PENDING=1, UNRESOLVED=2, NOT_APPLICABLE=0, SUPERSEDED=0

---

### School (school) — BATCH-01

| 规则ID | 规则描述 | 类型 | 来源 | 实现状态 | 测试 |
|------|------|------|------|:--:|------|
| CG-SCHOOL-001 | PENDING_ENABLE→NORMAL (activate) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-002 | NORMAL→SUSPENDED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-003 | NORMAL→DISABLED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-004 | SUSPENDED→NORMAL (restore) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-005 | SUSPENDED→DISABLED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-006 | DISABLED→PENDING_ENABLE (re-enable) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-SCHOOL-007 | NORMAL↛PENDING_ENABLE | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-008 | DISABLED↛NORMAL（必须经PENDING_ENABLE） | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-009 | SUSPENDED↛PENDING_ENABLE | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-SCHOOL-010 | 学校名称≤200字符，非空 | VALUE_CONSTRAINT | schema | ✅ | Creation |
| CG-SCHOOL-011 | internal_code不可修改 | VALUE_CONSTRAINT | spec-02 | ✅(final字段) | — |
| CG-SCHOOL-012 | DISABLED后历史保留，账号禁止登录 | LIFECYCLE_RULE | spec-02, spec-11 | ⏸️ APP | — |
| CG-SCHOOL-013 | SUSPENDED后不能新建业务 | LIFECYCLE_RULE | spec-02 | ⏸️ APP | — |
| CG-SCHOOL-014 | 恢复后已下架内容不自动重新发布 | LIFECYCLE_RULE | spec-02 | ⏸️ APP | — |

**统计**: IMPLEMENTED=11, APPLICATION_LAYER_PENDING=3, UNRESOLVED=1, NOT_APPLICABLE=0, SUPERSEDED=0

---

### ActivityApplication (activity) — BATCH-02

| 规则ID | 规则描述 | 类型 | 来源 | 实现状态 | 测试 |
|------|------|------|------|:--:|------|
| CG-ACT-APP-001 | DRAFT→SUBMITTED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-ACT-APP-002 | SUBMITTED→APPROVED | STATE_TRANSITION | registry, ADR-001 | ✅ | StateTransitions |
| CG-ACT-APP-003 | SUBMITTED→REJECTED | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-ACT-APP-004 | SUBMITTED→WITHDRAWN | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-ACT-APP-005 | REJECTED→DRAFT (revise, version++) | STATE_TRANSITION | registry | ✅ | StateTransitions |
| CG-ACT-APP-006 | DRAFT↛APPROVED | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-ACT-APP-007 | DRAFT↛REJECTED | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-ACT-APP-008 | APPROVED→终态不可逆 | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-ACT-APP-009 | WITHDRAWN→终态不可逆 | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-ACT-APP-010 | REJECTED↛SUBMITTED(须经DRAFT) | STATE_TRANSITION | registry | ✅ | IllegalTransitions |
| CG-ACT-APP-011 | title≤200字符，非空 | VALUE_CONSTRAINT | schema, spec-04 | ✅ | Creation |
| CG-ACT-APP-012 | schoolId, applicantId非空 | INVARIANT | schema | ✅ | Creation |
| CG-ACT-APP-013 | rejectReason驳回时必填 | INVARIANT | physical-schema | ✅ | StateTransitions |
| CG-ACT-APP-014 | applicationVersion从1开始，驳回重提递增 | VERSION_RULE | physical-schema | ✅ | Creation/StateTransitions |
| CG-ACT-APP-015 | createdActivityId唯一(每申请最多一个Activity) | UNIQUENESS_RULE | ADR-001 | ✅ | StateTransitions |
| CG-ACT-APP-016 | SUBMITTED后不可修改(DRAFT可修改) | INVARIANT | spec-04 | ✅ | FieldMutation |
| CG-ACT-APP-017 | DRAFT可物理删除；SUBMITTED后不可删除 | LIFECYCLE_RULE | spec-11 | ⏸️ APP | — |

**统计**: IMPLEMENTED=16, APPLICATION_LAYER_PENDING=1, UNRESOLVED=1, NOT_APPLICABLE=0, SUPERSEDED=0

---

## APPLICATION_LAYER_PENDING规则清单

| 规则ID | 描述 | 所属聚合 |
|------|------|------|
| CG-SCHOOL-REG-016 | DRAFT可物理删除；SUBMITTED后不可删除 | SchoolRegistration |
| CG-SCHOOL-012 | DISABLED后账号禁止登录 | School |
| CG-SCHOOL-013 | SUSPENDED后不能新建业务 | School |
| CG-SCHOOL-014 | 恢复后已下架内容不自动重新发布 | School |
| CG-ACT-APP-017 | DRAFT可物理删除；SUBMITTED后不可删除 | ActivityApplication |
| CG-ACT-APP-APP-001 | 重复申请检查 | ActivityApplication |
| CG-ACT-APP-APP-002 | 原子批准+活动创建(ADR-001 §9) | ActivityApplication |
| CG-ACT-APP-APP-003 | 学校状态检查 | ActivityApplication |
| CG-ACT-APP-APP-004 | 申请人角色检查(须为Teacher) | ActivityApplication |
| CG-ACT-APP-APP-005 | 审批结果通知发送 | ActivityApplication |

---

## 未解决规则 (UNRESOLVED)

| 规则ID | 描述 | 冲突来源 | 建议 |
|------|------|------|------|
| CG-SCHOOL-REG-UNRESOLVED-001 | rejected后新建申请时数据复用范围 | spec-02 不明确 | 需产品确认 |
| CG-SCHOOL-REG-UNRESOLVED-002 | NEED_SUPPLEMENT次数限制 | spec-02 未规定 | 需产品确认 |
| CG-SCHOOL-UNRESOLVED-001 | PENDING_ENABLE下可否修改基本信息 | spec-02 未规定 | 需产品确认 |
| CG-ACT-APP-UNRESOLVED-001 | 驳回重提次数限制 | spec-04 未规定 | 需产品确认 |

---

## 全局统计

| 聚合 | IMPL | APP | UNRESOLVED | NA | SUPERSEDED | 合计 |
|------|:--:|:--:|:--:|:--:|:--:|:--:|
| ChallengeProject | 7 | 0 | 0 | 0 | 0 | 7 |
| SchoolRegistration | 15 | 1 | 2 | 0 | 0 | 18 |
| School | 11 | 3 | 1 | 0 | 0 | 15 |
| ActivityApplication | 16 | 6 | 1 | 0 | 0 | 23 |
| **合计** | **49** | **10** | **4** | **0** | **0** | **63** |
