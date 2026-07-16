# V1.2 技术栈决策总览

> **状态**: FROZEN TECHNICAL BASELINE
> **日期**: 2026-07-14
> **业务基线**: v1.2-AI-FROZEN (25 DEC, 17台状态机, 5份Accepted ADR)
> **补丁锁定阶段**: 项目脚手架

---

## 推荐技术栈

| 领域 | 决定 | 状态 |
|------|------|:--:|
| 架构 | 模块化单体 | ACCEPTED |
| 模块验证 | Spring Modulith (ArchUnit为补充候选) | ACCEPTED |
| 模块组织 | 按业务能力: identity/school/project/activity/score/ranking/appeal/media/result/feedback/notification/platform/audit | ACCEPTED |
| Java | Java 21 LTS | ACCEPTED |
| 后端框架 | Spring Boot 3.5.x | ACCEPTED |
| 构建 | Maven Wrapper | ACCEPTED |
| 数据库 | PostgreSQL 18 | ACCEPTED |
| 迁移 | Flyway | ACCEPTED |
| 写模型 | JPA Repository (聚合根) | ACCEPTED |
| 复杂查询 | JdbcClient / 显式SQL (jOOQ后续评估) | ACCEPTED |
| 前端 | Vue 3 + TypeScript + Vite | ACCEPTED |
| UI | Element Plus | ACCEPTED |
| 状态管理 | Pinia | ACCEPTED |
| E2E | Playwright | ACCEPTED |
| 认证 | Spring Security + Spring Session JDBC | ACCEPTED |
| 凭证 | HttpOnly Secure SameSite Cookie | ACCEPTED |
| Session存储 | PostgreSQL | ACCEPTED |
| API | REST + OpenAPI (后端代码优先) | ACCEPTED |
| 错误 | RFC 9457 Problem Details | ACCEPTED |
| 文件 | S3兼容接口 (所有环境, 本地MinIO) | ACCEPTED |
| 异步 | DB任务表/Outbox语义 (V1无MQ) | ACCEPTED |
| 缓存 | V1无Redis | ACCEPTED |
| 测试 | JUnit5 + Testcontainers + Playwright | ACCEPTED |
| 部署 | Docker Compose | ACCEPTED |

---

## PostgreSQL 18 选择原因

1. 绿地项目，无历史升级负担
2. PostgreSQL 18是当前稳定主版本
3. 满足部分唯一索引、CHECK约束、事务、JSONB、MVCC和复杂查询
4. 比17拥有更长的后续支持周期
5. 具体补丁版本在脚手架阶段根据当时稳定版本锁定

**重新评估触发条件**:
- 目标生产环境不支持PostgreSQL 18
- 关键扩展或驱动不兼容
- 托管数据库只能提供17
- 测试确认存在阻塞性兼容问题

---

## MySQL拒绝理由

MySQL 8.4支持CHECK约束，但缺少PostgreSQL式原生部分唯一索引。EffectiveScore唯一性(同一Student+ActivityProject最多一个当前有效)需要生成列、函数索引、额外关系表或应用层协调等替代建模。PostgreSQL对本项目的条件唯一约束和复杂一致性约束表达更直接。

---

## 规模说明

当前分析识别出约16个候选API业务模块（来自业务规格组织方式）。具体API端点数量尚未设计和冻结，将在后续API范围设计和OpenAPI合同阶段确定。本项目为中等规模的多模块后台管理系统。

---

## 物理删除原则

- 已产生业务关联的核心对象不得物理删除
- 使用业务状态表达停用、撤回、下架、取消和失效
- 不对所有表机械添加`deleted`字段
- 审核记录、成绩历史、版本快照、申诉记录和审计日志不得物理删除
- 具体保留期限服从业务规格11
- 数据库设计阶段逐表决定

---

## NON_BLOCKING_CHOICE

| 事项 | 延迟到 |
|------|------|
| jOOQ是否引入 | 数据库逻辑设计 |
| Spring Boot 3.5.x/PostgreSQL 18/Maven具体补丁版本 | 脚手架阶段 |
| Element Plus/Playwright具体版本 | 脚手架阶段 |
| 对象存储厂商 | 部署阶段 |
| 反向代理选型 | 部署阶段 |
| CI工具 | 脚手架阶段 |
| ArchUnit是否额外引入 | 数据库逻辑设计 |

---

## 关联文档

- ADR-006 ~ ADR-014 (9份Proposed技术ADR)
- ADR-001 ~ ADR-005 (5份Accepted聚合ADR)
- business-spec-registry.yaml (17台状态机, 25 DEC)
