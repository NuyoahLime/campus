# V1.2 系统架构基线

> **状态**: FROZEN TECHNICAL BASELINE
> **日期**: 2026-07-14

---

## 架构形态: 模块化单体

单一部署单元，内部按业务能力划分为13个模块。模块之间通过Application Service和领域事件解耦。

## 模块清单

| 模块 | 业务范围 | 主要聚合 |
|------|------|------|
| identity | 认证、Session、用户账号 | User |
| school | 学校入驻、学校管理 | School, SchoolRegistration |
| project | 挑战项目资源库 | ChallengeProject, RuleVersion |
| activity | 活动申请、活动管理 | ActivityApplication, Activity |
| score | 成绩录入、审核、更正、异常补录 | ScoreAttempt |
| ranking | 排行榜定义、版本、L3授权 | RankingDefinition, L3Authorization |
| appeal | 成绩申诉 | ScoreAppeal |
| media | 素材上传、审核 | Media |
| result | 活动成果 | ActivityResult |
| feedback | 反馈 | Feedback |
| notification | 站内通知 | Notification |
| platform | 超管治理、审计、系统配置 | (跨模块) |
| audit | 操作日志、个人数据访问审计 | AuditLog |

## 模块内部结构

```
{module}/
├── domain/          # 聚合、实体、值对象、领域事件接口
├── application/     # Application Service、业务命令、查询
├── infrastructure/  # JPA实现、外部服务适配、任务表
└── interfaces/      # REST Controller、DTO、OpenAPI注解
```

## 跨模块协作

- 聚合间通过ID引用
- 跨聚合原子业务结果通过Application Service协调
- 领域事件接口定义在domain包，实现在infrastructure包
- V1模块间通信: 直接方法调用（非异步消息）

## 模块边界验证

- Spring Modulith: 模块依赖检查、模块集成测试、架构文档
- ArchUnit: 补充候选，用于Spring Modulith无法直接表达的自定义规则
- V1不得同时维护两套重复的模块规则体系
- 脚手架阶段优先落实Spring Modulith验证

## 数据库

- PostgreSQL 18，单一数据库实例
- 所有模块共享同一数据库
- 学校级数据通过school_id列隔离
- 迁移通过Flyway管理

## 部署

- Docker Compose (PostgreSQL + MinIO + 后端 + 前端Nginx)
- 后端单JAR部署
- 前端静态资源通过Nginx服务
