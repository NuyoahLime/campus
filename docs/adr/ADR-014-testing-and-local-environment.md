# ADR-014: 测试与本地环境

> **状态**: Accepted
> **日期**: 2026-07-14

---

## 背景

需要确定V1测试策略和本地开发环境。17台状态机、跨聚合事务和权限规则需要可验证的测试体系。

---

## TECH_DECISION

### 后端测试

| 层级 | 工具 |
|------|------|
| 单元测试 | JUnit 5 |
| 集成测试 | Spring Boot Test + Testcontainers |
| 数据库 | Testcontainers PostgreSQL 18 |
| 文件 | Testcontainers MinIO |
| API测试 | MockMvc或RestTestClient |
| 模块边界 | Spring Modulith测试 |

### 前端测试

| 层级 | 工具 |
|------|------|
| 单元 | Vitest |
| 组件 | Vue Test Utils |
| E2E | Playwright |

### Playwright范围

- Chromium主流程
- Firefox和WebKit关键兼容流程
- 登录和Session场景
- 角色与路由权限
- 跨校拒绝
- 关键状态机业务闭环
- 文件上传
- 审核与申诉流程

### 禁止项

- 禁止SQLite替代PostgreSQL测试
- 禁止H2替代PostgreSQL集成测试
- 禁止生产环境ORM自动建表

### 本地环境

Docker Compose基线:
- PostgreSQL 18
- MinIO
- 后端(JAR)
- 前端Nginx

### CI基线

- lint → format → typecheck
- 单元测试
- 集成测试(Testcontainers)
- OpenAPI生成校验
- 前端类型生成校验
- 前端测试 + E2E(Playwright)
