# Auth Deployment Topology & Browser Verification Plan

> **状态:** `AWAITING_PROXY_IMPLEMENTATION_APPROVAL`
> **日期:** 2026-07-18 | **Commit:** e823282

---

## 1. 部署资产搜查结果

| 资产 | 存在？ | 用途 |
|------|:--:|------|
| `docker-compose.yml` | YES | PostgreSQL 18 + MinIO |
| `.env.example` | YES | 数据库和 MinIO 环境变量模板 |
| nginx.conf / Caddyfile | NO | — |
| k8s / helm | NO | — |
| Dockerfile | NO | — |
| 前端构建配置 | NOT_FOUND | 前端仓库未在本目录 |

```text
VERDICT: 项目尚未配置任何反向代理、TLS 或生产部署基础设施。
当前为纯本地 HTTP 开发环境。
```

## 2. 部署拓扑选择

**当前阶段:** `LOCAL_HTTPS_ACCEPTANCE`

```text
SELECTED_DEPLOYMENT_TOPOLOGY = SAME_ORIGIN_WITH_REVERSE_PROXY

TARGET_ENVIRONMENT = LOCAL_HTTPS_ACCEPTANCE

FRONTEND_ORIGIN  = https://localhost
API_ORIGIN       = https://localhost
SAME_ORIGIN      = YES
SAME_SITE        = YES

REVERSE_PROXY    = Caddy (推荐，自动自签名 TLS)
TLS TERMINATION  = Caddy (localhost 自签名证书)
```

### 拓扑图

```text
Browser (https://localhost)
  │
  ▼
Caddy (:443)
  │ TLS 终止，自签名证书
  │ 反向代理:
  │   /api/*      → Spring Boot (:8080, 127.0.0.1)
  │   /*           → 前端开发服务器 (:5173)
  │
  ├── Spring Boot (127.0.0.1:8080)
  │      └── PostgreSQL (Docker:5432)
  │
  └── 前端 (127.0.0.1:5173)
```

## 3. Caddy 配置原则

```caddy
# TEMPLATE_ONLY_NOT_IMPLEMENTED
# 文件: Caddyfile (仓库根目录)

localhost {
    tls internal  # 自签名证书，仅用于本地验收

    # 清除外部 Forwarded 头（Caddy 默认不转发客户端头）
    header -Forwarded
    header -X-Forwarded-*

    # API 反向代理
    handle /api/* {
        reverse_proxy 127.0.0.1:8080 {
            header_up X-Forwarded-Proto https
            header_up X-Forwarded-Host {host}
        }
    }

    # 前端静态文件或开发服务器
    handle {
        reverse_proxy 127.0.0.1:5173
    }
}
```

### 信任边界

```text
1. Caddy 接收外部请求 → 默认不转发客户端 X-Forwarded-* 头 ✅
2. Caddy 设置可信 X-Forwarded-Proto=https ✅
3. Spring Boot 绑定 127.0.0.1:8080 → 不暴露到公网 ✅
4. forward-headers-strategy=framework → 读取 Caddy 生成的可信头 ✅
```

## 4. 生产 Allowed Origins

```text
LOCAL_HTTPS_ACCEPTANCE:
  CAMPUS_GUINNESS_SECURITY_CORS_ALLOWED_ORIGINS = https://localhost

PRODUCTION:
  未确定。需要真实的 https://<frontend-domain>
  不得硬编码默认值。生产部署时通过环境变量注入。
```

## 5. Session Cookie 预期（HTTPS 响应验证）

```text
COOKIE_NAME     = SESSION
SECURE          = true    (HTTPS 环境下)
HTTP_ONLY       = true    (防 XSS)
SAME_SITE       = Lax     (同源部署)
PATH            = /
DOMAIN          = 未设置   (Host-Only)
PERSISTENT      = false   (Session Cookie)
```

## 6. CSRF Cookie 预期（HTTPS 响应验证）

```text
COOKIE_NAME     = XSRF-TOKEN
SECURE          = true    (HTTPS 环境下，从 Session 配置继承)
HTTP_ONLY       = false   (SPA 需通过 JS 读取)
SAME_SITE       = Lax     (与 Session 一致)
PATH            = /
DOMAIN          = 未设置
```

## 7. Forwarded Header 验证

```text
在 Spring Boot 中验证:
  /api/v1/auth/csrf 响应中 Set-Cookie 的 Secure 属性存在
  → 证明 X-Forwarded-Proto=https 被正确读取

如果 Secure 缺失:
  → Forwarded Header 信任链断裂 → 停止浏览器验收
```

## 8. 应用端口隔离

```bash
# 验证命令 (TEMPLATE_ONLY_NOT_EXECUTED)

# Spring Boot 绑定在 127.0.0.1
curl http://127.0.0.1:8080/actuator/health

# 外部接口不应暴露 8080
curl http://localhost:8080/actuator/health  # 应从 Caddy 代理访问，不直接暴露
```

## 9. 浏览器验收步骤

```text
前置条件:
  [ ] Caddy 已启动且 localhost:443 可访问
  [ ] Spring Boot 已启动 (profile=local, 127.0.0.1:8080)
  [ ] 数据库已启动且 campus_super_admin 可用
  [ ] 浏览器信任 Caddy 自签名证书（首次访问 localhost 时接受）

验证步骤:
  1. 浏览器打开 https://localhost/api/v1/auth/csrf
     → 200, XSRF-TOKEN cookie 存在
  2. DevTools → Application → Cookies → localhost
     → SESSION: Secure ✓, HttpOnly ✓, SameSite=Lax ✓, Path=/ ✓
     → XSRF-TOKEN: Secure ✓, HttpOnly=✗ (SPA可读), SameSite=Lax ✓
  3. 浏览器 DevTools Console 执行:
     document.cookie → 应看不到 SESSION，但能看到 XSRF-TOKEN
  4. POST /api/v1/auth/login → 200
  5. GET /api/v1/auth/me → 200
  6. POST /api/v1/auth/logout → 204
  7. GET /api/v1/auth/me → 401
  8. 确认 logout 后 SESSION cookie 已被清除

停止条件:
  - SESSION cookie Secure=false → 停止
  - SESSION cookie HttpOnly=false → 停止
  - XSRF-TOKEN cookie HttpOnly=true → 停止
  - /me 在未认证时返回非 401 → 停止
  - logout 后 /me 未返回 401 → 停止
```

## 10. 生产环境迁移

```text
当前阶段: LOCAL_HTTPS_ACCEPTANCE
生产拓扑: 未确定（需要真实的 HTTPS 域名和反向代理决策）

迁移时需要:
  [ ] 确定真实前端域名
  [ ] 确定真实 API 域名
  [ ] 选择生产反向代理 (Caddy/Nginx/Traefik/Cloud LB)
  [ ] 获取真实 TLS 证书
  [ ] 设置 CAMPUS_GUINNESS_SECURITY_CORS_ALLOWED_ORIGINS
  [ ] 验证生产 Cookie Secure 属性
  [ ] 确认 HSTS 策略
```

---

> **本计划状态:** `READY_FOR_APPROVAL`
> **未修改代码、配置或数据库。**
