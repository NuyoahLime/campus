# ADR-007: 后端运行时与构建

> **状态**: Accepted
> **日期**: 2026-07-14

---

## 背景

需要确定后端语言、框架和构建工具。17台状态机、跨聚合事务和复杂权限规则需要成熟生态。

## FACT

- 存在跨聚合原子业务结果(ADR-001, ADR-002)
- 需要关系事务支持(R1, R2)
- 需要服务端Session和权限校验(R13)

## 候选方案

### 方案A: Java 21 + Spring Boot 3.5.x + Maven (推荐)

### 方案B: TypeScript + NestJS

---

## TECH_DECISION

**选择方案A: Java 21 LTS + Spring Boot 3.5.x + Maven Wrapper。**

| 项目 | 决定 |
|------|------|
| Java | 21 LTS |
| Spring Boot | 3.5.x |
| 构建 | Maven Wrapper |
| 补丁版本 | 脚手架阶段锁定 |
| Spring Boot 4.x | 保留为后续候选，不进入V1 |

**Spring Boot 4.x升级触发条件**: 关键依赖兼容 + 测试兼容 + OpenAPI兼容 + 文件SDK兼容 + 数据访问兼容 + 升级收益>迁移成本。不得在脚手架后无ADR直接跨主版本升级。

## 拒绝方案

**方案B(NestJS)**: TypeScript生态的事务和ORM成熟度不及Spring Boot；17台状态机的类型安全在两者均可满足但Spring生态更成熟。

## 对数据库设计的约束

- 事务管理通过Spring `@Transactional`
- Repository通过Spring Data JPA接口
- 数据库迁移通过Flyway
