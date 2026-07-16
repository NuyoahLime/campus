# TASK-DOMAIN-MODEL-001

> **开始日期**: 2026-07-15 | **分支**: design/v1.2-technical-stack

---

## 任务目标

建立可验证的领域模型，表达业务概念、聚合边界、状态转换、不变量、值对象、领域异常和领域事件。不实现应用层、接口层或完整业务流程编排。

---

## 试点聚合选择

**选择: ChallengeProject**

理由:
- 最简单的状态机（仅3个状态: DRAFT → PUBLISHED → ARCHIVED）
- 无外键依赖（`current_rule_version_id`为nullable cycle FK）
- 规则来源明确（DEC-PROJECT-001，规格03）
- 不涉及认证、排名计算或复杂审批
- 平台级共享资源，独立性强

---

## 聚合根确认（13个，基于已Accepted ADR）

| # | 聚合根 | 模块 | ADR依据 | 状态机 |
|:-:|------|------|------|:--:|
| 1 | User | identity | (隐式) | account (4 states) |
| 2 | School | school | (隐式) | school (4 states) |
| 3 | SchoolRegistration | school | (隐式) | school_registration (6 states) |
| 4 | ChallengeProject | project | (隐式) | challenge_project (3 states) |
| 5 | ActivityApplication | activity | ADR-001 | activity_application (5 states) |
| 6 | Activity | activity | (隐式) | activity_execution (5) + activity_public (7) |
| 7 | ScoreAttempt | score | ADR-002 | score (5 states) |
| 8 | RankingDefinition | ranking | (隐式) | — (ranking_version: 7 states) |
| 9 | L3Authorization | ranking | ADR-005 | l3_authorization (6 states) |
| 10 | ScoreAppeal | appeal | (隐式) | appeal (13 states) |
| 11 | Media | media | ADR-003 | media_internal (5) + media_public (6) |
| 12 | ActivityResult | result | ADR-004 | result_internal (3) + result_public (7) |
| 13 | Feedback | feedback | (隐式) | feedback (5 states) |

---

## 实施批次

| 批次 | 聚合 | 状态 |
|------|------|:--:|
| 试点 | ChallengeProject | 进行中 |
| 第一批 | SchoolRegistration, School | 待定 |
| 第二批 | ActivityApplication, Activity | 待定 |
| 第三批 | ScoreAttempt | 待定 |
| 第四批 | ActivityResult, Media | 待定 |
| 第五批 | RankingDefinition, L3Authorization | 待定 |
| 第六批 | ScoreAppeal, Feedback | 待定 |
| 第七批 | User | 待定 |

---

## 领域规则来源

- `docs/validation/business-spec-registry.yaml`: 17台状态机 + 25 DEC + P1/P2
- `docs/business-spec/01~11`: 11份业务规格
- `docs/adr/ADR-001~005`: 5份聚合边界决策
- `docs/decision/业务决策记录-v1.2.md`: 25个DEC完整记录
