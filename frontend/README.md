# Campus Guinness Frontend

校园吉尼斯纪录平台前端应用，基于 Vue 3 + TypeScript + Vite 构建。

## 环境要求

- Node.js >= 20
- npm >= 10

## 安装

```bash
cd frontend
npm install
```

## 开发

启动开发服务器（默认 http://localhost:5173）：

```bash
npm run dev
```

Vite 代理将 `/api` 请求转发到 `http://localhost:8080`（Spring Boot 后端）。

确保后端已在 `http://localhost:8080` 启动。

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | API 基础路径 | `/api` |

开发环境变量在 `.env.development` 中配置。
生产环境请复制 `.env.production.example` 为 `.env.production` 并修改。

## 测试

```bash
npm run test -- --run
```

## 类型检查

```bash
npm run type-check
```

## 代码检查

```bash
npm run lint
```

## 构建

```bash
npm run build
```

构建产物输出到 `dist/` 目录。

## 目录结构

```
frontend/
├── public/              静态资源
├── src/
│   ├── api/             API 请求模块
│   │   ├── http.ts      Axios 实例与拦截器
│   │   ├── public-project.ts   公开项目 API
│   │   └── public-activity.ts  公开活动 API
│   ├── components/      通用组件
│   ├── layouts/         布局组件
│   │   └── PublicLayout.vue    公共门户布局
│   ├── router/          路由配置
│   ├── stores/          状态管理 (Pinia)
│   ├── types/           TypeScript 类型定义
│   ├── utils/           工具函数
│   ├── views/           页面视图
│   │   ├── HomeView.vue              首页
│   │   ├── projects/
│   │   │   ├── ProjectListView.vue   项目列表
│   │   │   └── ProjectDetailView.vue 项目详情
│   │   └── activities/
│   │       ├── ActivityListView.vue   活动列表
│   │       └── ActivityDetailView.vue 活动详情
│   ├── __tests__/       测试文件
│   ├── App.vue          根组件
│   └── main.ts          入口文件
├── .env.development     开发环境变量
├── .env.production.example  生产环境变量示例
├── vite.config.ts       Vite 配置
├── vitest.config.ts     Vitest 配置
├── tsconfig.json        TypeScript 配置
└── package.json         项目配置
```
