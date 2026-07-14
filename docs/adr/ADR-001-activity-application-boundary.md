# ADR-001: ActivityApplication 聚合边界

> **状态**: Accepted
> **日期**: 2026-07-14
> **决策范围**: ActivityApplication 与 Activity 的聚合边界

---

## 1. 背景

业务规格明确活动申请和正式活动是"不同业务对象"(04 §2-3)，但未指定它们在领域模型中的聚合边界。申请通过后创建正式Activity，两者有明确的生命周期关联。

---

## 2. FACT

| # | FACT | 来源 |
|:-:|------|------|
| F1 | 老师提交活动申请，校管审批 | 04 §2 |
| F2 | 申请通过后系统创建新的正式Activity对象，初始状态为草稿 | 04 §2-3 |
| F3 | 活动申请与正式活动使用不同的状态字段 | 04 §2-3 |
| F4 | 申请被驳回后仅申请回到可修改状态，不会创建正式Activity | 04 §2 |
| F5 | 校管可以不经过申请直接创建活动 | 04 §2 |
| F6 | 申请状态: 草稿→已提交→已通过/已驳回/已撤回 | 04 §2 |
| F7 | 活动执行状态: 草稿→已发布→进行中→已结束/已取消 | 04 §3 |
| F8 | 活动申请通过后不可修改；驳回后回到草稿可修改重提 | 04 §2, 11 §6 |

---

## 3. DERIVED 约束

| # | 约束 | 推导 |
|:-:|------|------|
| D1 | 申请通过后必须创建Activity对象，两者是1:1关系（一个申请最多产生一个Activity） | F2 |
| D2 | 校管直接创建的活动没有关联的ActivityApplication | F5 |
| D3 | ActivityApplication的历史记录不应随Activity的取消/删除而丢失 | F8, 11 §6 |

---

## 4. 待裁决问题

Q1: ActivityApplication是否独立聚合根？
Q2: 申请通过和Activity创建是否必须同一事务？
Q3: 如何防止同一申请被重复审批创建多个Activity？
Q4: Activity创建失败时申请状态如何处理？
Q5: 是否需要`created_activity_id`关联字段？

---

## 5. 方案A: ActivityApplication 独立聚合根

ActivityApplication 和 Activity 各自为独立聚合根，通过ID引用。

**聚合内部对象**: ActivityApplication(申请版本)
**外部引用**: Activity.id (通过审批创建后记录)

**命令**:
- `SubmitApplication` (Teacher)
- `ApproveApplication` (SchoolAdmin) → 创建 Activity
- `RejectApplication` (SchoolAdmin)
- `WithdrawApplication` (Teacher, 审批前)

### 优点
- 符合规格"不同业务对象"声明
- 申请历史独立保留，不受Activity生命周期影响
- Activity可以直接创建（校管直创场景），不强制关联申请
- 审批幂等：审批前检查`created_activity_id`是否已存在

### 缺点
- 申请通过和Activity创建跨聚合，无法单事务原子完成
- 需处理"审批通过但Activity创建失败"的补偿

### 事务边界

- 审批操作是一个**不可部分成功的原子业务结果**: 批准申请 + 创建Activity(草稿) + 写入`created_activity_id`
- 对外不得出现"申请已通过但Activity不存在"的可见状态
- Activity创建失败时，申请必须保持或恢复为"已提交"
- 具体事务实现(本地事务/应用服务协调/TCC)留待技术选型
- 审批幂等: `created_activity_id`已存在→直接返回已创建的Activity

---

## 6. 方案B: ActivityApplication 作为 Activity 内部实体

ActivityApplication 是 Activity 聚合的一部分。

### 优点
- 申请通过+Activity创建可单事务完成
- 模型简单，无跨聚合问题

### 缺点
- **违反F3**: 规格明确"使用不同状态字段"，内部实体无法使用独立状态字段
- **违反F5**: 校管直接创建Activity时无申请来源，聚合内部需要处理可选子实体
- 申请历史随Activity删除而丢失（违反11 §6）
- Activity取消/结束时申请历史如何处理不明确

---

## 7. ADR_DECISION

**选择方案A: ActivityApplication 为独立聚合根。**

| 项目 | 决策 |
|------|------|
| 聚合根 | ActivityApplication |
| 聚合内部对象 | 申请版本记录(驳回→重提时的版本历史) |
| 外部引用 | Activity.id (通过审批后记录；校管直创时无此关联) |
| 引用方向 | ActivityApplication → Activity (单向ID引用) |
| 校管直创Activity | 不关联ActivityApplication，Activity上不记录申请来源字段 |

---

## 8. 聚合不变量

1. 同一申请最多产生一个Activity (`created_activity_id`唯一)
2. 已通过或已撤回的申请不可再修改
3. 未提交草稿可物理删除；已提交后不可删除
4. 驳回后重提保留历史审批记录

---

## 9. 强一致操作

- 申请审批(批准+创建Activity+写入`created_activity_id`): **不可部分成功的原子业务结果**
- 申请驳回/撤回: 单聚合事务

## 10. 最终一致操作

- 校管直创Activity: 独立操作，不涉及申请聚合（无申请来源）
- Activity取消/结束: 不影响申请历史（申请聚合无需感知）
- 申请历史保留: Activity生命周期变化不传播到申请聚合

---

## 11. 领域事件候选

- `ActivityApplicationSubmitted`
- `ActivityApplicationApproved` → 触发Activity创建
- `ActivityApplicationRejected`
- `ActivityApplicationWithdrawn`

---

## 12. 风险

| 风险 | 缓解 |
|------|------|
| 审批通过后Activity创建失败 | `created_activity_id`已记录，补偿重试创建；或用户可见"已通过(待创建)"状态 |
| 并发审批 | 乐观锁或数据库唯一约束(`application_id`→`created_activity_id`) |

---

## 13. OPEN_QUESTION

1. Activity创建失败后用户如何感知和手动触发重试？（技术选型后确定）
2. 申请版本历史存储策略？（数据库设计阶段确定）
3. 是否需要`ApplicationApproved`事件的at-least-once投递保证？

---

## 14. 被拒绝方案

**方案B**: 违反规格F3(独立状态字段)和F5(校管直创)。**拒绝**。

---

## 15. 规格追踪

| 规格 | 对应决策 |
|------|---------|
| 04 §2 "活动申请与正式活动是两个业务对象" | ADR-001: 独立聚合 |
| 04 §2 "使用不同的状态字段" | ADR-001: 独立聚合根 |
| 11 §6 "活动申请通过后创建正式活动" | ADR-001: 外部ID引用 |

---

## 16. 决策后果

- 数据库: `activity_applications`和`activities`为两张独立表
- API: 申请和活动的端点独立，通过`created_activity_id`关联
- 测试: 需覆盖"审批通过但Activity创建失败"的补偿场景
