# ADR-009: 前端技术栈

> **状态**: Accepted
> **日期**: 2026-07-14

---

## 背景

系统是浏览器管理后台，需要复杂表单(成绩录入/活动配置)、权限路由、审核工作台和文件上传。

## 候选方案

### 方案A: Vue 3 + TypeScript + Vite + Element Plus + Pinia (推荐)
### 方案B: React + TypeScript + Ant Design

---

## TECH_DECISION

**选择方案A: Vue 3 + TypeScript + Vite + Element Plus + Pinia。**

| 项目 | 决定 |
|------|------|
| 框架 | Vue 3 Composition API |
| 类型 | TypeScript 严格模式 |
| 构建 | Vite |
| 路由 | Vue Router |
| 状态管理 | Pinia |
| UI组件 | Element Plus |
| 请求层 | 从OpenAPI生成类型化客户端 |
| 表单验证 | 从后端DTO约束派生 |

## 测试

| 层级 | 工具 |
|------|------|
| 单元 | Vitest |
| 组件 | Vue Test Utils |
| E2E | Playwright (Chromium主, Firefox+WebKit兼容) |

**拒绝Cypress**: Playwright多浏览器支持和Session/Cookie测试能力更好。

## 拒绝方案

**方案B(React)**: Vue 3 + Element Plus对管理后台和复杂表单场景匹配度更高；国内生态更好。
