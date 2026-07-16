# ADR-008: 数据库与持久化

> **状态**: Accepted
> **日期**: 2026-07-14

---

## 背景

需要确定V1主数据库。EffectiveScore唯一约束(同一Student+ActivityProject最多一个当前有效)需要数据库级保证。

## FACT

- R10: EffectiveScore唯一性约束
- R4: 历史版本不可覆盖
- R5: 文件元数据与二进制分离
- R8: 物理删除禁止(已产生业务关联的数据)

## 候选方案

### 方案A: PostgreSQL 18 (推荐)
### 方案B: MySQL 8.4

---

## TECH_DECISION

**选择方案A: PostgreSQL 18。**

| 项目 | 决定 |
|------|------|
| 数据库 | PostgreSQL 18 |
| 迁移 | Flyway |
| 建表 | 禁止ORM自动建表/改表 |
| 时区 | UTC存储，展示层转换 |
| 时长成绩 | 64位整数毫秒 |
| 高精度数值 | 明确精度的DECIMAL/NUMERIC |
| JSON | 仅用于快照、配置、审计上下文(非核心关系/状态/权限) |
| 补丁版本 | 脚手架阶段锁定 |

**EffectiveScore唯一性**: 同一Student和ActivityProject在任一时刻最多存在一个当前有效ScoreAttempt（业务约束）。应用层必须检查。数据库层必须提供机械保障。并发情况下不得出现两个当前有效ScoreAttempt。具体实现（部分唯一索引/独立EffectiveScoreSelection关系/其他约束模型）在数据库逻辑设计阶段比较和裁决。PostgreSQL支持部分唯一索引是优势，但不是已锁定的物理模型。

**MySQL拒绝理由**: 虽然支持CHECK约束，但对本项目的条件唯一约束和复杂一致性约束，PostgreSQL表达更直接、成熟度更高。

**PostgreSQL 18重新评估**: 目标生产不支持/关键扩展不兼容/托管仅17/阻塞兼容问题 → 通过新ADR降级到17。

---

## JPA边界

1. JPA用于聚合写模型和简单查询
2. 领域对象与JPA持久化对象可以分离
3. Controller不得直接调用Repository
4. 禁止通用CRUD Service绕过业务命令
5. 聚合间只保存ID引用
6. 禁止为ORM便利建立双向关联
7. 审核/版本历史可独立持久化，逻辑归属按ADR
8. 不要求加载聚合全部历史才执行状态转换
9. 复杂查询使用读模型，不反向扩大聚合
10. Open Session in View默认关闭
11. 懒加载不得跨越Application Service事务边界
12. 排行榜和审计查询允许显式SQL
13. jOOQ保持NON_BLOCKING_CHOICE

## 物理删除原则

- 已产生业务关联的核心对象不得物理删除
- 使用业务状态(停用/撤回/下架/取消/失效)表达
- 不对所有表机械添加deleted字段
- 审核记录、成绩历史、版本快照、申诉记录、审计日志不得物理删除
- 临时上传、无引用的孤立文件、技术临时记录可物理清理
- 具体保留期限服从业务规格11
- 数据库设计阶段逐表决定，不使用全局软删除拦截器
