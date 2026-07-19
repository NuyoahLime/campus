# Real Local SUPER_ADMIN Login Runtime Verification Plan

> **状态:** `AWAITING_EXPLICIT_RUNTIME_APPROVAL`
> **日期:** 2026-07-18
> **基于 Git commit:** 1a5c8a9
> **所有命令均为模板——未经明确授权不得执行**

---

## 1. 目的

在本地 Docker PostgreSQL 上使用真实 `campus_super_admin` 管理员验证完整 HTTP 认证闭环（CSRF → 登录 → Session 恢复 → /me → logout → 清理）。

## 2. Spring Session JDBC 现状（从源码核对）

| 项目 | 值 | 来源 |
|------|-----|------|
| 依赖 | `spring-session-jdbc` | pom.xml:52 |
| 存储类型 | `jdbc` | application.yml:33 |
| Schema 初始化 | `never` | application.yml:35 |
| Schema 来源 | Flyway V015 | V015__create_spring_session_tables.sql |
| Session 表 | `spring_session` (7 列) | V015:4-14 |
| Attributes 表 | `spring_session_attributes` (3 列) | V015:20-28 |
| Session Cookie 名称 | `SESSION` | Spring Session JDBC 默认 |
| Session 超时 | `30m` idle | application.yml:36 |
| Cookie HttpOnly | `true` | application.yml:42 |
| Cookie Secure | `false` (默认) | application.yml:43 |
| Cookie SameSite | `lax` | application.yml:44 |
| Cookie Path | `/` | application.yml:45 |
| Tracking Modes | `cookie` | application.yml:46 |
| Persistent | `false` | application.yml:47 |
| CSRF Cookie | `XSRF-TOKEN`, HttpOnly=false | SecurityConfig:35 |
| CSRF Header | `X-XSRF-TOKEN` | CookieCsrfTokenRepository 默认 |
| Logout 删除 Cookie | `SESSION`, `XSRF-TOKEN` | SecurityConfig:55 |
| Logout 失效 Session | `invalidateHttpSession(true)` | SecurityConfig:53 |
| User 表写入 | **无** (login 仅读 users 表) | AuthController:38 |

## 3. 预期数据库写入

```text
EXPECTED_SESSION_INSERTS    = 1 (spring_session 新增 1 行)
EXPECTED_SESSION_UPDATES    = 1-2 (spring_session_attributes 新增; last_access_time 更新)
EXPECTED_SESSION_DELETES    = 1 (logout 删除 spring_session 行，spring_session_attributes 级联删除)
EXPECTED_USER_TABLE_WRITES  = 0 (AuthController 仅调用 AuthenticationManager.authenticate)
EXPECTED_AUDIT_TABLE_WRITES = 0 (无审计写入)
EXPECTED_OTHER_WRITES       = 0

禁止的写入：
  - users 表任何列的 INSERT/UPDATE/DELETE
  - password_hash 修改
  - account_status 修改
  - platform_role 修改
  - 任何业务表的写入
```

## 4. 授权门禁模板

> 以下字段在执行前必须填写实际值。占位符状态阻塞执行。

```text
APPROVE_TASK =
TASK-AUTH-RUNTIME-LOGIN-VERIFICATION-EXECUTION-01

ALLOW_REAL_DATABASE_CONNECTION = YES
ALLOW_READ_ONLY_DATABASE_PREFLIGHT = YES
ALLOW_AUTHENTICATION_SESSION_WRITES = YES
ALLOW_SESSION_CLEANUP_WRITES = YES
ALLOW_USER_TABLE_WRITES = NO
ALLOW_DATABASE_SCHEMA_CHANGES = NO
ALLOW_PRODUCTION_CODE_CHANGES = NO
ALLOW_CONFIGURATION_CHANGES = NO
ALLOW_GIT_COMMIT = NO

TARGET_ENVIRONMENT = LOCAL_DEVELOPMENT
TARGET_DATABASE_ALIAS = campus-guinness-local-docker-postgresql
DATABASE_CONNECTION_METHOD = Docker Compose PostgreSQL (localhost:5432, user=postgres)
APPLICATION_START_METHOD = mvn spring-boot:run -Dspring-boot.run.profiles=local
BOOTSTRAP_USERNAME = campus_super_admin
SECRET_INJECTION_METHOD = 由执行者从密码管理器读取，通过隐藏输入注入临时环境变量
EXECUTION_OPERATOR = <填写>
APPROVER = <填写>
MAINTENANCE_WINDOW = <填写>
```

## 5. 执行前只读预检

> **TEMPLATE_ONLY_NOT_EXECUTED**

```sql
-- 1. 确认环境
SELECT current_database(), current_user, inet_server_addr();

-- 2. 确认目标用户
SELECT username, account_status, platform_role,
       CASE WHEN password_hash IS NOT NULL AND password_hash LIKE '$2a$%' THEN 'BCRYPT_OK' END AS hash_format,
       login_failures, locked_until
FROM users WHERE username = 'campus_super_admin';
-- 预期: 恰好 1 行, status=NORMAL, platform_role=SUPER_ADMIN

-- 3. 确认 Session 表结构
SELECT COUNT(*) AS session_rows_before FROM spring_session;
SELECT COUNT(*) AS session_attrs_before FROM spring_session_attributes;

-- 4. 确认 Flyway 版本
SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

## 6. Phase 0: 授权核验

确认所有门禁字段已填写实际值。任一字段缺失 → 停止。

## 7. Phase 1: 工作区和构建身份

```bash
git branch --show-current
git rev-parse HEAD
git status --short
java -version
```

## 8. Phase 2: 只读数据库预检

执行第 5 节 SQL。确认全部检查通过。

## 9. Phase 3: 启动应用

```bash
# 设置数据库连接（如需要）
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/campus_guinness"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="<DB_PASSWORD>"

# 启动（local profile）
./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

健康检查：
```bash
curl -s http://localhost:8080/actuator/health
```

## 10. Phase 4: 获取 CSRF

```bash
# 使用 Cookie jar 保存 Session/Cookie
COOKIE_JAR=$(mktemp)

curl -s -c "$COOKIE_JAR" http://localhost:8080/api/v1/auth/csrf | python3 -m json.tool
# 提取 token 值（不输出到日志）
CSRF_TOKEN=$(curl -s -c "$COOKIE_JAR" http://localhost:8080/api/v1/auth/csrf | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 验证: token 非空, headerName="X-XSRF-TOKEN"
```

## 11. Phase 5: 真实管理员登录

> **密码由执行者通过安全方式注入，不输出到终端或日志**

```bash
# 安全读取密码（隐藏输入）
read -s ADMIN_PASSWORD

# 登录
curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -d "{\"username\":\"campus_super_admin\",\"password\":\"$ADMIN_PASSWORD\"}" \
  http://localhost:8080/api/v1/auth/login | python3 -m json.tool

# 验证: HTTP 200, username="campus_super_admin", status="NORMAL", platformRole="SUPER_ADMIN"
# 验证: SESSION cookie 存在于 Cookie Jar
# 验证: 响应不含 passwordHash
```

## 12. Phase 6: 当前用户验证

```bash
curl -s -b "$COOKIE_JAR" http://localhost:8080/api/v1/auth/me | python3 -m json.tool

# 验证: HTTP 200, userId="7b49cddd-6fc3-4166-9496-4eb86b34f969"
```

## 13. Phase 7: 数据库写入观察

```sql
-- 只读检查
SELECT COUNT(*) AS session_rows_after FROM spring_session;
-- 预期: +1

SELECT COUNT(*) AS session_attrs_after FROM spring_session_attributes;
-- 预期: +1-2

-- 确认 users 表无变化
SELECT username, account_status, platform_role, login_failures, locked_until
FROM users WHERE username = 'campus_super_admin';
-- 预期: 与预检完全相同
```

## 14. Phase 8: 退出

```bash
# 获取新 CSRF（因为之前的 token 可能过期）
CSRF_TOKEN2=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" http://localhost:8080/api/v1/auth/csrf | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  -H "X-XSRF-TOKEN: $CSRF_TOKEN2" \
  -X POST http://localhost:8080/api/v1/auth/logout -w "%{http_code}"
# 验证: 204
```

## 15. Phase 9: 退出后验证

```bash
curl -s -b "$COOKIE_JAR" http://localhost:8080/api/v1/auth/me -w "%{http_code}"
# 验证: 401

# 确认 Session 已清除
```

```sql
SELECT COUNT(*) AS session_rows_after_logout FROM spring_session;
-- 预期: 回到登录前数量
```

## 16. Phase 10: 敏感信息清理

```bash
unset ADMIN_PASSWORD
unset CSRF_TOKEN
unset CSRF_TOKEN2
rm -f "$COOKIE_JAR"
unset COOKIE_JAR
# 清除剪贴板
# 关闭终端
# 停止应用
```

## 17. 失败分支

| 条件 | 行动 |
|------|------|
| 预检失败 | 停止，不启动应用 |
| 启动失败 | 不修改配置，保存日志 |
| 登录 401 | 不重试，不判断原因，清 CSRF/Cookie，停止 |
| 登录 200 但 /me 401 | 检查 Session 表，logout 清理，停止 |
| users 表变化 | **立即停止**，标记事故，保存前后快照 |
| logout 失败 | 停止，不清 Session 表，生成清理任务 |
| Schema 变化 | **立即停止**，标记事故 |

## 18. Secure=false 边界声明

```text
本次验证仅限 LOCAL_DEVELOPMENT，使用 HTTP（非 HTTPS）。
Secure=false 是本地验证的必要条件，不代表生产配置。
生产部署需要独立任务 TASK-AUTH-DEPLOYMENT-COOKIE-HARDENING-01
处理 HTTPS、Secure=true、反向代理头和 SameSite 部署拓扑。
```

## 19. 执行确认单

```text
我确认:
[ ] 已阅读并理解本计划
[ ] 目标环境为 LOCAL_DEVELOPMENT
[ ] 已知悉数据库写入范围（仅 spring_session 表）
[ ] 已知悉 Login 不写入 users 表
[ ] 密码将通过安全方式注入
[ ] 已知悉所有失败分支
[ ] 已知悉敏感信息清理步骤
[ ] 已获得执行授权

执行者: ________    日期: ________
审批者: ________    日期: ________
```

---

> **本计划状态:** `READY_FOR_APPROVAL`
> **未连接真实数据库。未执行登录。未创建 Session。**
