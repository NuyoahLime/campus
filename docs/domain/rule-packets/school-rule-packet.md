# School 规则包

> **聚合**: School | **模块**: school | **状态**: 本轮实现

---

## 聚合标识

- 聚合根: School
- 聚合ID: SchoolId (UUID)
- 创建来源: SchoolRegistration审批通过后创建
- 外部引用: 无（School是被引用方）
- 无内部子实体（V1）

---

## 创建入口

- SchoolRegistration审批通过时由应用层创建
- 初始状态: PENDING_ENABLE
- platform内部编码(internal_code)由超管生成，唯一且不可重用

---

## 状态机 (source: registry §school)

```
PENDING_ENABLE → NORMAL ⇄ SUSPENDED
                    ↓            ↓
                  DISABLED ←─────┘
                    ↓
              PENDING_ENABLE
```

| 状态 | 前驱 | 后继 | 说明 |
|------|------|------|------|
| PENDING_ENABLE | (initial), DISABLED | NORMAL | 待启用(仅有1个校管时) |
| NORMAL | PENDING_ENABLE, SUSPENDED | SUSPENDED, DISABLED | 正常运行 |
| SUSPENDED | NORMAL | NORMAL, DISABLED | 暂停(不能新建业务) |
| DISABLED | NORMAL, SUSPENDED | PENDING_ENABLE | 停用(禁止登录) |

---

## 状态转换规则

| 规则ID | 转换 | 前置条件 | 操作者 |
|------|------|------|------|
| CG-SCHOOL-001 | PENDING_ENABLE → NORMAL | ≥2个正常状态校管 | SuperAdmin |
| CG-SCHOOL-002 | NORMAL → SUSPENDED | 超管执行，需填写原因 | SuperAdmin |
| CG-SCHOOL-003 | NORMAL → DISABLED | 超管执行，需填写原因 | SuperAdmin |
| CG-SCHOOL-004 | SUSPENDED → NORMAL | 超管恢复 | SuperAdmin |
| CG-SCHOOL-005 | SUSPENDED → DISABLED | 超管执行 | SuperAdmin |
| CG-SCHOOL-006 | DISABLED → PENDING_ENABLE | 学校重新启用 | SuperAdmin |

---

## 非法转换

| 规则ID | 禁止操作 |
|------|------|
| CG-SCHOOL-007 | NORMAL → PENDING_ENABLE (不可逆) |
| CG-SCHOOL-008 | DISABLED → NORMAL (必须经过PENDING_ENABLE) |
| CG-SCHOOL-009 | SUSPENDED → PENDING_ENABLE |

---

## 业务不变量

| 规则ID | 不变量 | 来源 |
|------|------|------|
| CG-SCHOOL-010 | 学校名称≤200字符，非空 | schema |
| CG-SCHOOL-011 | internal_code一旦生成不可修改 | spec-02 |
| CG-SCHOOL-012 | DISABLED后历史数据保留，账号禁止登录 | spec-02, spec-11 |
| CG-SCHOOL-013 | SUSPENDED后不能新建活动、录入新成绩、发布新内容，但可处理待办 | spec-02 |
| CG-SCHOOL-014 | 恢复后已下架公开内容不自动重新发布 | spec-02 |

---

## 领域事件

| 事件 | 触发时机 |
|------|------|
| SchoolActivated | PENDING_ENABLE → NORMAL |
| SchoolSuspended | NORMAL → SUSPENDED |
| SchoolRestored | SUSPENDED → NORMAL |
| SchoolDisabled | NORMAL/SUSPENDED → DISABLED |
| SchoolReEnabled | DISABLED → PENDING_ENABLE |

---

## School与SchoolRegistration的边界

- School是独立聚合根，不由SchoolRegistration聚合管理
- SchoolRegistration审批通过后，应用层创建School并关联`created_school_id`
- School停用/暂停不影响SchoolRegistration历史记录
- School重新启用不恢复已撤回的授权（见ADR-005）

---

## 不明确规则

- CG-SCHOOL-UNRESOLVED-001: PENDING_ENABLE下学校管理员是否可以修改学校基本信息？

---

## 来源

- `docs/validation/business-spec-registry.yaml` §school
- `docs/business-spec/02-学校入驻与账号管理规格.md`
- `docs/business-spec/11-生命周期与数据保留规格.md`
- `src/main/resources/db/migration/V002__create_school_domain.sql`
