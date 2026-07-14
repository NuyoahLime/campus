# ADR-002: Score 聚合边界

> **状态**: Accepted
> **日期**: 2026-07-14
> **决策范围**: Score / ScoreAttempt / EffectiveScore 的聚合边界

---

## 1. 背景

成绩是最复杂的业务对象之一：每次录入为独立尝试、需分别审核、有效成绩参与排行、审核后不可原地覆盖而必须通过更正流程。需要裁决"尝试""成绩""有效成绩"三者的聚合关系。

---

## 2. FACT

| # | FACT | 来源 |
|:-:|------|------|
| F1 | 同一学生在同一活动项目中可以有多次成绩尝试 | 05 §3.1 |
| F2 | 每次尝试单独记录，分别审核 | 05 §3.1 |
| F3 | 只有审核通过的当前有效成绩参与排行榜 | 05 §3.2 |
| F4 | 审核通过后不得直接覆盖；更正创建新记录→重新审核→原成绩失效 | 05 §6 |
| F5 | 审核人≠录入人；校管不得自审 | 05 §2 |
| F6 | 每个活动项目必须指定有效成绩选择规则(最好/最后/管理员指定)，默认最好 | 05 §3.2 |
| F7 | 成绩有业务发生时间，不得用录入/提交/审核时间代替 | 05 §3.3 |
| F8 | 异常补录有独立状态机(7状态) | 05 §9 |
| F9 | 无效或被替代成绩继续作为历史记录保留 | 05 §3.2 |

---

## 3. DERIVED 约束

| # | 约束 | 推导 |
|:-:|------|------|
| D1 | 同一(学生, 活动项目)下最多一个"当前有效成绩" | F3+F6 |
| D2 | 选择有效成绩的规则属于活动项目配置，不属于单个成绩 | F6 |
| D3 | 异常补录审批后仍需进入正常成绩审核流程 | F8 |

---

## 4. 术语定义

| 术语 | 定义 | 分类 |
|------|------|:--:|
| **ScoreAttempt** (成绩尝试) | 每次录入的独立记录，拥有自己的状态机(草稿→待审核→审核通过/审核驳回→已失效) | FACT — 有独立状态机 |
| **EffectiveScore** (当前有效成绩) | 在某(学生, 活动项目)范围内，根据有效成绩选择规则选出的当前应参与排行的成绩 | DERIVED — 派生关系/索引 |
| **ScoreCorrectionRecord** | 审核通过后的更正操作记录，关联原成绩和新成绩 | FACT |

---

## 5. 方案A: 每次尝试为独立 Score 聚合根

每个 ScoreAttempt 是一个聚合根，拥有独立的身份、状态机和审核记录。

**聚合内部**: ScoreAttempt + ScoreReviewRecord + ScoreCorrectionRecord
**EffectiveScore**: 不独立存储，由查询投影或索引派生

### 优点
- 符合F1-F2: 每次尝试独立记录、独立审核
- 审核人和录入人的职责分离在聚合内自然保证
- 不会形成"学生+项目下全部尝试"的大聚合
- EffectiveScore作为派生结果，不存在"修改有效成绩"的命令——只能通过创建新尝试+更正流程间接改变

### 缺点
- 保证同一(学生,项目)下只有一个当前有效成绩需要跨ScoreAttempt的约束
- "取最优成绩"规则需要在查询层或应用层实现

---

## 6. 方案B: 学生+项目下全部尝试为一个聚合

以(Student, ActivityProject)为边界，包含该学生在该项目下的全部ScoreAttempt。

### 优点
- 有效成绩选择在聚合内完成，不需要跨聚合协调

### 缺点
- **违反F2**: 每次尝试应"独立记录、分别审核"——大聚合内无法体现独立身份
- 聚合随活动参与学生数增长而变大
- 聚合内状态机混乱：多个ScoreAttempt各自有独立状态
- 更正操作影响聚合内两个对象

---

## 7. 方案C: ScoreAttempt独立聚合 + EffectiveScore独立聚合

### 优点
- EffectiveScore有独立身份

### 缺点
- **无规格证据**: EffectiveScore没有独立状态机、没有独立业务命令、没有独立生命周期
- EffectiveScore无法"直接修改"——其变化总是由ScoreAttempt的状态变化或有效成绩规则变化驱动
- 引入不必要的跨聚合同步

---

## 8. ADR_DECISION

**选择方案A: 每次ScoreAttempt为独立聚合根，EffectiveScore为派生关系。**

| 项目 | 决策 |
|------|------|
| 聚合根 | ScoreAttempt (每次成绩尝试为一个聚合实例) |
| 聚合内部对象 | ScoreReviewRecord (审核历史，聚合内追加) |
| ScoreCorrectionRecord | 独立关联记录：关联新旧两个ScoreAttempt，由应用服务在更正操作中创建。不属于任一ScoreAttempt聚合内部 |
| EffectiveScore | 业务语义：同一(Student, ActivityProject)在任一时刻最多有一个当前有效ScoreAttempt。是否持久化为独立关系留待数据库设计 |
| 外部引用 | ActivityProject.id, Student(User).id |
| 更正引用 | 新ScoreAttempt通过`replaces_score_attempt_id`单向引用被替代的旧ScoreAttempt |

---

## 9. 聚合不变量（ScoreAttempt聚合内）

1. 审核人 ≠ 录入人（同一次尝试内）
2. 审核通过后不可直接修改score_value
3. 新ScoreAttempt审核通过前，被引用的旧ScoreAttempt保持原状态

## 9b. 跨聚合约束（应用层/查询层保证）

1. 同一(Student, ActivityProject)在任一时刻最多有一个当前有效ScoreAttempt
2. 更正操作: 新ScoreAttempt审核通过 + 旧ScoreAttempt→已失效 + ScoreCorrectionRecord创建 → 不可部分成功
3. 旧ScoreAttempt在新ScoreAttempt审核通过**之后**才失效
4. `replaces_score_attempt_id`为单向引用（新→旧），不能替代完整的CorrectionRecord

---

## 11. 跨聚合约束（需应用层/查询层保证）

1. 同一(Student, ActivityProject)下最多一个当前EffectiveScore
2. 有效成绩选择规则读取所有审核通过的ScoreAttempt，按项目配置的规则计算
3. 并发控制策略（防止两个尝试同时被标记为有效）: **应用层乐观锁或数据库部分唯一索引** — 具体实现在技术设计阶段确定

---

## 12. 强一致操作

- 单次ScoreAttempt的录入/提交/审核/驳回: **单聚合事务**
- **更正操作** (跨两个ScoreAttempt聚合，应用服务协调): 新ScoreAttempt审核通过 + 旧ScoreAttempt标记失效 + 创建ScoreCorrectionRecord → **不可部分成功的原子业务结果**
- 旧ScoreAttempt在新ScoreAttempt审核通过**之后**才失效，不可提前

## 13. 最终一致操作

- 新EffectiveScore的计算和排行榜重算: **最终一致** (通过领域事件触发)
- 异常补录审批: 审批和成绩录入分属不同聚合
- ScoreCorrectionRecord创建: 由应用服务在更正完成时创建，保证完整性

---

## 14. 领域事件候选

- `ScoreAttemptSubmittedForReview`
- `ScoreAttemptApproved` → 触发EffectiveScore重算 → 触发排行榜重算
- `ScoreAttemptRejected`
- `ScoreCorrectionCreated`
- `ScoreCorrected` → 同上连锁

---

## 15. OPEN_QUESTION

1. EffectiveScore的具体实现: 物化视图、Redis缓存还是查询时实时计算？（技术选型后确定）
2. 并发控制具体方案: 乐观锁版本号 vs 数据库部分唯一索引 vs 分布式锁？（数据库选型后确定）
3. 异常补录聚合是否应独立？（当前作为独立状态机，归属待定）

---

## 16. 被拒绝方案

- **方案B**: 违反F2独立记录原则 + 聚合膨胀。**拒绝**。
- **方案C**: EffectiveScore无独立业务命令和生命周期。**拒绝**。

---

## 17. 规格追踪

| 规格 | 对应决策 |
|------|---------|
| 05 §3.1 "每次尝试单独记录，分别审核" | ADR-002: 每次尝试独立聚合 |
| 05 §6 "审核通过后不得直接覆盖" | ADR-002: 更正=新聚合+关联记录 |
| 05 §3.2 "只有审核通过的当前有效成绩参与排行榜" | ADR-002: EffectiveScore=派生 |

---

## 18. 决策后果

- 数据库: `score_attempts`表, EffectiveScore通过查询/物化视图派生
- 并发: 需在(Student, ActivityProject)上建立有效成绩唯一约束或乐观锁
- API: 成绩录入/审核/更正操作以ScoreAttempt为单位
