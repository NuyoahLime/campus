# ADR-004: ActivityResult 聚合边界

> **状态**: Accepted
> **日期**: 2026-07-14
> **决策范围**: ActivityResult 的聚合边界及与 Activity 的关系

---

## 1. 背景

规格明确"成果已发布"不是活动执行状态(04 §3, 08 §6.5)。ActivityResult拥有独立的双状态模型。需裁决ActivityResult是独立聚合根还是Activity的内部实体。

---

## 2. FACT

| # | FACT | 来源 |
|:-:|------|------|
| F1 | result_internal_status(3状态)和result_public_status(7状态)是两个独立状态字段 | 08 §6.5 |
| F2 | "成果已发布"不是活动执行状态 | 04 §3, 08 §6.5 |
| F3 | 成果必须关联活动 | 08 §6.1 |
| F4 | 校内撤回触发平台已下架；恢复不自动恢复公开 | 08 §6.5 |
| F5 | 核心内容修改需重新审核；格式修改可保留公开状态 | 08 §6.3 |
| F6 | 超管可强制下架但不得代替校管执行正常发布 | 08 §6.3 |
| F7 | 被引用素材下架→成果进入公开异常待处理 | 08 §6.3 |
| F8 | 活动取消后成果按独立规则处理 | 04 §3 |

---

## 3. 方案A: ActivityResult 独立聚合根

ActivityResult 拥有独立身份和生命周期，通过Activity.id引用活动。

### 优点
- 符合F2: 成果状态独立于活动执行状态
- 成果可独立发布、撤回、下架，不受活动状态变化直接影响
- 活动结束后仍可编辑成果
- F8直接满足: 活动取消不删除成果

### 缺点
- 活动删除/取消时需处理关联成果（可通过F8规则: 保留成果）
- 1个Activity可有多少个Result？规格未明确→默认1:1

---

## 4. 方案B: ActivityResult 作为 Activity 内部实体

### 优点
- Activity和Result在同一聚合内，事务简单

### 缺点
- **违反F2**: "成果已发布"不是活动执行状态 → 内部实体无法拥有独立状态机
- **违反F8**: 活动取消时内部实体处理不明确
- Result的双状态(10状态组合)混入Activity聚合，聚合膨胀

---

## 5. ADR_DECISION

**选择方案A: ActivityResult 为独立聚合根。**

| 项目 | 决策 |
|------|------|
| 聚合根 | ActivityResult |
| 聚合内部对象 | result_internal_status, result_public_status, 成果内容(text/media引用/score_highlights) |
| 外部引用 | Activity.id (必填), Media[].id (引用已审核素材) |
| 与Activity关系 | 1个Activity对应1个ActivityResult (V1默认) |

---

## 6. 聚合不变量

1. result_internal_status和result_public_status独立，不得合并
2. 只有校内已发布时可提交平台公开审核
3. 校内撤回→若已公开→自动平台已下架
4. 恢复校内不自动恢复公开
5. 核心内容修改→公开状态回到未提交，需重新审核
6. 超管不得执行正常发布

---

## 7. 强一致操作

- 校内发布/撤回: 单聚合事务
- **校内撤回**: 若已公开→同时转为平台已下架(**必须同事务**)
- 成果编辑(草稿状态): 单聚合事务

## 8. 最终一致操作

- 被引用素材下架→成果进入公开异常: 可最终一致
- 活动取消→成果独立保留(无需联动操作)
- 平台公开审核: Result聚合更新

---

## 9. 领域事件候选

- `ResultInternalPublished`
- `ResultInternalWithdrawn` → 若已公开, 触发`ResultPublicTakenDown`
- `ResultPublicReviewApproved`
- `ResultPublicPublished`
- `ResultPublicAnomalyDetected` (素材下架触发)
- `ResultPublicTakenDown`

---

## 10. Activity:ActivityResult 基数裁决

**决定: Activity 1 : 0..1 ActivityResult (V1)**

- 规格08 §6使用单数语境("成果编辑""成果发布")，无证据支持1:N
- 一个ActivityResult可拥有多个不可变ResultVersion（修改→生成新版本）
- 不得把多个ResultVersion误认为多个ActivityResult
- V2如需多成果，通过新增ADR扩展

## 10b. ResultVersion 语义

1. 核心内容修改(标题/文字/成绩亮点/引用素材)→创建新ResultVersion→重新审核
2. 新版本审核通过前，旧公开版本继续展示
3. 新版本审核驳回，旧版本保持不变
4. 格式修改(不改业务语义/审核内容/公开实质)→同版本内更新+记录修改日志
5. ResultVersion不可变：已发布的版本快照不得修改

## 11. OPEN_QUESTION

1. 成果引用的Media保存ID引用还是内容快照？推荐ID引用+异常处理
2. 成果内容的富文本存储方案
3. 格式修改日志的保留策略

---

## 11. 被拒绝方案

**方案B**: 违反F2(独立状态)和F8(活动取消不删成果)。**拒绝**。

---

## 12. 规格追踪

| 规格 | 对应决策 |
|------|---------|
| 04 §3 "成果已发布不是活动执行状态" | ADR-004: 独立聚合根 |
| 08 §6.5 双状态 | ADR-004: 聚合内部双状态字段 |
| 08 §6.3 异常处理 | ADR-004: 最终一致 |

---

## 13. 决策后果

- 数据库: `activity_results`独立表，含`result_internal_status`和`result_public_status`两个独立列
- API: 成果CRUD独立于活动CRUD，通过`activity_id`关联
