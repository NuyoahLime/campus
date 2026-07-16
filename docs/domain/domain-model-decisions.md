# 领域模型设计决策记录

> 记录不属于冻结规格原文、但影响实现一致性的跨文档推断和解释规则。

---

## DEC-DOMAIN-001: MEDIA-REJECTED — 业务状态名称与持久化编码

**问题**: `business-spec-registry.yaml` 中 `media_internal` 状态机使用 `REJECTED`，但 DDL CHECK 约束使用 `INTERNAL_REJECTED`。

**冲突来源**:
- 注册表 `media_internal` 状态机: `code: REJECTED`
- `V009__create_media_domain.sql` CHECK: `'INTERNAL_REJECTED'`
- `media` 表同时包含 `internal_status` 和 `public_status` 两个独立 CHECK 约束

**采用结论**: Java 领域枚举使用 `INTERNAL_REJECTED`，区分于 `PLATFORM_REJECTED`。

**映射策略**:
```text
业务状态名称 = REJECTED（注册表上下文：media_internal 状态机内）
Java 领域枚举 = INTERNAL_REJECTED（MediaInternalStatus 枚举值）
数据库编码     = INTERNAL_REJECTED（DDL CHECK 约束值）
```

**证据**:
1. 单状态机聚合（如 `activity_application`）使用 `REJECTED` 无歧义
2. 双状态机聚合（`media` 的 internal + public）需要前缀区分
3. 6 台存在回边的状态机均在初始状态使用 `prev: []`（系统性惯例，非个别错误）

**是否属于直接规格事实**: 否 — 属于跨文档一致性推断，基于双状态机命名冲突的工程判断。

**影响的 Java 类型**: `MediaInternalStatus.INTERNAL_REJECTED`

**影响的测试**: `MediaTest` internal_status transitions

**未来修正规则**: 如注册表未来统一命名（如 `media_internal` 的 `REJECTED` 改为 `INTERNAL_REJECTED`），Java 枚举无需变更。

---

## DEC-DOMAIN-002: RESULT-PREV — 注册表 `prev` 数组语义

**问题**: 注册表多个状态机的初始状态（`DRAFT`、`NOT_SUBMITTED`）`prev` 数组为空（`[]`），但其 `next` 数组中存在回到这些状态的转换。

**冲突来源**:
- `result_internal` DRAFT: `prev: []`, 但 INTERNAL_WITHDRAWN `next: [DRAFT]`
- `result_public` NOT_SUBMITTED: `prev: []`, 但 PLATFORM_REJECTED/PLATFORM_TAKEDOWN/ANOMALY_PENDING `next: [NOT_SUBMITTED]`
- 同模式在 activity_application、score、media_internal、media_public 中重复出现（6 台无例外）

**采用结论**: `prev` 数组不表示完整反向边集合。`next` 数组是有效转换的权威源。

**解释规则**:
```text
next = 允许转换的权威正向边（代码实现以此为准）
prev = 仅用于"创建路径"标记或展示性前驱信息
初始状态（prev: []）= 创建时的起始状态，即使存在回到该状态的循环转换
```

**证据**:
1. 6 台状态机无一例外：所有带循环回到初始状态的状态机均使用 `prev: []`
2. 如 `prev` 为完整集合，则 DRAFT 应包含 REJECTED、INTERNAL_WITHDRAWN 等多个前驱
3. 现有 10 个聚合的领域代码均以 `next` 为准实现，无一致性问题

**是否属于直接规格事实**: 否 — 属于项目级解释规则，注册表未明确说明 `prev` 语义。

**影响的 Java 类型**: 所有状态枚举和聚合根的转换守卫逻辑

**影响的测试**: 所有聚合的 `IllegalTransitions` 测试

**未来修正规则**: 如注册表明确区分 `initial`（仅创建路径）和 `prev`（完整前驱集合），需更新此解释。

---
