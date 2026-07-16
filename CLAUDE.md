# CLAUDE.md

## 1. 项目说明

本仓库是 **Campus Guinness** 后端项目。

项目采用：

* Java
* Spring Boot
* Spring Modulith
* Maven Wrapper
* PostgreSQL
* Flyway
* JPA / Spring Data JPA
* MinIO
* DDD 聚合建模
* 分层架构
* 批次化任务推进

Claude Code 在本仓库中的首要目标不是“尽快生成代码”，而是：

1. 保持领域模型和架构边界稳定；
2. 严格按当前批准任务工作；
3. 用测试和文档证明每项实现；
4. 不扩大任务范围；
5. 不破坏已经通过验收的领域模型、Migration 和持久化基线。

---

## 2. 当前项目状态

截至当前基线：

```text
TASK-DOMAIN-MODEL-001 = COMPLETED

AGGREGATES_TOTAL = 13
AGGREGATES_COMPLETED = 13
AGGREGATES_REMAINING = 0

DOMAIN_CLASSES = 121
AGGREGATE_ROOTS = 13
DOMAIN_ENTITIES = 1
VALUE_OBJECTS_IDS_ENUMS = 37
DOMAIN_EVENTS = 57
DOMAIN_EXCEPTIONS = 12
SEALED_SCORE_VALUE = 1

DOMAIN_TESTS = 282
PERSISTENCE_ARCHITECTURE_TESTS = 68
TOTAL_TESTS = 350

FAILURES = 0
ERRORS = 0
SKIPPED = 0

DOMAIN_FRAMEWORK_DEPENDENCIES = 0
CROSS_MODULE_DOMAIN_OBJECT_REFERENCES = 0
PUBLIC_SETTERS = 0

FLYWAY_MIGRATIONS = V001-V015 UNCHANGED
MODULITH_MODULES = 13
MODULE_VIOLATIONS = 0

LOCAL_STARTUP = PASS
ACTUATOR_HTTP_STATUS = 200
ACTUATOR_HEALTH = UP
```

当前应用层状态：

```text
TASK-APPLICATION-LAYER-001 = IN_PROGRESS
TASK-APPLICATION-LAYER-001-BATCH-01 = COMPLETED
```

已实现的 ChallengeProject 应用用例：

* CreateChallengeProject
* FindChallengeProject
* PublishChallengeProject

当前需要优先修正的架构问题：

```text
应用层不得直接依赖 JPA Entity 或 Spring Data Repository。
```

目标结构：

```text
Application Service
        ↓
Application Port 或 Domain Repository Interface
        ↓
Infrastructure Repository Adapter
        ↓
Persistence Mapper
        ↓
JpaRepository / JPA Entity
```

在该边界修正完成之前，不得扩展 SchoolRegistration、School、Controller、DTO 或 REST API。

---

## 3. 13 个已完成聚合

|  # | 聚合根                 | 模块       | 主要状态模型                       |
| -: | ------------------- | -------- | ---------------------------- |
|  1 | ChallengeProject    | project  | 3 states                     |
|  2 | SchoolRegistration  | school   | 6 states                     |
|  3 | School              | school   | 4 states                     |
|  4 | ActivityApplication | activity | 5 states                     |
|  5 | Activity            | activity | execution/public 双状态机        |
|  6 | ScoreAttempt        | score    | 5 states + sealed ScoreValue |
|  7 | ActivityResult      | result   | 双状态机                         |
|  8 | Media               | media    | internal/public 双状态机         |
|  9 | RankingDefinition   | ranking  | enable/disable               |
| 10 | L3Authorization     | ranking  | 6 states                     |
| 11 | ScoreAppeal         | appeal   | 13 states                    |
| 12 | Feedback            | feedback | 5 states                     |
| 13 | User                | identity | 4 states + memberships       |

这些聚合已经通过领域阶段验收。除非当前任务明确要求，否则不得重构其业务状态机、事件、异常或不变量。

---

## 4. 核心设计决策

正式设计决策位于：

```text
docs/domain/domain-model-decisions.md
```

当前至少包含：

### DEC-DOMAIN-001：MEDIA-REJECTED

* 业务状态名称：`REJECTED`
* Java 领域枚举：`INTERNAL_REJECTED`
* 数据库存储编码：`INTERNAL_REJECTED`
* 目的：显式区分内部拒绝与平台拒绝

不得将 `INTERNAL_REJECTED` 简化回模糊的 `REJECTED`，除非有新的正式裁决。

### DEC-DOMAIN-002：RESULT-PREV

* `next` 是允许状态转换的权威边
* `prev: []` 仅用于创建路径或展示性前驱信息
* 不得用 `prev` 推翻已经确认的 `next` 状态机

---

## 5. 永久架构原则

### 5.1 依赖方向

允许：

```text
Interface → Application → Domain
Infrastructure → Application Port / Domain Repository
Infrastructure → Domain
```

禁止：

```text
Domain → Application
Domain → Infrastructure
Domain → Spring
Domain → JPA
Application → JPA Entity
Application → JpaRepository
Application → EntityManager
Application → persistence.internal 包
```

### 5.2 各层职责

#### Domain

负责：

* 聚合和实体
* 值对象
* 状态机
* 领域不变量
* 领域事件
* 领域异常
* 聚合内部的一致性

不得负责：

* Repository 查询
* 事务
* 权限查询
* 通知
* JWT
* Session
* 文件上传
* 外部服务调用
* 数据库映射
* Controller 输入输出

#### Application

负责：

* Use Case 编排
* 事务边界
* Repository Port 调用
* 加载聚合
* 调用领域行为
* 保存聚合
* 跨聚合协调
* 幂等策略
* 应用异常
* 结果模型

不得负责：

* 复制领域状态机
* 直接修改聚合字段
* 直接操作 JPA Entity
* 直接依赖 Spring Data Repository
* 承载数据库映射
* 创建万能 Service

#### Infrastructure

负责：

* JPA Entity
* JpaRepository
* Repository Adapter
* Persistence Mapper
* MinIO
* 邮件、消息、外部系统
* 数据库和技术实现

#### Interface

负责：

* Controller
* Request / Response DTO
* 参数解析
* HTTP 状态码
* API 异常映射

Interface 层只有在明确批准后才能开始。

---

## 6. 应用层编码规范

### 6.1 包结构

按模块组织，推荐：

```text
com.campusguinness.<module>.application
├── command
├── query
├── service
├── result
├── exception
└── port
```

基础设施推荐：

```text
com.campusguinness.<module>.internal.persistence
```

或遵循仓库现有的等价包结构。

不要为了“统一”而一次性迁移所有现有包。

### 6.2 Command

Command 必须：

* 不可变；
* 只携带输入事实；
* 不包含 JPA Entity；
* 不包含 Repository；
* 不包含业务逻辑；
* 不使用 `Map<String, Object>`；
* 不使用模糊的通用字段集合。

命名示例：

```java
CreateChallengeProjectCommand
PublishChallengeProjectCommand
SubmitSchoolRegistrationCommand
```

### 6.3 Query

查询用例与修改用例必须区分。

查询方法应使用：

```java
@Transactional(readOnly = true)
```

不得通过查询用例偷偷修改聚合或数据库状态。

### 6.4 Result

Application Result：

* 不得返回 JPA Entity；
* 不得直接暴露可变聚合；
* 应返回用例所需的最小稳定结果；
* 与未来 HTTP DTO 保持分离。

推荐：

```java
ChallengeProjectResult.from(project)
```

或使用职责单一的：

```java
ChallengeProjectResultMapper
```

### 6.5 Application Service

Application Service 的标准流程：

```text
接收 Command
→ 验证应用级前置条件
→ Repository 加载聚合
→ 调用正式领域行为
→ Repository 保存聚合
→ 返回 Result
```

允许：

```java
@Transactional
```

禁止：

```java
project.setStatus(...)
project.changeStatus(...)
entity.setStatus(...)
repository.jpaSpecificMethod(...)
```

禁止在应用服务中复制如下领域判断：

```java
if (project.status() == SOME_STATE) {
    // 模拟领域状态机
}
```

应直接调用领域行为，由领域对象决定是否合法。

---

## 7. Repository 和持久化规则

### 7.1 Repository Port

应用层只依赖以下之一：

1. Domain Repository Interface；或
2. Application Port。

一个业务概念只能有一个权威 Repository 接口，不得同时创建两个语义重复的接口。

接口不得暴露：

* JPA Entity
* `JpaRepository`
* `Pageable`，除非应用层查询规范明确允许
* `EntityManager`
* 数据库列名
* 数据库内部主键细节

示例：

```java
Optional<ChallengeProject> findById(ChallengeProjectId id);

void save(ChallengeProject project);
```

### 7.2 Repository Adapter

Repository Adapter 位于基础设施层，负责：

```text
Domain Aggregate
↔ Persistence Mapper
↔ JPA Entity
↔ JpaRepository
```

Adapter 可以依赖 Spring 和 JPA。

Application 和 Domain 不得依赖 Adapter 的实现类。

### 7.3 Persistence Mapper

领域模型与 JPA Entity 的映射只能位于基础设施层。

命名推荐：

```java
ChallengeProjectPersistenceMapper
```

不得把以下三种映射混在同一个类中：

* Domain ↔ JPA Entity
* Domain → Application Result
* DTO ↔ Command

### 7.4 聚合恢复

从 Entity 恢复聚合时：

* ID 必须保持；
* 状态必须保持；
* 时间字段必须保持；
* 不得触发“创建事件”；
* 不得调用仅适用于新建流程的 `create()`；
* 不得通过公共 setter 恢复状态。

需要时可建立语义明确的恢复入口，例如：

```java
static ChallengeProject reconstitute(...)
```

恢复入口必须维持结构不变量，且不得提供任意修改状态的能力。

### 7.5 JPA Entity 可见性

不得为了应用层映射而增加：

* 全量 `public` setters；
* 面向应用层的公共无约束构造器；
* 暴露可变集合的方法。

JPA 无参构造器优先使用：

```java
protected EntityName() {
}
```

实体创建应由基础设施包内部构造器、工厂或 Mapper 完成。

---

## 8. 领域模型永久约束

领域代码必须保持：

```text
DOMAIN_FRAMEWORK_DEPENDENCIES = 0
CROSS_MODULE_DOMAIN_OBJECT_REFERENCES = 0
PUBLIC_DOMAIN_SETTERS = 0
```

领域代码禁止引用：

```text
org.springframework
jakarta.persistence
org.hibernate
org.springframework.data
org.springframework.security
jakarta.servlet
com.fasterxml.jackson
io.minio
org.testcontainers
```

聚合之间只通过 ID 引用，不得直接持有其他聚合根。

禁止在领域对象中调用：

```java
Instant.now()
LocalDateTime.now()
```

需要当前时间时，必须显式传入。

禁止通用状态修改方法：

```java
setStatus(...)
changeStatus(...)
transitionTo(...)
update(...)
```

状态变化必须使用正式业务行为。

失败的领域行为必须保证：

* 状态不变；
* 属性不变；
* 子实体或集合不变；
* 不产生领域事件。

---

## 9. 高风险领域边界

### 9.1 User 与认证凭据

`User` 管理业务身份和生命周期。

以下内容不属于 User 聚合：

* password hash
* 原始密码
* JWT
* OAuth
* Session
* Spring Security UserDetails
* PasswordEncoder
* 登录失败技术统计

认证凭据应由后续应用层和基础设施层实现。

### 9.2 User 与 SchoolMembership

`SchoolMembership` 已裁决为：

```text
USER_AGGREGATE_CHILD_ENTITY
```

约束：

* 不可脱离 User 存在；
* 通过 `SchoolId` 引用 School；
* 不得持有 School 对象；
* 不得升级为第 14 个聚合，除非有新的正式设计裁决。

### 9.3 L3Authorization

`L3Authorization` 是独立聚合。

User 不得持有：

```java
List<L3Authorization>
```

权限查询和授权检查属于应用层。

### 9.4 ScoreAppeal

ScoreAppeal 只管理申诉自身生命周期。

不得在聚合内部：

* 修改 ScoreAttempt；
* 创建 ScoreCorrectionRecord；
* 重算 ActivityResult；
* 更新 RankingVersion；
* 发送通知。

这些属于应用层跨聚合编排。

### 9.5 双状态机聚合

以下聚合包含双状态机或联动状态：

* Activity
* ActivityResult
* Media

不得在应用层复制其联动规则。

外部触发导致的跨聚合一致性应使用应用编排、领域事件和最终一致性补偿。

---

## 10. 延期表规则

共 17 张延期表，已经全部分类，不能重新随意解释。

```text
DEFERRED_TABLES_TOTAL = 17
DEFERRED_TABLES_CLASSIFIED = 17
DEFERRED_TABLE_BLOCKERS = 0
```

| 表                            | 分类                         | 归属                |
| ---------------------------- | -------------------------- | ----------------- |
| student_profiles             | AGGREGATE_CHILD_ENTITY     | User              |
| teacher_profiles             | AGGREGATE_CHILD_ENTITY     | User              |
| project_rule_versions        | AGGREGATE_CHILD_ENTITY     | ChallengeProject  |
| project_rule_compatibilities | RELATION_ENTITY            | ChallengeProject  |
| activity_projects            | AGGREGATE_CHILD_ENTITY     | Activity          |
| responsible_teachers         | RELATION_ENTITY            | Activity          |
| activity_participants        | RELATION_ENTITY            | Activity          |
| score_review_records         | IMMUTABLE_HISTORY_RECORD   | ScoreAttempt      |
| score_correction_records     | APPLICATION_LAYER_RECORD   | 跨聚合               |
| abnormal_score_entries       | AGGREGATE_CHILD_ENTITY     | ScoreAttempt      |
| ranking_versions             | IMMUTABLE_VERSION_SNAPSHOT | RankingDefinition |
| ranking_entries              | IMMUTABLE_SNAPSHOT_ENTRY   | RankingDefinition |
| ranking_entry_score_sources  | RELATION_ENTITY            | RankingDefinition |
| appeal_records               | IMMUTABLE_HISTORY_RECORD   | ScoreAppeal       |
| media_review_records         | IMMUTABLE_HISTORY_RECORD   | Media             |
| result_versions              | IMMUTABLE_VERSION_SNAPSHOT | ActivityResult    |
| task_records                 | INFRASTRUCTURE_TASK        | 基础设施              |

不要因为表存在就机械创建领域实体。

不要在未经批准的任务中实施这些延期对象。

---

## 11. Migration 和数据库保护

当前冻结：

```text
V001-V015
```

默认禁止：

* 修改 V001-V015；
* 新增 V016；
* 修改表、列、约束；
* 修改已有 JPA annotation；
* 修改 docker-compose；
* 升级依赖；
* 改写持久化测试断言以“适配”错误实现。

任何数据库或 Migration 变更都必须有明确任务批准。

验证命令：

```powershell
git diff -- src/main/resources/db/migration
git status --short src/main/resources/db/migration
```

---

## 12. 测试规范

### 12.1 Domain Test

领域测试必须：

* 使用纯 Java；
* 不启动 Spring；
* 不访问数据库；
* 不 Mock Repository；
* 测试合法路径和非法路径；
* 测试失败操作无副作用；
* 通过 `@DisplayName` 关联规则 ID。

禁止：

```java
@SpringBootTest
@DataJpaTest
Testcontainers
Repository
EntityManager
```

### 12.2 Application Test

应用服务测试优先使用：

* JUnit 5
* Mockito
* Mock Repository Port

应用测试不得引用：

* JPA Entity
* JpaRepository
* EntityManager
* Persistence Mapper

至少验证：

* 成功路径；
* 聚合不存在；
* Repository 调用；
* 领域异常透传；
* 应用异常转换；
* 保存时机；
* 返回结果。

### 12.3 Persistence Adapter Test

遵循仓库已有测试方式，不重复创建数据库测试框架。

至少验证：

* Domain → Entity 映射；
* Entity → Domain 恢复；
* ID、状态和时间字段保持；
* 查询不存在返回空结果；
* 恢复聚合不产生创建事件。

### 12.4 Architecture Test

必须持续检查：

```text
application 不依赖 JPA Entity
application 不依赖 Spring Data
application 不依赖 EntityManager
domain 不依赖 Spring/JPA
domain 不依赖 application
domain 不依赖 infrastructure
跨模块不直接引用其他聚合对象
```

### 12.5 精确测试统计

不得报告：

```text
~17
约 20
大约 350
```

测试数量必须从全新的 Surefire XML 中统计。

不得伪造测试结果。

不得在未实际执行时声称：

```text
BUILD SUCCESS
0 failures
health UP
```

---

## 13. 每次任务的标准执行流程

### 13.1 开始前

执行：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
git diff --stat
git diff --check
git diff
```

原则：

* 不覆盖来源不明的修改；
* 不删除用户代码；
* 不假设工作区干净；
* 先识别半成品、失败测试和未提交变更。

然后执行基线：

```powershell
.\mvnw.cmd clean verify
$LASTEXITCODE
```

### 13.2 规则审计

实现前必须：

1. 读取对应 rule-packet；
2. 读取 `domain-rule-catalog.md`；
3. 读取 `aggregate-model-matrix.md`；
4. 读取相关设计决策；
5. 读取对应领域类和测试；
6. 区分领域规则与应用层待实现规则。

规则分类只能使用：

```text
IMPLEMENTED
APPLICATION_LAYER_PENDING
UNRESOLVED
NOT_APPLICABLE
SUPERSEDED
```

不得通过删除规则让统计闭合。

### 13.3 测试先行

优先顺序：

```text
审计规则
→ 编写或确认失败测试
→ 最小实现
→ 针对性测试
→ 架构测试
→ 全量测试
→ Diff 审查
```

### 13.4 完成后

执行：

```powershell
git diff --check
git diff --stat
git diff

.\mvnw.cmd test
.\mvnw.cmd clean verify
$LASTEXITCODE
```

需要本地启动验证时：

```powershell
docker compose config
docker compose up -d
docker compose ps

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

健康检查：

```powershell
$response = Invoke-WebRequest `
  "http://localhost:8080/actuator/health" `
  -UseBasicParsing

$response.StatusCode
$response.Content
```

结束：

```powershell
docker compose down
```

禁止：

```powershell
docker compose down -v
```

---

## 14. 任务范围控制

只执行当前明确批准的 Task 和 Batch。

不得自动开始下一个批次。

不得顺手实现：

* Controller
* DTO
* REST API
* Swagger
* JWT
* 登录
* 密码
* Redis
* MQ
* 通知
* MinIO 上传
* 新 Migration
* 未批准的延期表

发现邻近问题时：

1. 记录问题；
2. 判断是否阻塞当前任务；
3. 不阻塞则列入剩余风险；
4. 阻塞则输出 `PARTIALLY_COMPLETED`；
5. 不扩大实现范围。

---

## 15. 修改原则

必须：

* 最小修改；
* 保持现有命名风格；
* 保持模块边界；
* 避免重复抽象；
* 优先复用已有模式；
* 所有新增行为有测试；
* 所有新增类型有明确职责。

禁止：

* 大规模重命名；
* 无任务依据的目录迁移；
* 为减少代码行数牺牲边界；
* 创建万能 `Utils`；
* 创建万能 `BusinessException`；
* 创建万能 `ApplicationService`；
* 用反射绕过封装；
* 用公共 setter 解决恢复问题；
* 修改测试以掩盖实现错误。

---

## 16. 当前应用层修正任务

当前应优先完成：

```text
TASK-APPLICATION-LAYER-001-BATCH-01A
```

目标：

```text
移除 Application → JPA Entity 依赖
```

必须达到：

```text
APPLICATION_TO_JPA_ENTITY_REFERENCES = 0
APPLICATION_TO_SPRING_DATA_REFERENCES = 0
DOMAIN_FRAMEWORK_DEPENDENCIES = 0
```

保留现有用例：

* CreateChallengeProject
* FindChallengeProject
* PublishChallengeProject

不得在该修正批次中增加新业务用例。

完成后才允许进入：

```text
TASK-APPLICATION-LAYER-001-BATCH-02
```

---

## 17. 完成报告规范

每个批次结束必须输出精确报告。

最低格式：

```text
TASK-APPLICATION-LAYER-001 = IN_PROGRESS
TASK-APPLICATION-LAYER-001-BATCH-XX = COMPLETED

USE_CASES_IMPLEMENTED:
- ...

APPLICATION_SERVICES = <精确数量>
REPOSITORY_PORTS = <精确数量>
REPOSITORY_ADAPTERS = <精确数量>
APPLICATION_TESTS = <精确数量>
TOTAL_TESTS = <精确数量>

FAILURES = 0
ERRORS = 0
SKIPPED = 0

APPLICATION_TO_JPA_ENTITY_REFERENCES = 0
APPLICATION_TO_SPRING_DATA_REFERENCES = 0
DOMAIN_FRAMEWORK_DEPENDENCIES = 0
CROSS_MODULE_DOMAIN_OBJECT_REFERENCES = 0

MAVEN_CLEAN_VERIFY = BUILD SUCCESS

NEXT_TASK = <下一任务>
STATUS = AWAITING_EXPLICIT_APPROVAL
```

同时附上：

* 实现的用例；
* 修改文件列表；
* Repository Port 位置；
* Adapter 和 Mapper 位置；
* 测试清单；
* 架构检查；
* 未解决规则；
* 剩余风险。

完成报告后立即停止，不得自动开始下一任务。

---

## 18. 阻塞处理

遇到以下情况不得猜测：

* 业务规格冲突；
* 状态机来源不一致；
* Repository 边界不明确；
* SchoolMembership 归属冲突；
* 用户认证职责不明确；
* 申诉成功后的跨聚合流程不明确；
* 排名版本和结果版本触发顺序不明确。

不阻塞基本实现：

```text
标记 UNRESOLVED
完成可确认部分
```

依赖查询、事务、权限或基础设施：

```text
标记 APPLICATION_LAYER_PENDING
```

阻塞当前任务：

```text
TASK-... = PARTIALLY_COMPLETED
STATUS = AWAITING_EXPLICIT_APPROVAL
```

必须说明：

* 阻塞规则 ID；
* 冲突来源；
* 已确认事实；
* 无法安全推断的内容；
* 最小裁决选项；
* 已完成内容；
* 未完成内容。

---

## 19. Claude Code 行为要求

Claude Code 必须：

* 先读取相关文件再修改；
* 先审计现有实现再创建新类型；
* 使用实际代码和测试作为事实来源；
* 明确区分事实、推断和建议；
* 对测试数字使用精确统计；
* 对无法验证的结果保持诚实；
* 保持任务串行；
* 完成后停止并等待批准。

Claude Code 不得：

* 声称执行了未执行的命令；
* 声称通过了未运行的测试；
* 伪造文件、类、规则或测试数量；
* 用常见行业经验替代冻结业务规格；
* 自动开启下一批次；
* 通过破坏领域封装换取开发便利；
* 将持久化模型泄漏到应用层或接口层。

---

## 20. 首要判断标准

每次修改前先回答以下问题：

1. 这项变化属于 Domain、Application、Infrastructure 还是 Interface？
2. 当前任务是否明确批准这项变化？
3. 是否让 Application 依赖了 JPA Entity？
4. 是否让 Domain 依赖了 Spring 或 JPA？
5. 是否复制了已经存在于聚合中的业务规则？
6. 是否新增了未经证据支持的状态或行为？
7. 是否需要新增测试或架构约束？
8. 是否改变了 V001-V015 或数据库基线？
9. 是否扩大了当前批次范围？
10. 完成后是否应该立即停止？

只要其中任何一项存在疑问，先审计和记录，不要盲目编码。
