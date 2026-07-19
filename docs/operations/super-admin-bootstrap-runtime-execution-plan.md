# SUPER_ADMIN Bootstrap Runtime Execution Plan

> **状态:** `AWAITING_EXPLICIT_DATABASE_WRITE_APPROVAL`
> **创建日期:** 2026-07-17
> **基于 Git commit:** 1a5c8a9（未提交工作区已验证通过）
> **所有命令均为模板——未经明确授权不得执行**

---

## 1. 目的

为空 `campus_guinness` 数据库创建首个 `SUPER_ADMIN` 账户。Bootstrap 进程是一次性的、非 HTTP 的命令行操作，在 PostgreSQL 事务级锁保护下原子完成。

## 2. 适用范围

| 环境 | 允许？ |
|------|:--:|
| 本地开发（空数据库） | 需批准 |
| 共享开发 | 需批准 |
| 预发布 | 需批准 |
| 生产 | 需批准 + 维护窗口 |

**一次只针对一个环境执行。环境切换需重新批准。**

## 3. 前置条件

### 3.1 构建验证

```text
确认 mvn clean verify 通过，所有 811 测试绿色（当前可信基线：Surefire 738 + Failsafe 73 = 811, 0 failures, 0 errors）
确认 Git 工作区无意外修改
```

### 3.2 代码配置核对（从实际源码提取）

| 配置项 | 实际值 | 来源 |
|--------|--------|------|
| Bootstrap Profile | `bootstrap-admin` | SuperAdminBootstrapRunner.java:30 |
| Enabled 属性 | `campus-guinness.bootstrap-admin.enabled` | SuperAdminBootstrapRunner.java:31 |
| Username 环境变量 | `CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME` | BootstrapAdminProperties.java:16 |
| Password 环境变量 | `CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD` | BootstrapAdminProperties.java:19 |
| 数据库锁 | `pg_advisory_xact_lock(789123456)` | PostgresBootstrapLockAdapter.java:25 |
| 密码算法 | BCrypt strength=12 | PasswordEncoderConfig |
| 密码规则 | 8-72 UTF-8 字节，非空，非全空白，不 trim | PasswordPolicy.java |

### 3.3 退出码（从实际源码提取）

| 退出码 | 含义 | 来源 |
|:--:|------|------|
| 0 | 成功创建 | SuperAdminBootstrapRunner.java:73 |
| 2 | 缺少 username | SuperAdminBootstrapRunner.java:60 |
| 3 | 缺少 password | SuperAdminBootstrapRunner.java:64 |
| 4 | 数据库非空拒绝 | SuperAdminBootstrapRunner.java:76 |
| 5 | 未预期异常 | SuperAdminBootstrapRunner.java:79 |
| 1 | 默认（不应出现） | SuperAdminBootstrapRunner.java:40 |

### 3.4 应用退出行为

```text
SpringApplication.exit(context, this) 在 finally 块中调用
——无论成功或失败，Runner 结束后立即关闭 ApplicationContext，JVM 退出。
Web 服务器不会被启动。
```

## 4. 角色与审批

```text
执行者:   <NAME>
审批者:   <NAME>
批准日期: <YYYY-MM-DD>
维护窗口: <START_TIME> - <END_TIME>
```

## 5. 环境确认（执行前填写）

```text
TARGET_ENVIRONMENT:           <LOCAL_DEVELOPMENT | STAGING | PRODUCTION>
DATABASE_HOST_ALIAS:          <HOST>
DATABASE_NAME:                <DB_NAME>
DATABASE_USER:                <BOOTSTRAP_DB_USER>
GIT_COMMIT:                   1a5c8a9
JAVA_VERSION:                 21
```

## 6. 执行前只读检查

> **TEMPLATE_ONLY_NOT_EXECUTED** — 所有 SQL 仅供人工复核，未经批准不得运行。

### 6.1 确认数据库为空

```sql
-- TEMPLATE_ONLY_NOT_EXECUTED
SELECT COUNT(*) AS total_users FROM users;
-- 预期: 0
```

### 6.2 确认无 SUPER_ADMIN

```sql
-- TEMPLATE_ONLY_NOT_EXECUTED
SELECT COUNT(*) AS super_admin_count FROM users WHERE platform_role = 'SUPER_ADMIN';
-- 预期: 0
```

### 6.3 确认 Flyway 迁移版本

```sql
-- TEMPLATE_ONLY_NOT_EXECUTED
SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```

### 6.4 检查清单

```text
[ ] 目标数据库身份已确认
[ ] users 表总数为 0
[ ] SUPER_ADMIN 数为 0
[ ] Flyway 迁移版本最新
[ ] 无其他维护任务运行
[ ] Bootstrap DB 账号已创建且权限最小
[ ] 审计日志保存位置已确定
[ ] Bootstrap 密码已通过密码管理器生成
[ ] 密码规则: ≥8 Unicode 字符, ≤72 UTF-8 字节, 非空白
```

## 7. 凭据安全

### 禁止

```text
- 密码写入命令行参数
- 密码写入 application.yml / .properties
- 密码写入 Git 文件
- 密码写入日志
- 密码粘贴到任务报告
```

### 正确方式

```text
1. 由执行者在当前 shell 进程中临时设置环境变量
2. 使用关闭命令历史的终端会话（或 PowerShell: $env:VAR = "value"）
3. 执行完成后立即清除环境变量
4. 密码由密码管理器生成和保存
```

## 8. 执行命令

### 8.1 方式 A：运行已构建 JAR

> **TEMPLATE_ONLY_NOT_EXECUTED**
> **REQUIRES_EXPLICIT_DATABASE_WRITE_APPROVAL**

```bash
# 阶段 1: 设置非敏感变量
export SPRING_PROFILES_ACTIVE=bootstrap-admin
export SPRING_MAIN_WEB_APPLICATION_TYPE=none
export CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_ENABLED=true
export SPRING_DATASOURCE_URL="<REAL_DATABASE_URL>"
export SPRING_DATASOURCE_USERNAME="<BOOTSTRAP_DB_USER>"
export SPRING_DATASOURCE_PASSWORD="<BOOTSTRAP_DB_PASSWORD>"

# 阶段 2: 由执行者安全设置密码（在受控终端中逐行执行）
# 提示: 使用密码管理器，不要复制粘贴到共享文档
export CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME="<BOOTSTRAP_USERNAME>"
export CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD="<SECURE_PASSWORD_INPUT>"

# 阶段 3: 人工复核
echo "TARGET: <TARGET_ENVIRONMENT>"
echo "DB:     <DATABASE_HOST_ALIAS>"
echo "USER:   $CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME"
echo "按 Ctrl+C 中止，或按 Enter 继续..."
read

# 阶段 4: 运行 Bootstrap
java -jar target/campus-guinness-0.1.0-SNAPSHOT.jar

# 阶段 5: 立即保存退出码
BOOTSTRAP_EXIT_CODE=$?
echo "Exit code: $BOOTSTRAP_EXIT_CODE"

# 阶段 6: 清除敏感变量
unset CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME
unset CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD
unset SPRING_DATASOURCE_PASSWORD
```

### 8.2 方式 B：Maven Spring Boot 插件

> **TEMPLATE_ONLY_NOT_EXECUTED**
> **REQUIRES_EXPLICIT_DATABASE_WRITE_APPROVAL**

```bash
# 阶段 1-3: 同上
# 阶段 4:
./mvnw.cmd spring-boot:run \
  -Dspring-boot.run.profiles=bootstrap-admin \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"
```

## 9. 执行后验证（成功：退出码 0）

### 9.1 只读验证

```sql
-- TEMPLATE_ONLY_NOT_EXECUTED
SELECT COUNT(*) AS total_users FROM users;
-- 预期: 1

SELECT COUNT(*) AS super_admin_count FROM users WHERE platform_role = 'SUPER_ADMIN';
-- 预期: 1
```

### 9.2 密码哈希验证（不输出哈希值）

```sql
-- TEMPLATE_ONLY_NOT_EXECUTED
-- 确认哈希非空且格式正确，但不得输出完整值
SELECT
    CASE WHEN password_hash IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS hash_present,
    CASE WHEN password_hash LIKE '$2a$%' THEN 'BCRYPT' ELSE 'UNKNOWN' END AS hash_format,
    CASE WHEN login_failures = 0 THEN 'OK' ELSE 'WARN' END AS login_failures,
    CASE WHEN locked_until IS NULL THEN 'OK' ELSE 'WARN' END AS locked_until
FROM users WHERE platform_role = 'SUPER_ADMIN';
```

### 9.3 验证清单

```text
[ ] 进程退出码 = 0
[ ] users 总数 = 1
[ ] SUPER_ADMIN 数 = 1
[ ] 目标 username 存在
[ ] account_status = NORMAL
[ ] platform_role = SUPER_ADMIN
[ ] password_hash 非空且为 BCrypt 格式
[ ] login_failures = 0
[ ] locked_until = NULL
[ ] 无额外用户记录
```

## 10. 失败分支处理

### 10.1 退出码 2 或 3（缺少配置）

```text
[ ] 确认环境变量名称与代码匹配
[ ] 确认环境变量在正确的 shell 进程中设置
[ ] 修正后重新获得执行批准
[ ] 不得绕过配置校验
```

### 10.2 退出码 4（数据库非空拒绝）

```text
[ ] 立即停止——不得删除现有用户
[ ] 调查数据库现有数据来源
[ ] 不得强制继续或手工插入 SUPER_ADMIN
[ ] 生成调查任务: TASK-AUTH-SUPER-ADMIN-RECOVERY-001
```

### 10.3 退出码 5（异常）

```text
[ ] 只读确认 users 表仍为空
[ ] 保存完整应用日志
[ ] 确认没有部分写入
[ ] 确认事务已回滚
[ ] 根据真实错误生成 Revision 任务
[ ] 不得立即重复执行
```

### 10.4 密码非法

```text
[ ] 数据库不得产生新记录
[ ] 重新生成符合 PasswordPolicy 的密码:
    - ≥8 Unicode 字符
    - ≤72 UTF-8 字节
    - 非空、非全空白
[ ] 不得放宽 PasswordPolicy
```

## 11. 审计证据

### 执行后必须保留

```text
[ ] 执行日期和时区
[ ] 目标环境名称
[ ] Git commit: 1a5c8a9
[ ] 构建产物或本地运行确认
[ ] Java 版本
[ ] 执行命令的脱敏版本
[ ] 开始时间
[ ] 结束时间
[ ] 进程退出码
[ ] 执行前 users 总数: 0
[ ] 执行前 SUPER_ADMIN 数: 0
[ ] 执行后 users 总数: 1
[ ] 执行后 SUPER_ADMIN 数: 1
[ ] 创建的 userId（非敏感）
[ ] 创建的 username
[ ] 应用日志脱敏副本
[ ] 只读验证结果
[ ] 执行者
[ ] 审批者
```

### 禁止保留

```text
- 初始明文密码
- 完整 password_hash
- 完整数据库连接串
- 数据库密码
- 访问令牌 / Cookie / Session ID
```

## 12. 敏感信息清理

```text
[ ] 清除 shell 环境变量: unset CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_USERNAME
[ ] 清除 shell 环境变量: unset CAMPUS_GUINNESS_BOOTSTRAP_ADMIN_PASSWORD
[ ] 清除 shell 环境变量: unset SPRING_DATASOURCE_PASSWORD
[ ] 关闭终端会话（防止历史记录泄露）
[ ] 确认密码未被 shell 历史文件记录
[ ] Bootstrap DB 账号如为临时创建，执行后回收或降权
```

## 13. 回滚与事故处理

### Bootstrap 成功后

```text
不得通过自动脚本或 SQL DELETE 删除首个管理员作为常规回滚。
首个 SUPER_ADMIN 的创建是安全状态变更，
修改或删除需要单独、明确、可审计的管理任务。
```

### 错误 username

```text
不得运行 DELETE 修复
不得重新运行 Bootstrap 创建第二个管理员
停止并生成 TASK-AUTH-SUPER-ADMIN-USERNAME-CORRECTION
```

### 记录数异常

```text
立即停止
禁止启动登录和其他业务功能
保存日志和数据库只读快照
生成 TASK-AUTH-SUPER-ADMIN-INCIDENT-INVESTIGATION
```

## 14. 禁止事项

```text
- 不得通过 HTTP 端点执行 Bootstrap
- 不得将 Bootstrap 端点标记为 permitAll
- 不得将密码写入 Migration / application.yml / 代码
- 不得使用默认或硬编码密码
- 不得在数据库非空时强制继续
- 不得手工 INSERT SUPER_ADMIN
- 不得重复执行以重置密码
- 不得在两个独立的环境中执行同一个命令模板而不重新确认
- 不得在报告或日志中记录明文密码
```

## 15. 执行确认单

```text
我确认:
[ ] 已阅读并理解本运行手册
[ ] 已确认目标环境和数据库
[ ] 已执行所有只读预检
[ ] 密码已通过安全渠道生成且符合密码规则
[ ] 已知悉退出码含义和失败分支处理
[ ] 已知悉审计证据要求
[ ] 已获得执行批准

执行者签名: ________________    日期: ________
审批者签名: ________________    日期: ________
```

## 16. 执行后报告模板

```text
=== SUPER_ADMIN Bootstrap 执行报告 ===

执行日期:   <YYYY-MM-DD HH:MM TZ>
目标环境:   <ENVIRONMENT>
Git commit: 1a5c8a9
执行者:     <NAME>
审批者:     <NAME>

执行前:
  users 总数:       0
  SUPER_ADMIN 数:   0

执行结果:
  退出码:           <0|2|3|4|5>
  状态:             <SUCCESS|REFUSED|CONFIG_ERROR|ERROR>

执行后（仅成功时填写）:
  users 总数:       1
  SUPER_ADMIN 数:   1
  userId:           <UUID>
  username:         <USERNAME>
  status:           NORMAL
  platform_role:    SUPER_ADMIN
  password_hash:    已设置 (BCrypt)
  login_failures:   0
  locked_until:     null
  version:          <VERSION>

异常和备注:
  <如有>

审计附件:
  [ ] 脱敏应用日志
  [ ] 只读验证结果
  [ ] 原始执行环境信息

=========================================
```

---

> **本手册状态:** `READY_FOR_APPROVAL`
> **未执行任何真实数据库操作。**
> **所有 SQL 和命令均为模板，未执行。**
