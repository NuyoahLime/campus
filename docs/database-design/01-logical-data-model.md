# 逻辑数据模型 v1.2

> **任务**: TASK-DATABASE-001
> **状态**: PROPOSED LOGICAL DATA MODEL
> **日期**: 2026-07-14
> **权威输入**: ADR-001~014, business-spec 01~11, 注册表(17台状态机/25 DEC)
> **注意**: 不包含物理数据库设计(SQL/DDL/字段类型/索引)。下一阶段批准前不得据此创建表或迁移。

---

## 一、已读取的事实源

| # | 文件 | 提供约束 |
|:-:|------|------|
| 1 | `docs/validation/business-spec-registry.yaml` | 25 DEC, 17台状态机, P1/P2, spec_files映射 |
| 2 | `docs/decision/业务决策记录-v1.2.md` | 25 DEC决策 |
| 3-13 | `docs/business-spec/01~11` | 全部业务规则, 状态表, 权限矩阵, 审核记录字段 |
| 14 | `docs/business-spec/业务冻结检查表.md` | 冻结状态, P0/P1/P2 |
| 15-19 | `docs/adr/ADR-001~005` | 聚合边界(ActivityApplication/Activity/ScoreAttempt/Media/ActivityResult/L3Authorization) |
| 20-28 | `docs/adr/ADR-006~014` | 架构/后端/数据库/前端/认证/文件/API/异步/测试 |
| 29 | `docs/analysis/v1.2-state-machine-reconciliation.md` | 17台状态机对账 |

---

## 二、13模块聚合持久化地图

### identity
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| User | — | — | 待激活→正常→锁定→停用 |

### school
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| School | — | — | 待启用→正常→暂停→停用 |
| SchoolRegistration | — | — | 草稿→已提交→需补充→已通过/已驳回/已撤回 |

### project
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| ChallengeProject | ProjectRuleVersion, ProjectRuleCompatibility | — | 草稿→已上架→已下架 |

### activity
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| ActivityApplication | (申请版本记录) | Activity.id | 草稿→已提交→已通过/已驳回/已撤回 |
| Activity | ActivityProject, ResponsibleTeacher, ActivityParticipant | ChallengeProject.id, User.id(teacher/student) | 草稿→已发布→进行中→已结束/已取消 |

### score
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| ScoreAttempt | ScoreReviewRecord | ActivityProject.id, Student(User).id | 草稿→待审核→审核通过/审核驳回→已失效 |
| AbnormalScoreEntry | — | ActivityProject.id, Student.id | 草稿→待审批→已批准待录入→成绩审核中→已完成/已终止 |
| ScoreCorrectionRecord | — (独立关联记录) | old_ScoreAttempt.id, new_ScoreAttempt.id | 不可变 |

### ranking
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| RankingDefinition | RankingVersion | ChallengeProject.id, School.id(L1/L2) | 可停用/启用 |
| L3Authorization | — | School.id, ChallengeProject.id, RuleVersion.id | 草稿→待审核→审核通过→已暂停→已撤回 |

### appeal
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| ScoreAppeal | AppealRecord | ScoreAttempt.id, Student(User).id | 13状态完整流转 |

### media
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| Media | MediaReviewRecord(校内+平台) | Activity.id, Uploader(User).id | internal_status(5)+public_status(6) |

### result
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| ActivityResult | ResultVersion | Activity.id, Media[].id | internal(3)+public(7) |

### feedback
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| Feedback | — | Submitter(User).id | 已提交→处理中→已处理/已升级→已关闭 |

### notification
| 聚合根 | 内部实体 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| Notification | — | Recipient(User).id | 系统生成→已发送→已读 |

### platform
无独立聚合。平台治理通过School聚合、User聚合(校管账号)、L3Authorization聚合和AuditRecord表达。

### audit
| 实体 | 类型 | 外部ID引用 | 生命周期 |
|------|------|------|------|
| AuditRecord | 基础设施 | Actor(User).id, Target(type+id), School.id | 不可变追加 |

---

## 三、正式逻辑实体目录

**共30个逻辑实体**（不含基础设施记录）。

### Aggregate Root (16)
User, School, SchoolRegistration, ChallengeProject, ActivityApplication, Activity, ScoreAttempt, AbnormalScoreEntry, RankingDefinition, L3Authorization, ScoreAppeal, Media, ActivityResult, Feedback, Notification

### Internal Entity (4)
ProjectRuleVersion, ActivityProject, ResultVersion, RankingVersion

### Association Record (4)
ProjectRuleCompatibility, ScoreCorrectionRecord, ResponsibleTeacher, ActivityParticipant

### Domain Audit Record (3)
ScoreReviewRecord, MediaReviewRecord, AppealRecord

### Infrastructure Record (3)
SessionRecord, TaskRecord, AuditRecord

> Note: 注册表使用`Score`，ADR-002正式命名为`ScoreAttempt`。本模型使用`ScoreAttempt`。`RankingDefinition`和`RankingVersion`注册表中以version状态机定义，定义本身是隐含聚合根。

---

## 四、Q1-Q6 核心裁决

### Q1: User与School关系

**裁决**: User是认证主体。SchoolMembership表达学校成员关系。平台角色与学校角色分离。

| 概念 | 分类 | 说明 |
|------|------|------|
| User | Aggregate Root (identity模块) | 认证主体: user_id, 登录凭证, 账号状态 |
| SchoolMembership | DERIVED_LOGICAL_MODEL | User在School中的成员关系: membership_id, user_id, school_id, role_in_school, 有效时间范围, 状态 |
| StudentProfile | DERIVED_LOGICAL_MODEL | 学生专属业务属性(班级/年级等)，关联SchoolMembership |
| TeacherProfile | DERIVED_LOGICAL_MODEL | 教师专属业务属性(科目等)，关联SchoolMembership |
| PlatformRole | 值对象/枚举 | 超管标记在User上，非学校成员 |

V1约束: 一个User最多一个有效SchoolMembership。历史Membership保留。普通用户不得切换school上下文。

### Q2: StudentProfile与TeacherProfile

**裁决**: 认证属性属User；学校成员身份属Membership；学生/教师专属属性分别属StudentProfile/TeacherProfile。校管无额外业务属性，通过Membership.role表达。超管通过PlatformRole表达。

### Q3: EffectiveScore逻辑模型

**裁决: 方案A — ScoreAttempt自身当前有效标记**（配合数据库机械保障）。

| 维度 | ScoreAttempt自标记 | 独立关联 | 版本头指针 |
|------|:--:|:--:|:--:|
| 并发安全 | ✅ 单行CAS更新 | ✅ 独立行锁 | ⚠️ |
| 历史保留 | ✅ | ✅ | ✅ |
| 成绩更正 | ✅ 旧失效+新标记 | ⚠️ 需更新关联 | ⚠️ |
| 查询复杂度 | ✅ 简单过滤 | ⚠️ 需JOIN | ⚠️ |
| JPA复杂度 | ✅ | ⚠️ | ⚠️ |

逻辑语义: ScoreAttempt.is_current_effective = true 表示当前有效。同一(Student, ActivityProject)下最多一行is_current_effective=true。更正: 新ScoreAttempt审核通过→旧ScoreAttempt.is_current_effective=false(失效)+新ScoreAttempt.is_current_effective=true。数据库保障: 部分唯一约束或等价能力(物理设计阶段裁决具体实现)。

### Q4: ActivityProject

**裁决**: ActivityProject是独立持久化的逻辑关联实体，属于Activity聚合内部。

ScoreAttempt引用ActivityProject.id（不分别引用Activity和Project）。ActivityProject绑定Activity、ChallengeProject和ProjectRuleVersion，作为成绩、报名、排名的共同作用域。

### Q5: RankingVersion快照模型

**裁决**: 结构化RankingEntry快照属于RankingVersion，同时保留来源引用。

- RankingVersion包含: version_number, status, 计算参数快照, 数据范围快照
- RankingEntry(快照): rank_position, ScoreAttempt.id(引用), student_display_info, score_value, ProjectRuleVersion.id(引用)
- 已发布RankingVersion不可变
- 重算创建新RankingVersion
- 物理存储形式(JSON/关系表/混合)留到物理设计阶段

### Q6: 状态转换记录

**裁决**:
- 领域审核记录(ScoreReviewRecord, MediaReviewRecord, AppealRecord)属于对应聚合内部，表达业务审计事实
- 平台安全AuditRecord独立记录
- 不增设通用StateTransitionRecord
- 各聚合的状态字段直接保存当前状态
- 状态变更历史通过领域审核记录+AuditRecord双重覆盖

---

## 五、核心关系矩阵

| 来源 | 目标 | 关系 | 基数 | 跨聚合 | 引用方式 | 删除影响 |
|------|------|------|:--:|:--:|------|------|
| User | SchoolMembership | 拥有 | 1:N | 否 | — | 级联停用 |
| SchoolMembership | School | 引用 | N:1 | 是 | school_id | 阻止删除 |
| School | SchoolRegistration | 拥有 | 1:N | 否 | — | 保留历史 |
| ChallengeProject | ProjectRuleVersion | 拥有 | 1:N | 否 | — | 不可删除已引用版本 |
| ActivityApplication | Activity | 引用 | 1:0..1 | 是 | created_activity_id | 不影响申请 |
| Activity | ActivityProject | 拥有 | 1:N | 否 | — | 随Activity |
| ActivityProject | ChallengeProject | 引用 | N:1 | 是 | project_id | 阻止删除 |
| ActivityProject | ProjectRuleVersion | 引用 | N:1 | 是 | rule_version_id | 阻止删除 |
| ScoreAttempt | ActivityProject | 引用 | N:1 | 是 | activity_project_id | 阻止删除 |
| ScoreAttempt | User(Student) | 引用 | N:1 | 是 | student_id | 阻止删除 |
| ScoreAttempt | ScoreAttempt | 更正链 | N:1(单向) | 是 | replaces_id | 保留历史 |
| ScoreCorrectionRecord | ScoreAttempt | 关联 | N:2 | 是 | old_id, new_id | 不可变 |
| ScoreAppeal | ScoreAttempt | 引用 | N:1 | 是 | score_attempt_id | 保留历史 |
| RankingDefinition | RankingVersion | 拥有 | 1:N | 否 | — | 保留历史版本 |
| RankingVersion | ScoreAttempt | 快照引用 | N:M | 是 | entry.snapshot | 历史版本不变 |
| L3Authorization | School | 引用 | N:1 | 是 | school_id | 阻止删除 |
| Media | Activity | 引用 | N:1 | 是 | activity_id | 阻止删除 |
| Media | User(Uploader) | 引用 | N:1 | 是 | uploader_id | 保留历史 |
| ActivityResult | Activity | 引用 | 1:1 | 是 | activity_id | 保留历史 |
| ResultVersion | ActivityResult | 拥有 | N:1 | 否 | — | 保留历史版本 |
| Feedback | User(Submitter) | 引用 | N:1 | 是 | submitter_id | 保留历史 |
| Notification | User(Recipient) | 引用 | N:1 | 是 | recipient_id | 可清理 |

---

## 六、school隔离模型

| 对象 | 直接school归属 | 推导路径 | 历史固定 |
|------|:--:|------|:--:|
| User | ❌ | — | N/A |
| SchoolMembership | ✅ school_id | — | ✅ 历史Membership保留 |
| StudentProfile | ❌ | →Membership→School | ✅ |
| TeacherProfile | ❌ | →Membership→School | ✅ |
| School | ✅ (自身) | — | ✅ |
| SchoolRegistration | ❌ (入驻前无School) | →School(通过后) | ✅ |
| ChallengeProject | ❌ (平台级) | — | N/A |
| Activity | ✅ school_id | — | ✅ |
| ActivityProject | ❌ | →Activity→School | ✅ |
| ScoreAttempt | ✅ school_id(冗余) | →ActivityProject→Activity→School | ✅ |
| ScoreAppeal | ✅ school_id(冗余) | →ScoreAttempt→... | ✅ |
| RankingDefinition(L1/L2) | ✅ school_id | — | ✅ |
| L3Authorization | ✅ school_id | — | ✅ |
| Media | ✅ school_id(冗余) | →Activity→School | ✅ |
| ActivityResult | ✅ school_id(冗余) | →Activity→School | ✅ |
| Feedback(校内) | ✅ school_id | — | ✅ |
| Notification | ❌ (按接收人) | →Recipient→Membership | N/A |
| AuditRecord | ✅ school_id(可空) | — | ✅ |
| TaskRecord | ❌ (基础设施) | →关联业务对象推导 | N/A |

原则: 频繁用于授权隔离、且推导链>1跳的对象，在逻辑模型中冗余school_id。用户转校不改变历史对象的school归属。

---

## 七、17台状态机逐台映射

| # | 状态机 | 聚合 | 状态承载对象 | 领域审计记录 | 通用转换记录 | 终态 |
|:-:|------|------|------|------|:--:|------|
| 1 | school_registration | SchoolRegistration | SchoolRegistration.status | — | — | 已通过/已驳回/已撤回 |
| 2 | school | School | School.school_status | — | AuditRecord | 无(循环) |
| 3 | account | User | User.account_status | — | AuditRecord | 无(停用可恢复) |
| 4 | challenge_project | ChallengeProject | ChallengeProject.project_status | — | — | 无(循环) |
| 5 | activity_application | ActivityApplication | ActivityApplication.application_status | — | — | 已通过/已撤回 |
| 6 | activity_execution | Activity | Activity.execution_status | — | — | 已结束/已取消 |
| 7 | activity_public | Activity | Activity.public_status | — | AuditRecord | 无 |
| 8 | score | ScoreAttempt | ScoreAttempt.score_status | ScoreReviewRecord | — | 已失效 |
| 9 | abnormal_score_entry | AbnormalScoreEntry | AbnormalScoreEntry.entry_status | — | AuditRecord | 已完成/已终止 |
| 10 | ranking_version | RankingVersion | RankingVersion.version_status | — | — | 已撤回/已过期/已被替换/已作废 |
| 11 | l3_authorization | L3Authorization | L3Authorization.authorization_status | — | AuditRecord | 已撤回 |
| 12 | appeal | ScoreAppeal | ScoreAppeal.appeal_status | AppealRecord | — | 已驳回/已解决/已撤回 |
| 13 | media_internal | Media | Media.internal_status | MediaReviewRecord | — | 无 |
| 14 | media_public | Media | Media.public_status | MediaReviewRecord | — | 无 |
| 15 | result_internal | ActivityResult | ActivityResult.result_internal_status | — | — | 无 |
| 16 | result_public | ActivityResult | ActivityResult.result_public_status | — | AuditRecord | 无 |
| 17 | feedback | Feedback | Feedback.feedback_status | — | — | 已关闭 |

---

## 八、逻辑不变量登记册（27条）

| # | 不变量 | 来源 | 涉及对象 | 数据库保障类别 | 并发风险 |
|:-:|------|------|------|------|:--:|
| I01 | User登录标识唯一 | 02 §3.4 | User | 唯一性能力 | 低 |
| I02 | 学校统一识别编码全局唯一 | 02 §1.1 | School | 唯一性能力 | 低 |
| I03 | 普通用户不得跨校访问 | 01 §2.2 | 全部学校级对象 | school一致性校验 | 低 |
| I04 | ProjectRuleVersion不可覆盖 | 03 §4.2 | ProjectRuleVersion | 追加不可变 | 低 |
| I05 | Activity引用明确规则版本 | 04 §5.1 | ActivityProject | 引用完整性 | 低 |
| I06 | 同一Application最多生成一个Activity | ADR-001 | ActivityApplication | 唯一性能力 | 中 |
| I07 | 同一(Student, ActivityProject)最多一个当前有效ScoreAttempt | 05 §3.2, ADR-002 | ScoreAttempt | 唯一性能力+并发控制 | **高** |
| I08 | 成绩更正: 新有效+旧失效+CorrectionRecord原子 | 05 §6, ADR-002 | ScoreAttempt, CorrectionRecord | 原子事务 | **高** |
| I09 | ScoreAppeal引用明确ScoreAttempt | 07 §2 | ScoreAppeal | 引用完整性 | 低 |
| I10 | 相同ScoreAttempt同时最多一个处理中Appeal | 07 §5 | ScoreAppeal | 状态约束 | 中 |
| I11 | 已发布RankingVersion不可变 | 06 §5 | RankingVersion | 追加不可变+版本检查 | 低 |
| I12 | 同一RankingDefinition最多一个当前发布版本 | 06 §5 | RankingVersion | 唯一性能力 | 中 |
| I13 | Media公开必须internal_status=校内审核通过 | 08 §5.3 | Media | 状态约束 | 低 |
| I14 | 已引用Media不得物理删除 | 08 §4, 11 §11 | Media | 引用完整性 | 低 |
| I15 | 已发布ResultVersion不可覆盖 | 08 §6.5 | ResultVersion | 追加不可变 | 低 |
| I16 | Notification接收人必须有效 | 09 §2 | Notification | 引用完整性 | 低 |
| I17 | 审核记录追加不可变 | 全部规格 | ScoreReviewRecord等 | 追加不可变 | 低 |
| I18 | AuditRecord不可修改 | 11 §16 | AuditRecord | 追加不可变 | 低 |
| I19 | Task幂等处理 | ADR-013 | TaskRecord | 幂等控制 | 中 |
| I20 | 跨聚合引用不得跨school | 01 §2.2 | 全部 | school一致性校验 | 低 |
| I21 | 历史对象保留原school归属 | 11 §3-4 | ScoreAttempt等 | 追加不可变 | 低 |
| I22 | 状态转换符合17台状态机 | 注册表 | 全部 | 状态约束 | 低 |
| I23 | Activity取消不删除成绩/素材/成果 | 04 §3, 11 §7 | Activity | 引用完整性 | 低 |
| I24 | 学校停用→L3授权自动已撤回 | 06 §8, ADR-005 | L3Authorization | 原子事务 | 中 |
| I25 | 校内停用→平台下架(Media原子) | 08 §5.3 | Media | 原子事务 | 低 |
| I26 | 校内撤回→平台下架(Result原子) | 08 §6.5 | ActivityResult | 原子事务 | 低 |
| I27 | L3生成时双重检查(auth+school状态) | ADR-005 | L3Authorization, School | 状态约束 | 中 |

---

## 九、跨聚合事务登记册（15项）

| # | 业务命令 | 涉及聚合 | 必须原子 | 允许异步 |
|:-:|------|------|:--:|:--:|
| T01 | SchoolRegistration批准+School激活 | SchoolRegistration, School | ✅ | 否 |
| T02 | ActivityApplication批准+Activity创建+关联 | ActivityApplication, Activity | ✅ | 否 |
| T03 | Activity绑定ActivityProject+规则版本 | Activity(内部) | ✅ | 否 |
| T04 | ScoreAttempt首次生效(标记当前有效) | ScoreAttempt | ✅ | 否 |
| T05 | ScoreAttempt更正(新有效+旧失效+CorrectionRecord) | ScoreAttempt×2 | ✅ | 否 |
| T06 | Appeal裁决→新ScoreAttempt | ScoreAppeal, ScoreAttempt | ✅ | 否 |
| T07 | RankingVersion生成 | RankingDefinition(内部) | ✅ | 否 |
| T08 | RankingVersion发布(旧→已替换+新→已发布) | RankingDefinition(内部) | ✅ | 否 |
| T09 | ResultVersion发布 | ActivityResult(内部) | ✅ | 否 |
| T10 | Media+Result公开校验 | Media, ActivityResult | 否(校验) | — |
| T11 | 学校暂停/停用 | School | ✅ | L3联动可异步 |
| T12 | User角色撤销 | User, Membership | ✅ | Session失效可异步 |
| T13 | User账号停用 | User | ✅ | Session失效可异步 |
| T14 | Session主动失效 | User(基础设施) | 否 | ✅ |
| T15 | Task创建(与业务事务同提交) | TaskRecord | ✅ | 执行可异步 |

安全原则: 业务数据原子性优先；Session失效和通知生成可异步但不得成为安全唯一保障。

---

## 十、并发场景分析（14项）

| # | 场景 | 风险 | 保护不变量 | 推荐事务范围 | 所需并发控制 |
|:-:|------|------|------|------|------|
| C01 | 重复批准同一Application | 两个Activity | I06 | Application聚合 | 幂等键/唯一约束 |
| C02 | 两个操作同时创建当前有效成绩 | 两个"当前有效" | I07 | (Student,ActivityProject)范围 | 唯一约束+乐观锁 |
| C03 | 两个操作同时更正同一成绩 | 更正链分叉 | I08 | 旧ScoreAttempt行 | 乐观锁(版本检查) |
| C04 | 申诉裁决与人工更正并发 | 冲突裁决 | I08 | ScoreAttempt | 乐观锁 |
| C05 | Ranking生成期间成绩变化 | 排名不一致 | I11 | 生成快照隔离 | 读已提交+版本快照 |
| C06 | 两个版本同时发布为当前 | 两个"当前" | I12 | RankingDefinition | 乐观锁 |
| C07 | 两个ResultVersion同时发布 | 两个"当前公开" | — | ActivityResult | 乐观锁 |
| C08 | Media下架与Result发布并发 | 发布引用已下架 | I13 | Result聚合 | 发布前校验 |
| C09 | 学校停用与校内操作并发 | 停用后操作生效 | I24 | School聚合 | 操作前检查状态 |
| C10 | 角色撤销与敏感请求并发 | 旧权限执行 | 权限规则 | 无(实时数据库复核) | 每次请求复核 |
| C11 | Task重复消费 | 重复通知/重算 | I19 | TaskRecord | 幂等键 |
| C12 | Task人工重放与自动重试并发 | 双重处理 | I19 | TaskRecord | 状态机+乐观锁 |
| C13 | 同一Application并发撤回+批准 | 撤回后批准 | I06 | Application聚合 | 乐观锁 |
| C14 | 注册并发(同学校编码) | 重复入驻 | I02 | SchoolRegistration | 唯一约束 |

---

## 十一、删除和数据保留策略

| 实体 | 物理删除 | 替代状态/处理 | 历史保留 | 引用存在时 |
|------|:--:|------|------|------|
| User | ❌ | 停用 | 永久 | 阻止删除 |
| SchoolMembership | ❌ | 结束(记录end_date) | 永久 | — |
| School | ❌ | 停用 | 永久 | — |
| SchoolRegistration | 仅草稿(未提交) | 已通过/已驳回/已撤回 | 永久 | — |
| ChallengeProject | 仅草稿(无引用) | 已下架 | 永久 | 阻止删除 |
| ProjectRuleVersion | ❌ | — | 永久 | 阻止删除 |
| ActivityApplication | 仅草稿(未提交) | 已通过/已驳回/已撤回 | 永久 | — |
| Activity | 仅草稿(无成绩) | 已取消 | 永久 | 阻止删除 |
| ScoreAttempt | ❌ | 已失效 | 永久 | 阻止删除 |
| ScoreCorrectionRecord | ❌ | — | 永久 | — |
| ScoreAppeal | ❌ | 已撤回/已驳回/已解决 | 永久 | — |
| RankingDefinition | ❌(有版本后) | 停用 | 永久 | 阻止删除 |
| RankingVersion | ❌(已发布) | 已作废(未发布) | 永久 | — |
| L3Authorization | 仅草稿(未提交) | 已撤回 | 永久 | — |
| Media | 仅草稿(未提交) | 校内停用/平台下架 | 永久 | 被引用时阻止物理删除 |
| ActivityResult | 仅草稿(未发布) | 下架 | 永久 | — |
| ResultVersion | ❌(已发布) | — | 永久 | — |
| Feedback | ❌ | 已关闭 | 永久 | — |
| Notification | ✅ 按保留期 | — | 保留期 | — |
| AuditRecord | ❌ | — | 永久 | — |
| SessionRecord | ✅ 过期/失效后 | — | — | — |
| TaskRecord | ✅ 完成后按保留期 | — | 保留期+人工重放期 | — |

---

## 十二、Domain Event、Task与Outbox边界

| 概念 | V1使用 | 持久化 | 说明 |
|------|:--:|:--:|------|
| Domain Event | ✅ | 否(瞬时) | 表达业务事实；由Application Service发布 |
| TaskRecord | ✅ | ✅ | 异步工作: 排行榜重算、通知生成、学校状态派生联动、孤立Media清理 |
| Outbox Record | ❌ | ❌ | V1无跨进程可靠投递需求；如后续引入MQ则新增 |

TaskRecord字段: task_id, task_type, reference_type, reference_id, status, retry_count, next_retry_at, last_error, created_at。Handler幂等。

安全边界: 权限判断、学校状态判断、L3双重检查、审批、更正、发布等安全关键操作不得异步延迟。

---

## 十三、DERIVED_LOGICAL_MODEL对象

以下对象非业务规格直接命名，由聚合边界和school隔离需求推导:

| 对象 | 推导来源 | 分类 |
|------|------|------|
| SchoolMembership | ADR-010(school上下文), 01 §2.2(学校隔离) | 关联实体 |
| StudentProfile | ADR-010(User身份), 02 §3.3(学生属性) | 关联实体 |
| TeacherProfile | ADR-010(User身份), 02 §3.2(老师属性) | 关联实体 |
| RankingEntry | RankingVersion快照模型 | 快照实体 |
| SessionRecord | ADR-010(Session JDBC) | 基础设施 |
| TaskRecord | ADR-013(DB任务表) | 基础设施 |

---

## 十四、DATABASE_BLOCKER: 0
## 十五、DOMAIN_MODEL_BLOCKER: 0
## 十六、NON_BLOCKING_CHOICE（物理设计阶段裁决）

主键类型 | 字段类型和长度 | 具体索引和约束SQL | 乐观锁实现 | 外键策略 | JSON vs 关系表物理存储 | 表命名 | 审核历史共表/分表 | Session和Task表的物理结构

---

> **状态**: PROPOSED LOGICAL DATA MODEL
> **下一阶段**: TASK-DATABASE-001-C (物理表结构设计) — 需本模型验收批准后进入
