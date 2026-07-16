# 应用层架构规范

> **版本**: v1.0 | **日期**: 2026-07-15 | **阶段**: TASK-APPLICATION-LAYER-001

---

## 1. 职责边界

```
Domain (internal/domain):
  业务规则、状态机、不变量、领域事件

Application (application):
  用例编排、事务边界、跨聚合协调、Repository调用、领域对象装配

Infrastructure (internal/persistence):
  JPA Entity、Repository实现、数据库映射

Interface (未实现):
  Controller、DTO、REST API
```

---

## 2. 包结构

```
com.campusguinness.<module>.application
├── command          ← 不可变输入命令
├── service          ← 应用服务（用例编排）
├── result           ← 用例返回结果
├── mapper           ← 领域对象↔持久化实体映射
└── exception        ← 应用层异常
```

---

## 3. Command 规则

- 不可变 Java record
- 只包含输入事实
- 不包含业务逻辑
- 不引用 Entity 或 Aggregate
- 字段使用基本类型和值对象

```java
public record CreateChallengeProjectCommand(
    String name,
    String category,
    ScoreStorageType scoreStorageType,
    ScoreIndicatorType scoreIndicatorType,
    ComparisonDirection comparisonDirection,
    String effectiveScoreRule,
    boolean allowTie,
    String description
) {}
```

---

## 4. ApplicationService 规则

- `@Transactional` 标注
- 接收 Command，返回 Result
- 调用 Repository 加载 Aggregate
- 调用领域行为方法
- 保存 Aggregate
- 不绕过领域方法直接修改状态
- 不复制领域规则

```java
@Transactional
public class ChallengeProjectApplicationService {
    // 加载 → 领域行为 → 保存
}
```

---

## 5. 事务边界

每个 Use Case 一个事务：

```
事务开始
  ↓
加载 Aggregate
  ↓
执行领域行为
  ↓
保存 Aggregate
  ↓
提交事务
  ↓
处理领域事件（事务外）
```

禁止一个事务修改多个无关 Aggregate。

---

## 6. 异常处理

| 层次 | 异常类型 | 示例 |
|------|------|------|
| Domain | 领域异常 | `InvalidProjectStateTransitionException` |
| Application | 应用异常 | `ChallengeProjectNotFoundException` |

应用层负责将持久化异常转换为应用异常。领域异常直接透传。

---

## 7. Repository 使用

- Application 依赖 Repository 接口
- Repository 返回 JPA Entity
- Mapper 负责 Entity ↔ Aggregate 转换
- 不在 Application 中直接操作 EntityManager

---

## 8. 禁止事项

- ApplicationService 不得设置 aggregate.status = xxx
- 不得在 Application 中复制领域不变量
- 不得使用 Map<String, Object> 作为命令或结果
- 不得创建万能 XxxService
- 不得在 Application 中直接调用 Instant.now()（通过 Clock 或参数传入）
