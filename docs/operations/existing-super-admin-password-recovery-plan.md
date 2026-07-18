# Existing SUPER_ADMIN Password Recovery Plan

> **状态:** `AWAITING_IMPLEMENTATION_APPROVAL`
> **日期:** 2026-07-18
> **Git commit:** 1a5c8a9
> **所有命令均为模板——未经明确授权不得执行**

---

## 1. 问题陈述

```text
USER_ID         = 7b49cddd-6fc3-4166-9496-4eb86b34f969
USERNAME        = campus_super_admin
STATUS          = NORMAL
PLATFORM_ROLE   = SUPER_ADMIN

原始密码:       openssl rand 生成 → unset 清除 → 不可恢复
Password Hash:  已持久化为 BCrypt，无法逆向
```

Bootstrap (`TASK-AUTH-BOOTSTRAP-SUPER-ADMIN-RUNTIME-EXECUTION-01`) 是创建首个管理员的机制，不能用于现有账户密码重置。

## 2. 源码审计证据

| 组件 | 行为 | 密码更新兼容性 |
|------|------|:--:|
| `UserPersistenceMapper.updateEntity()` | 更新 username/status/platformRole，**保留** passwordHash | ❌ 不可用 |
| `UserPersistenceMapper.toNewEntity()` | 创建新实体，设置 passwordHash 和认证默认值 | ❌ 仅用于 INSERT |
| `UserRepository.save(User)` | 通过 `UserRepositoryAdapter` 调用 `updateEntity` | ❌ 不更新 passwordHash |
| `UserEntity.setPasswordHash(String)` | package-private setter，存在于实体层 | ✅ 可被同包 Mapper 调用 |
| `PasswordEncoder` bean | BCryptPasswordEncoder(strength=12)，已配置 | ✅ 可注入 |
| `PasswordPolicy` | 静态 validate() 方法，8-72 UTF-8 字节 | ✅ 可直接调用 |
| `UserBootstrapStateQuery` | jpa.count() | ⚠️ 可参考模式但不复用语义 |
| `BootstrapLock` / `PostgresBootstrapLockAdapter` | pg_advisory_xact_lock | ✅ 可复用锁模式 |
| `spring_session.principal_name` | VARCHAR(300)，有索引 `spring_session_ix3` | ✅ 可按 principal 查找 Session |
| `UserJpaRepository.findByUsername()` | 存在但只在 JPA 层 | ⚠️ 需暴露到端口 |

## 3. 设计方案：专用密码重置 Runner

### 3.1 架构

```text
PasswordRecoveryRunner (ApplicationRunner, @Profile, @ConditionalOnProperty)
  → PasswordRecoveryService (@Transactional)
    → PasswordPolicy.validate(newPassword)
    → UserRepository.findById(targetUserId)
    → 强校验 (userId, status, platformRole)
    → PasswordEncoder.encode(newPassword)
    → 专用 Mapper 方法更新 passwordHash
    → 清除目标用户 Session (spring_session WHERE principal_name = ?)
    → 返回结果
```

### 3.2 需要新增的生产文件

| 文件 | 说明 |
|------|------|
| `PasswordRecoveryService.java` | `@Transactional` 服务：强校验 → 编码 → 更新 → Session 清除 |
| `PasswordRecoveryRunner.java` | `@Profile("password-recovery")` + `@ConditionalOnProperty`，非 Web Runner |
| `PasswordRecoveryProperties.java` | `@ConfigurationProperties("campus-guinness.security.admin-password-recovery")` |
| `UserPersistenceMapper` 新增方法 | `updatePasswordHash(UserEntity, String newHash)` — 仅更新 passwordHash + updatedAt |
| `UserRepository` 或专用端口 | 暴露 `findByUsername` 用于查询目标用户 |

### 3.3 需要修改的现有文件

| 文件 | 修改 |
|------|------|
| `Application.java` | + `@EnableConfigurationProperties(PasswordRecoveryProperties.class)` |
| `UserPersistenceMapper.java` | + `updatePasswordHash(entity, hash)` 方法 |

### 3.4 专用 Mapper 方法设计

```java
// UserPersistenceMapper — new method
static void updatePasswordHash(UserEntity entity, String newPasswordHash) {
    entity.setPasswordHash(newPasswordHash);
    entity.setUpdatedAt(Instant.now());
    // Only passwordHash changes; all other fields preserved
}
```

### 3.5 目标用户强校验

```java
// PasswordRecoveryService — validate target
if (!targetUserId.equals(user.id().value()))   → EXIT 32
if (!"NORMAL".equals(user.status().name()))    → EXIT 33
if (!"SUPER_ADMIN".equals(user.platformRole())) → EXIT 34
```

### 3.6 Session 失效

```java
// 通过 JdbcTemplate 按 principal_name 删除
// spring_session.principal_name = campus_super_admin
// 利用索引 spring_session_ix3

// 注意: principal_name 的值由 Spring Session 的
// SecurityContext 中 Authentication.getName() 决定。
// CampusGuinnessUserDetails.getUsername() 返回 loginName。
// 因此 principal_name = "campus_super_admin"

jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", username);
// spring_session_attributes 通过 ON DELETE CASCADE 自动删除
```

## 4. 配置设计

```yaml
campus-guinness:
  security:
    admin-password-recovery:
      enabled: false              # 必须显式设为 true
      target-user-id:             # 必须精确匹配
      target-username:            # 双重校验
      expected-status: NORMAL     # 默认值
      expected-platform-role: SUPER_ADMIN
      invalidate-sessions: true   # 默认 true
```

环境变量映射：
```text
CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_ENABLED=true
CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_TARGET_USER_ID=7b49cddd-6fc3-4166-9496-4eb86b34f969
CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_TARGET_USERNAME=campus_super_admin
CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_NEW_PASSWORD=<hidden>
```

## 5. 退出码

```text
0   = SUCCESS
10  = FEATURE_DISABLED
20  = INVALID_CONFIGURATION (缺少必填字段)
30  = TARGET_USER_NOT_FOUND
31  = TARGET_USER_NOT_UNIQUE
32  = TARGET_ID_MISMATCH
33  = TARGET_STATUS_MISMATCH
34  = TARGET_ROLE_MISMATCH
40  = PASSWORD_POLICY_REJECTED
50  = LOCK_NOT_ACQUIRED
60  = PASSWORD_UPDATE_FAILED
61  = UNEXPECTED_UPDATE_COUNT
70  = SESSION_INVALIDATION_FAILED
80  = DATABASE_ERROR
90  = UNEXPECTED_ERROR
```

与现有 Bootstrap 退出码 (2,3,4,5) 不冲突。

## 6. 失败原子性策略

采用策略 A：整体回滚。

```text
Session 清除失败 → 整个事务回滚 → password_hash 不更新
```

理由：密码恢复中如果旧 Session 未被清除但密码已变更，旧 Session 持有者仍可通过旧的认证上下文访问系统。这违反了密码恢复的安全意图。

## 7. 执行命令模板

> **TEMPLATE_ONLY_NOT_EXECUTED**

```bash
# Profile: password-recovery
# Web type: none

export SPRING_PROFILES_ACTIVE=local,password-recovery
export SPRING_MAIN_WEB_APPLICATION_TYPE=none

export CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_ENABLED=true
export CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_TARGET_USER_ID=7b49cddd-6fc3-4166-9496-4eb86b34f969
export CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_TARGET_USERNAME=campus_super_admin

# 密码通过隐藏输入设置
read -s NEW_PASSWORD
export CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_NEW_PASSWORD="$NEW_PASSWORD"

# 执行
./mvnw.cmd spring-boot:run \
  -Dspring-boot.run.profiles=local,password-recovery \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"

# 保存退出码
RECOVERY_EXIT_CODE=$?

# 如果成功 (exit 0): 立即将新密码保存到密码管理器
# 然后清除变量
unset NEW_PASSWORD
unset CAMPUS_GUINNESS_SECURITY_ADMIN_PASSWORD_RECOVERY_NEW_PASSWORD
```

## 8. 执行后验证链

```text
[ ] 确认退出码 = 0
[ ] users 表目标记录: password_hash 已变化 (不输出值)
[ ] users 表目标记录: username/status/platformRole/loginFailures/lockedUntil 未变化
[ ] spring_session: 目标用户的 Session 已删除
[ ] 其他用户的 Session 未被影响
[ ] 其他表无变化
[ ] 临时密码变量已清除
[ ] 新密码已保存到密码管理器

然后执行:
[ ] TASK-AUTH-RUNTIME-LOGIN-VERIFICATION-EXECUTION-01
    (使用新密码进行真实 login → me → logout 验证)
```

## 9. 授权模板

```text
APPROVE_TASK =
TASK-AUTH-SUPER-ADMIN-CREDENTIAL-RECOVERY-EXECUTION-01

ALLOW_REAL_DATABASE_CONNECTION = YES
ALLOW_READ_ONLY_DATABASE_PREFLIGHT = YES
ALLOW_SINGLE_USER_PASSWORD_HASH_WRITE = YES
ALLOW_TARGET_USER_SESSION_INVALIDATION = YES
ALLOW_OTHER_USER_TABLE_WRITES = NO
ALLOW_OTHER_DATABASE_WRITES = NO
ALLOW_DATABASE_SCHEMA_CHANGES = NO
ALLOW_PRODUCTION_CODE_CHANGES = NO
ALLOW_GIT_COMMIT = NO

TARGET_ENVIRONMENT = LOCAL_DEVELOPMENT
TARGET_DATABASE_ALIAS = campus-guinness-local-docker-postgresql
TARGET_USER_ID = 7b49cddd-6fc3-4166-9496-4eb86b34f969
TARGET_USERNAME = campus_super_admin
EXPECTED_STATUS = NORMAL
EXPECTED_PLATFORM_ROLE = SUPER_ADMIN

SECRET_INJECTION_METHOD = <实际安全方式>
PASSWORD_MANAGER_DESTINATION = <新密码保存位置>
EXECUTION_OPERATOR = <真实操作人>
APPROVER = <真实审批人>
MAINTENANCE_WINDOW = <真实时间和时区>
```

## 10. 禁止事项

```text
- 不得重新执行 Bootstrap
- 不得删除现有 campus_super_admin
- 不得创建第二个 SUPER_ADMIN
- 不得手工执行 UPDATE users SET password_hash = ...
- 不得关闭 Bootstrap 空库保护
- 不得降低 PasswordPolicy
- 不得通过 HTTP 接口重置密码
- 不得将密码写入命令参数或文件
```

---

> **本计划状态:** `READY_FOR_APPROVAL`
> **下一步:** `TASK-AUTH-SUPER-ADMIN-CREDENTIAL-RECOVERY-IMPLEMENTATION-01`
> **未连接数据库。未执行密码重置。**
