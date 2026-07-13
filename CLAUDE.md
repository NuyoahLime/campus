# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 项目概述

校园吉尼斯挑战赛资源平台 — 面向多学校的校园挑战赛资源与活动管理平台。当前处于**业务规格冻结阶段**，尚未进入技术实现。没有任何应用代码、数据库或API。

## 核心命令

```bash
# 运行业务规格一致性校验（唯一需要运行的命令）
python scripts/validate_business_specs.py
```

校验脚本从 `docs/spec/business-spec-registry.yaml`（唯一事实源）读取DEC、状态机、延期项定义，逐文件检查12份业务规格文档的一致性。退出码0表示全部通过。

## 文档架构

```
docs/
├── spec/
│   ├── business-spec-registry.yaml    ← 唯一事实源：25 DEC + 7台状态机 + P1/P2 + 禁止短语
│   ├── 角色权限矩阵.md                 ← 五角色×全模块权限表
│   ├── 需求确认清单.md                 ← 全部81项TODO已决策关闭
│   ├── 核心业务实体.md                 ← 25个业务实体（无字段类型/无表设计）
│   ├── 页面与功能清单.md               ← 26个页面清单与归属关系
│   └── 业务流程与状态机.md             ← 27个业务流程 + 12台状态机
├── business-spec/                    ← 12份业务规格（01~11 + 冻结检查表）
│   └── 业务冻结检查表.md              ← 冻结状态、DEC追踪矩阵、P0/P1/P2统计
├── decision/
│   └── 业务决策记录-v1.2.md           ← 25个DEC完整记录
├── validation/                       ← 校验产物副本
└── source/                           ← 原始需求PDF + 提取文本 + 截图
```

## 工作原则

- **这是纯文档项目，不编写任何代码。** 禁止创建数据库表、API、ORM模型、前端组件、Docker配置。
- 所有规格文档版本统一为 `v1.2-AI-FROZEN`。不要新增版本号。
- 标签体系：`SOURCE-CONFIRMED` / `SOURCE-CONFLICT` / `AI-DECIDED` / `AI-DECIDED-FROM-CONFLICT` / `DEFERRED-P1:<ID>` / `DEFERRED-P2:<ID>`
- 设计原则：学校数据隔离优先、最小权限、MVP优先、超管不越级、历史可追溯、职责分离、状态机前后对称。
- 终态不得有后续状态。已产生业务关联的数据不得物理删除。冻结规则中不得出现"原则上""通常""一般"等模糊表达。
- DEC编号共25个（DEC-PERMISSION-001 ~ DEC-LIFECYCLE-001），不得增减。决策包13个。P1共6项，P2共4项。
- 修改规格后必须运行校验脚本确认通过。校验未通过时禁止将剩余P0写为0。
