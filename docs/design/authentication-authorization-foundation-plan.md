# Authentication & Authorization Foundation Plan

> **Stage 3 update (2026-08-06):** `SchoolMembership` is implemented as a `User`
> aggregate child entity with stable id, `SchoolRole`, `MembershipStatus`,
> `startedAt`, `endedAt`, and version reconstitution. Writes go through
> `UserRepository`, which now restores and diffs membership rows and provides
> `findByIdForUpdate`. `AuthenticationMembershipQuery` can read ACTIVE
> `STUDENT` and `SCHOOL_ADMIN` memberships for authentication preparation.
> Spring Security authority mapping is still intentionally disabled, no
> controller/API was added, `TEACHER` remains historical reconstitution-only,
> and no Flyway migration was added.

**Task:** `TASK-AUTH-001`
**Mode:** `READ_ONLY_ARCHITECTURE_AUDIT`
**Date:** 2026-07-16
**Status:** PARTIALLY_IMPLEMENTED — CORE AUTHENTICATION COMPLETE; AUTHORIZATION & EXPOSURE REMAINING
**Spring Boot:** 3.5.7 | **Java:** 21 | **DB:** PostgreSQL 18

> **实现状态更新 (2026-07-19):** 截至提交 `1e5f2cf`，核心认证身份基础（`fb62263`）、凭据设置与密码策略（`a478cdf`）、Session 登录与 Web 安全（`7397e68`）、安全超级管理员初始化（`e285a23`）、管理员密码恢复接线（`a0550e0`）已实现。Score Appeal Path A 服务已提交（`1e5f2cf`）但尚无 HTTP 入口。剩余授权、HTTP 暴露和细粒度资源控制仍属未来设计。

---

## 1. 执行摘要 (Executive Summary)

本文件基于对 `campus-guinness` 仓库的完整只读审计，设计认证与授权基础方案。**未修改任何生产代码、测试代码、数据库迁移或依赖。**

### 关键发现

1. **认证子系统几乎完全缺失**：`SecurityConfig` 使用 `anyRequest().denyAll()` 阻止所有 API 请求。无 `AuthenticationProvider`、`UserDetailsService` 或 `PasswordEncoder`。
   > ✅ **已实现 (fb62263, 7397e68):** AuthenticationProvider、UserDetailsService、BCryptPasswordEncoder (strength 12)、Session 登录、CSRF/CORS 均已就位。
2. **Spring Session JDBC 已配置但未使用**：V015 迁移创建了 `spring_session` 和 `spring_session_attributes` 表，`application.yml` 设置 `spring.session.store-type=jdbc`，但表单登录已禁用。
3. **密码哈希列存在但未通过映射器填充**：`password_hash varchar(255) NOT NULL` 列存在，但 `UserPersistenceMapper.toEntity()` 从不设置它——会导致 DB 约束违反。
4. **无 JWT 库**：POM 中无 `jjwt`、`nimbus-jose-jwt` 或任何 JWT 依赖。无 OAuth2 依赖。无 Redis 依赖。
5. **角色模型是自由格式字符串**：`platformRole`（仅限于 `SUPER_ADMIN` 或 NULL）和 `roleInSchool`（`STUDENT`、`TEACHER`、`SCHOOL_ADMIN`）是带数据库 CHECK 约束的字符串，而非枚举。
6. **审计基础设施存在但最小**：`audit_records` 表（V013）具有 `actor_id`、`action`、`target_type`、`target_id`、`detail`（JSONB）和 `ip_address`。实体存在但无仓库或服务。

### 推荐方案

**方案 A：服务端 Session + 安全 HttpOnly Cookie（已选择）**

理由：
- Spring Session JDBC 已经配置完成并随时可用
- 零额外依赖——无需 JWT 库、无需 Redis
- 安全 Cookie（HttpOnly、Secure、SameSite）为第一方浏览器 SPA 提供最强保护
- 会话立即可撤销（`DELETE FROM spring_session`），适用于禁用/强制下线/密码修改
- Cookie 在每次请求时自动发送——无令牌存储或手动附加问题
- 非 SPA 客户端可以改用 Bearer 令牌，作为后续增强

---

## 2. 现状审计 (Current State Audit)

### 2.1 User 聚合

| 关注点 | 审计结果 |
|--------|---------|
| 聚合根 | `User`（最终聚合，13/13） |
| 状态机 | 4 状态：`PENDING_ACTIVATION` → `NORMAL` ⇄ `LOCKED`；均 → `DISABLED`；`DISABLED` → `NORMAL` |
| 可登录状态 | 仅 `NORMAL` |
| 禁止登录状态 | `PENDING_ACTIVATION`、`LOCKED`、`DISABLED` |
| `platformRole` | `String`，数据库 CHECK 约束：仅 `'SUPER_ADMIN'` 或 NULL |
| `SchoolMembership` | 子实体；`roleInSchool`（`String`）：`STUDENT`、`TEACHER`、`SCHOOL_ADMIN`；`schoolId`（`UUID`） |
| 密码/凭据 | **从领域模型中排除**（User.java 第 25 行的 Javadoc） |
| 领域事件 | `UserActivated`、`UserDisabled` |
| 框架依赖 | **零**——纯 POJO |

### 2.2 持久化层

| 关注点 | 审计结果 |
|--------|---------|
| `UserEntity` | 映射到 `users` 表；包含领域外字段：`passwordHash`、`loginFailures`、`lockedUntil` |
| `UserPersistenceMapper` | **不设置** `passwordHash`、`loginFailures`、`lockedUntil`——在 NOT NULL 列上存在 DB 约束违反风险 |
| `UserJpaRepository` | 具有 `findByUsername(String)`——认证所需 |
| `UserRepository` 端口 | **缺少** `findByUsername`——应用层无法按用户名查找 |
| `SchoolMembershipEntity` | 映射到 `school_memberships`；有 getter，包私有 setter |
| **成员资格加载延迟** | 映射器注释："SchoolMembership restoration deferred" |

### 2.3 数据库模式

| 表 | 关键列 | 约束 |
|---|---------|----------|
| `users` | `password_hash varchar(255) NOT NULL`、`platform_role varchar(32)`、`login_failures int DEFAULT 0`、`locked_until timestamptz` | CHECK：`platform_role IS NULL OR platform_role = 'SUPER_ADMIN'`、`account_status IN (...)` |
| `school_memberships` | `role_in_school varchar(32)`、`user_id`、`school_id` | CHECK：`role_in_school IN ('STUDENT','TEACHER','SCHOOL_ADMIN')`；部分唯一索引 `(user_id, school_id) WHERE status = 'ACTIVE'` |
| `spring_session` | `primary_id`、`session_id`、`principal_name`、`expiry_time` | Spring Session JDBC 的标准模式 |
| `audit_records` | `actor_id`、`action`、`target_type`、`target_id`、`detail jsonb`、`ip_address` | FK 到 `users` 和 `schools` |

### 2.4 Spring Security

> **实现状态:** 截至 `7397e68`，以下"零"项已实现。保留原始审计记录以展示设计基准。

| 关注点 | 审计结果 | 当前状态 |
|--------|---------|---------|
| `SecurityFilterChain` | 仅 1 个 Bean，位于 `SecurityConfig` | 已增强: CSRF、CORS、Session 登录 |
| `.authorizeHttpRequests` | `/actuator/health`、`/actuator/info` → `permitAll()`；**所有其他** → `denyAll()` | `/api/v1/auth/**` 已开放 |
| CSRF | **已禁用**（`csrf.disable()`） | **已重新启用** (CookieCsrfTokenRepository) |
| 会话管理 | `SessionCreationPolicy.IF_REQUIRED` | Spring Session JDBC + Session 登录 |
| HTTP Basic | 已禁用 | 已禁用 |
| 表单登录 | 已禁用 | 已禁用 (使用 JSON 登录) |
| `AuthenticationProvider` | **零**——未配置 | ✅ 已实现 (`AuthenticationProviderConfig`) |
| `UserDetailsService` | **零**——未实现 | ✅ 已实现 (`CampusGuinnessUserDetailsService`) |
| `PasswordEncoder` | **零**——无 Bean | ✅ 已实现 (BCrypt, strength=12) |
| `AuthenticationEntryPoint` | **未自定义** | ✅ 已实现 (JSON 401) |
| `AccessDeniedHandler` | **未自定义** | ✅ 已实现 (JSON 403) |
| 方法安全 | **无** `@PreAuthorize` | 待实现 |

### 2.5 现有 HTTP 端点

> **实现状态:** 截至 `7397e68`，认证端点（`/api/v1/auth/login`、`/api/v1/auth/logout`、`/api/v1/auth/me`、CSRF Token）已开放。

13 个控制器，共 40+ 个端点。认证端点已开放；管理端点仍受保护。

**携带 `TEMPORARY_EXPLICIT_ACTOR_ID` 的端点**（actorId 来自请求体，必须迁移到 `SecurityContext`）：

| 控制器 | 请求 DTO | actorId 字段 |
|--------|-----------|-------------|
| `SchoolController` | `ActivateSchoolRequest` | `UUID actorId` |
| `SchoolRegistrationController` | `ApproveSchoolRegistrationRequest` | `reviewerId` |
| `SchoolRegistrationController` | `RejectSchoolRegistrationRequest` | `reviewerId` |
| `ActivityController` | `CreateActivityRequest` | `createdBy` |
| `ActivityApplicationController` | `ApproveActivityApplicationRequest` | `reviewerId` |
| `ActivityApplicationController` | `RejectActivityApplicationRequest` | `reviewerId` |
| `ScoreAttemptController` | `SubmitScoreRequest` | `enteredBy` |
| `ScoreAppealController` | `BeginProcessingRequest` | `handlerId` |
| （至少 8 个 DTO 有临时 actorId 字段） |

### 2.6 依赖

| 依赖 | 版本 | 状态 |
|------|------|------|
| `spring-boot-starter-security` | 3.5.7 | 已包含，未充分利用 |
| `spring-session-jdbc` | 3.5.7 | 已包含，模式已创建，配置完成 |
| `spring-security-test` | 3.5.7 | 测试范围 |
| JWT 库（jjwt、nimbus 等） | — | **未包含** |
| Redis（lettuce、jedis 等） | — | **未包含** |
| OAuth2 客户端/资源服务器 | — | **未包含** |
| `springdoc-openapi` | — | **未包含** |
| `spring-modulith-starter-core` | 1.4.0 | 已包含 |
| Testcontainers | 1.21.4 | 测试范围，PostgreSQL 18.4 |

---

## 3. 部署假设与未知项 (Deployment Assumptions & Unknowns)

### 从代码和配置中确认

```text
FRONTEND_BACKEND_ORIGIN_MODEL              = UNKNOWN_REQUIRES_PRODUCT_DECISION
  证据：无 CORS 配置。application.yml 中无前端 URL。
  推理：POM 中仅有 spring-boot-starter-web（无 spring-boot-starter-webflux）。
        Controller 返回 ResponseEntity，无 CORS 头。
        SecurityConfig 无 CORS 配置。

FIRST_PARTY_BROWSER_ONLY                   = LIKELY_YES
  证据：无第三方客户端 SDK。无 OAuth2 客户端凭据流。
        API 设计（基于 Cookie 的 TempActorId 模式）适合第一方 SPA。
  注意：需产品确认。

MOBILE_OR_THIRD_PARTY_CLIENT_REQUIRED      = UNKNOWN_REQUIRES_PRODUCT_DECISION
  证据：无移动端或第三方客户端证据。
  注意：如需要，Spring Session 可以通过 Bearer 令牌头进行扩展。

MULTI_INSTANCE_DEPLOYMENT                  = UNKNOWN_REQUIRES_PRODUCT_DECISION
  证据：application.yml 中无 Spring Session 的 Hazelcast/Redis 备份。
        当前的 JDBC 会话存储适用于单实例或粘性会话负载均衡。
        多实例非粘性会话需要共享会话存储。

SPRING_SESSION_JDBC_AVAILABLE              = YES
  证据：pom.xml 第 51-53 行：spring-session-jdbc。
        application.yml 第 32-35 行：spring.session.store-type=jdbc。
        V015__create_spring_session_tables.sql 存在并创建所需表。

REDIS_AVAILABLE                             = NO
  证据：POM 中无 spring-boot-starter-data-redis。
        docker-compose.yml 中无 Redis 服务。
        application.yml 中无 Redis 配置。

MULTI_DEVICE_LOGIN_REQUIRED                = UNKNOWN_REQUIRES_PRODUCT_DECISION
  证据：无多设备策略配置。
  注意：Spring Session 允许按主体名称并发会话。最大会话数可配置。

FORCED_LOGOUT_REQUIRED                     = LIKELY_YES（从 User.disable() 隐含）
  证据：User 聚合具有 disable() 转换。
        应用程序服务公开了 disable 端点。
  注意：需要会话失效策略。
```

### 需产品决策

| # | 问题 | 影响 |
|---|------|------|
| D1 | 前端 URL / 源（开发、预发布、生产）？ | CORS 配置、SameSite Cookie 策略 |
| D2 | 仅限第一方浏览器 SPA，还是也需要移动端/第三方 API 访问？ | 基于 Cookie 的会话 vs 混合 Bearer 令牌 |
| D3 | 多实例后端部署（负载均衡）？ | 会话持久化策略——JDBC 足够（粘性会话）或需要共享存储 |
| D4 | 允许多设备同时登录？ | 最大会话数配置、会话列表 API |
| D5 | 管理员需要强制用户下线能力吗？ | `spring_session` 删除 API、会话撤销端点 |
| D6 | 密码修改应使其他设备上的现有会话失效吗？ | 会话注销策略 |

---

## 4. 认证方案比较 (Authentication Scheme Comparison)

### 方案 A：服务端 Session + 安全 HttpOnly Cookie ✅ 已选择

| 维度 | 评估 |
|------|------|
| **第一方 SPA 适配性** | ✅ 优秀。Cookie 在每个请求上自动发送——无需前端令牌管理。 |
| **Spring Session JDBC 复用** | ✅ 直接复用。`spring.session.store-type=jdbc` 已配置。`spring_session` 表已存在（V015）。 |
| **多实例会话共享** | ✅ JDBC 存储提供粘性会话开箱即用。对于非粘性，回退到数据库查询。未来可无痛迁移到 Redis。 |
| **安全 Cookie 配置** | ✅ 计划：`Secure=true`、`HttpOnly=true`、`SameSite=Lax` |
| **CSRF 保护** | ✅ 必须重新启用 CSRF 保护。SPA 从 Cookie 读取 CSRF 令牌并通过 `X-CSRF-TOKEN` 头发送。 |
| **登录/退出/强制下线** | ✅ 即时。`session.invalidate()` 或 `DELETE FROM spring_session WHERE principal_name = ?`。 |
| **会话固定攻击防护** | ✅ Spring Security 的内置 `SessionFixationProtectionStrategy`（`migrateSession`）。 |
| **跨域部署复杂度** | ⚠️ 对于跨域需要特殊处理（需要 `SameSite=None; Secure`）。同域部署简单。 |
| **内存/数据库负载** | ⚠️ 每个会话在主内存中有一个 `Session` 对象 + `spring_session` 中的一行。对于数百个并发用户可接受。 |

### 方案 B：短期 Access Token + Refresh Token

| 维度 | 评估 |
|------|------|
| **Access Token 有效期** | ✅ 短期（15 分钟）限制攻击窗口。 |
| **Refresh Token 存储** | ✅ 建议：Secure HttpOnly Cookie 中的 Refresh Token。仅存储 SHA-256 哈希。 |
| **Refresh Token 轮换** | ✅ 每次使用发放新的 Refresh Token。旧令牌失效。 |
| **重用检测** | ✅ 如果曾用过的 Refresh Token 被使用 → 撤销该用户的所有令牌。 |
| **多设备会话管理** | ✅ 每个设备/会话独立 Refresh Token 行。 |
| **数据库表和迁移** | ❌ 需要新的 `refresh_tokens` 或等效表（新迁移）。 |
| **签名密钥管理** | ❌ 需要密钥生成、轮换和安全存储配置。 |
| **撤销复杂度** | ⚠️ 适度。需要服务端撤销表 + 定期清理作业。 |
| **POM 变化** | ❌ 需要添加 JWT 库（jjwt 或 nimbus-jose-jwt）。 |

### 方案 C：Bearer JWT 存入 localStorage

| 维度 | 评估 |
|------|------|
| **XSS 风险** | ❌ localStorage 可通过 `localStorage.getItem('token')` 被任何 JS 读取。任何 XSS 漏洞 = 完全令牌泄露。 |
| **CSRF 免疫** | ✅ 无自动 Cookie 发送意味着无 CSRF。但 XSS 风险压倒这一点。 |
| **撤销** | ❌ 无状态 JWT 无法撤销。需要撤销黑名单（为无状态令牌添加状态）。 |
| **令牌过期** | ❌ 无 Refresh Token 模式，Access Token 必须长存（数小时到数天），扩大攻击窗口。 |
| **SPA 模式** | ❌ 不应将"SPA 通常使用 JWT"作为选择不安全存储的依据。 |

### 裁决

```text
SELECTED_AUTHENTICATION_MODE               = SERVER_SESSION_WITH_SECURE_HTTPONLY_COOKIE
REJECTED_AUTHENTICATION_MODES              = BEARER_JWT_IN_LOCALSTORAGE
DEFERRED_AUTHENTICATION_MODES              = SHORT_LIVED_ACCESS_TOKEN_WITH_REFRESH_TOKEN
SELECTION_REASON                           =
  1. Spring Session JDBC 已经配置和模式化——零额外基础设施。
  2. Secure HttpOnly Cookie 针对第一方浏览器 SPA 提供了最强的 XSS/令牌盗窃保护。
  3. 会话通过 DB DELETE 或 session.invalidate() 立即可撤销——对
     禁用用户、密码修改和强制下线至关重要。
  4. 无需向 POM 添加 JWT 库或创建新迁移。
  5. CSRF 保护可重新启用，遵循成熟的 Spring Security 模式。
  6. 如以后需要非浏览器客户端，Session 可通过 Bearer 令牌头
     或短期 Access Token 进行扩展。

SECURITY_TRADEOFFS                         =
  1. 需要 CSRF 保护（方案 C 不需要——但 XSS 风险无法接受）。
  2. 会话需要数据库空间（方案 B 不需要——但撤销能力是值得的）。
  3. 多实例部署需要粘性会话或共享会话存储（如果出现此需求，
     未来可迁移到 Redis）。
  4. Cookie 的 SameSite 限制意味着跨域部署是可能的但不简单。
```

---

## 5. 认证主体设计 (Authentication Principal Design)

### 5.1 Principal 类型

```java
/**
 * Authentication principal wrapping the domain User identity.
 * Populated by UserDetailsService during authentication.
 * Attached to SecurityContext after successful login.
 */
public final class CampusGuinnessUserDetails implements UserDetails {

    private final UUID userId;           // ← actorId 来源
    private final String username;       // 登录名
    private final String passwordHash;   // BCrypt 哈希（仅用于匹配，不暴露）
    private final AccountStatus accountStatus;
    private final String platformRole;   // null 或 "SUPER_ADMIN"
    private final Set<GrantedAuthority> authorities; // 从白名单映射派生

    // UserDetails 契约
    public String getPassword() { return passwordHash; }
    public String getUsername() { return username; }
    public boolean isEnabled() { return accountStatus == AccountStatus.NORMAL; }
    public boolean isAccountNonLocked() { return accountStatus != AccountStatus.LOCKED; }

    // 自定义访问器
    public UUID getUserId() { return userId; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    // 注意：不暴露 passwordHash。仅通过 PasswordEncoder.matches 内部使用。
}
```

### 5.2 actorId 映射

```text
PRINCIPAL_TYPE                             = CampusGuinnessUserDetails (implements UserDetails)
PRINCIPAL_FIELDS                           = userId (UUID), username (String), accountStatus (enum),
                                             platformRole (String), authorities (Set<GrantedAuthority>)
ACTOR_ID_MAPPING                           =
  actorId = ((CampusGuinnessUserDetails) authentication.getPrincipal()).getUserId()
  从以下位置获取：SecurityContextHolder.getContext().getAuthentication()
SECURITY_CONTEXT_ACCESS_LOCATION           = Controller 层或专用 CurrentActor 抽象
CURRENT_ACTOR_ABSTRACTION_REQUIRED         = YES（推荐）

  推荐的安全边界接口：

  public interface CurrentActor {
      UUID requireUserId();
  }

  实现（基础设施层）：
  - 从 SecurityContextHolder 提取 Authentication
  - 转换为 CampusGuinnessUserDetails
  - 返回 userId
  - 如果未认证，抛出 AuthenticationException

  该抽象防止：
  - Controller 直接依赖 SecurityContextHolder
  - Application Service 依赖 HttpServletRequest
  - Domain 层接触任何安全概念
```

### 5.3 设计规则

```text
actorId 不得来自请求体、查询参数或自定义 Header        = YES
Controller 不得自行解析 JWT 或 Cookie                  = YES
Application Service 不得依赖 HttpServletRequest       = YES
Domain 层不得依赖 SecurityContext                      = YES
用户名/邮箱/手机号不得直接充当领域 User UUID            = YES
```

---

## 6. User 与 Credential 认证规则

### 6.1 User 状态登录规则

```text
LOGIN_ALLOWED_USER_STATUSES                = NORMAL
LOGIN_DENIED_USER_STATUSES                 = PENDING_ACTIVATION, LOCKED, DISABLED
ACTIVE_SESSION_REVOCATION_ON_DISABLE       = YES
  User.disable() 时 → 应用层必须使该用户所有 Spring Session 失效
  DELETE FROM spring_session WHERE principal_name = ?
REENABLE_LOGIN_BEHAVIOR                    = 用户必须重新登录
  重新启用不恢复旧会话。旧会话已在上次禁用时被删除。
```

### 6.2 密码存储

```text
CURRENT_PASSWORD_STORAGE                   =
  列：users.password_hash varchar(255) NOT NULL
  当前哈希：无——尚未实现哈希。
  风险：UserPersistenceMapper.toEntity() 不设置 passwordHash。
        对真实 DB 的 UserApplicationService.create() 会因 NOT NULL 约束而失败。
        在认证实现中必须修复。

SELECTED_PASSWORD_ENCODER                   = BCrypt
  理由：Spring Security 默认。抗 GPU 暴力破解良好。比 Argon2id 更广泛的库支持。
  BCryptPasswordEncoder 带有 strength=12（约 250ms 哈希时间）。

PASSWORD_POLICY                            =
  最小长度：8 个字符
  最大长度：72 个字符（BCrypt 限制）
  必须包含：无特定字符类别要求（初始 MVP）
  不得是：用户名、常见密码

LOGIN_FAILURE_POLICY                       =
  连续失败 5 次 → 锁定账户 10 分钟
  成功登录 → 重置 login_failures = 0
  实现：在 login_failures >= 5 后设置 locked_until = now() + 10 分钟
  计数字段：UserEntity.loginFailures（需要在认证基础设施中读取/写入）

PASSWORD_CHANGE_SUPPORT                    = YES（需实现）
  已认证用户可以 POST /api/v1/auth/change-password
  需要当前密码 + 新密码
  将 password_hash 更新为新 BCrypt 哈希
  可选：密码修改时使其他会话失效

PASSWORD_RESET_SUPPORT                     = DEFERRED（不是初始认证 MVP）
  需要：电子邮件/短信集成、重置令牌生成、有效期窗口。

DATABASE_MIGRATION_REQUIRED                = NO
  password_hash 列已存在。无需模式更改。
```

### 6.3 认证安全规则

```text
不得存储明文密码                              = ENFORCED（仅存储 BCrypt 哈希）
不得记录原始密码                              = ENFORCED
不得在 API 响应中返回密码哈希                  = ENFORCED（InterfaceArchitectureTest 验证）
密码校验必须使用 PasswordEncoder.matches       = ENFORCED
不存在用户和错误密码应返回相同的外部错误         = ENFORCED（"用户名或密码无效"）
```

---

## 7. 角色与 Authority 模型

### 7.1 角色分类

```text
PLATFORM_ROLE_MODEL                        =
  来源：users.platform_role（varchar(32)）
  数据库约束：NULL 或 'SUPER_ADMIN'
  当前有效值：
    SUPER_ADMIN  → ROLE_SUPER_ADMIN（Spring Security 前缀约定）
    null         → 仅学校级角色（从 school_memberships 派生）

SCHOOL_ROLE_MODEL                          =
  来源：school_memberships.role_in_school（varchar(32)）
  数据库约束：'STUDENT'、'TEACHER'、'SCHOOL_ADMIN'
  当前有效值：
    SCHOOL_ADMIN → ROLE_SCHOOL_ADMIN
    TEACHER      → ROLE_TEACHER
    STUDENT      → ROLE_STUDENT

  设计说明：学校角色通过 schoolId 限定范围。
  ROLE_SCHOOL_ADMIN 仅在检查以 schoolId=X 为范围的资源时授予 authority。
```

### 7.2 Authority 映射（安全适配器中的严格白名单）

```java
// 在 UserDetailsService 中（基础设施层）
public class CampusGuinnessUserDetailsService implements UserDetailsService {

    private static final Set<String> KNOWN_PLATFORM_ROLES = Set.of("SUPER_ADMIN");
    private static final Map<String, String> ROLE_IN_SCHOOL_TO_AUTHORITY = Map.of(
        "SCHOOL_ADMIN", "ROLE_SCHOOL_ADMIN",
        "TEACHER",      "ROLE_TEACHER",
        "STUDENT",      "ROLE_STUDENT"
    );

    public UserDetails loadUserByUsername(String username) {
        UserEntity userEntity = jpa.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        Set<GrantedAuthority> authorities = new HashSet<>();

        // 平台角色：严格白名单
        if (userEntity.getPlatformRole() != null) {
            if (KNOWN_PLATFORM_ROLES.contains(userEntity.getPlatformRole())) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + userEntity.getPlatformRole()));
            } else {
                log.warn("Unknown platform_role '{}' for user {} — denying platform authority",
                    userEntity.getPlatformRole(), userEntity.getId());
            }
        }

        // 学校角色：从有效成员资格加载
        List<SchoolMembershipEntity> memberships = membershipRepo.findByUserId(userEntity.getId());
        for (var m : memberships) {
            if (m.getStatus().equals("ACTIVE") && ROLE_IN_SCHOOL_TO_AUTHORITY.containsKey(m.getRoleInSchool())) {
                // 注意：此处不添加 schoolId 上下文。
                // 学校范围在授权服务中处理，不在此处处理。
                authorities.add(new SimpleGrantedAuthority(ROLE_IN_SCHOOL_TO_AUTHORITY.get(m.getRoleInSchool())));
            }
        }

        return new CampusGuinnessUserDetails(/* ... */, authorities);
    }
}
```

### 7.3 裁决

```text
STRING_TO_AUTHORITY_VALIDATION             = 严格白名单——未知值被拒绝并记录安全告警。
                                             不使用 new SimpleGrantedAuthority(databaseString)。
UNKNOWN_ROLE_BEHAVIOR                      = 拒绝（无 authority 被授予）+ WARN 级别日志。
                                             用户仍经过认证但被有效限制。
ENUM_MIGRATION_REQUIRED                    = DEFERRED（拆分为 TASK-AUTH-ROLE-MAPPING-001）
  理由：将 platformRole 和 roleInSchool 迁移到枚举会触及：
  - 领域 User 和 SchoolMembership 类（可能破坏现有领域测试）
  - 数据库 CHECK 约束（需要新迁移）
  - 每个 DTO 和 ApplicationResult 的序列化
  安全适配器中的白名单边界提供了等效的安全性，无需此破坏性变更。
```

---

## 8. 资源级授权设计

### 8.1 授权链（以 Path A 为例）

```text
1. 认证主体有效                         → 401 如果未认证
2. User 状态允许操作                     → 403 如果 DISABLED/LOCKED/PENDING_ACTIVATION
3. 非方法安全：加载目标 ScoreAppeal      → 404 如果未找到（不泄露存在性）
4. 提取 appeal.schoolId                  → 用于资源归属
5. 如果 platformRole == SUPER_ADMIN      → 允许（跨学校旁路）
6. 查询 actor 在 appeal.schoolId 的
   SchoolMembership                       → 403 如果该学校无有效成员资格
7. 根据 roleInSchool 校验                → 403 如果 STUDENT 或角色不足
8. 校验 actorId != appeal.studentId      → 403 如果自我纠正
9. 调用 ScoreAppealCorrectionService      → 正常流程
```

### 8.2 授权规则矩阵

```text
SUPER_ADMIN_BYPASS                        = YES — SUPER_ADMIN 可以纠正任何学校/项目的任何申诉。
SCHOOL_ADMIN_RULE                         = 可以纠正 actor 为 SCHOOL_ADMIN（或 TEACHER）
                                            的学校内的任何申诉。
TEACHER_RULE                              = 可以纠正 actor 为 TEACHER 的学校内的申诉。
                                            未来：可添加项目级范围。
STUDENT_RULE                              = 在任何情况下均不得纠正申诉。
SELF_CORRECTION_RULE                      = 如果 actorId == appeal.studentId → 403。
                                            防止利益冲突。
RESOURCE_NOT_FOUND_DISCLOSURE_POLICY      = 未认证 → 401。已认证但未授权 → 404。
                                            不向攻击者泄露哪些资源 ID 存在。
```

### 8.3 授权服务边界

```text
AUTHORIZATION_SERVICE_BOUNDARY            = 应用层（专用授权服务）
RESOURCE_SCHOOL_RESOLUTION                = 每种资源类型提供其 schoolId（
                                            ScoreAppeal.schoolId()、ScoreAttempt.schoolId() 等）

  推荐结构：

  com.campusguinness.appeal.application.authorization
  └── ScoreAppealAuthorizationService    ← 特定于模块的授权
      方法：authorizeCorrection(UUID actorId, ScoreAppeal appeal)
      抛出：AccessDeniedException 如果未经授权

  可共享基础设施：
  com.campusguinness.infrastructure.authorization
  └── SchoolMembershipResolver           ← 跨领域共享
      方法：findActiveMembership(UUID userId, UUID schoolId)
      返回：Optional<SchoolMembershipEntity>
```

### 8.4 需产品决策

```text
PRODUCT_DECISIONS_REQUIRED                =
  1. TEACHER 是否应仅限于其教学的项目/活动，还是可以访问全校申诉？
     （建议：全校申诉访问，限制在未来细化）
  2. 是否应该有项目级授权（教师 A 只能纠正项目 X 的分数）？
     （建议：不在初始实现中——未来通过 project-level permission 表实现）
```

---

## 9. SecurityConfig 迁移计划

### 9.1 当前状态

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
    .anyRequest().denyAll()
)
```

### 9.2 目标结构（渐进式开放）

```text
PUBLIC_ENDPOINTS                           =
  /actuator/health                         — 负载均衡器健康检查
  /actuator/info                           — 构建信息
  /api/v1/auth/login                       — 登录端点
  /api/v1/auth/refresh                     — 会话续期（如需要）
  （所有公共端点均显式列出——不使用通配符）

AUTHENTICATED_ENDPOINTS                   =
  GET  /api/v1/auth/me                     — 当前用户
  POST /api/v1/auth/logout                 — 退出
  POST /api/v1/auth/change-password        — 修改密码
  GET  /api/v1/schools                     — 公共查询（如确定）
  GET  /api/v1/schools/{id}                — 公共查询（如确定）
  （通常：所有需要认证但不需要特定角色的端点）

ROLE_PROTECTED_ENDPOINTS                  =
  POST /api/v1/score-appeals/{id}/correct-and-resolve  → hasAnyRole('SUPER_ADMIN','SCHOOL_ADMIN','TEACHER')
  + 方法级 @PreAuthorize 用于跨学校边界检查
  （所有管理命令端点——随着接口开放逐步增加）

FINAL_FALLBACK_RULE                        = .anyRequest().denyAll()
  （必须保留——不可替换为 anyRequest().authenticated()）

METHOD_SECURITY_REQUIRED                   = YES
  使用 @EnableMethodSecurity 进行 prePost 授权。
  在方法级别组合 URL 模式角色：
  - URL 模式 → 粗略的角色门控（必须是 SCHOOL_ADMIN/TEACHER/SUPER_ADMIN）
  - @PreAuthorize → 细粒度的学校/资源归属（自定义 bean 方法调用）
```

### 9.3 迁移步骤

```
步骤 1（初始）：
  - 启用 @EnableMethodSecurity
  - 添加 /api/v1/auth/** → permitAll
  - 保留 .anyRequest().denyAll()

步骤 2（逐控制器）：
  - 对于每个被批准开放的控制器：
    添加 .requestMatchers("/api/v1/schools/**").authenticated()
  - 根据需要添加角色约束
  - 移除临时 actorId 从 DTO → 从 SecurityContext 注入

步骤 3（方法安全）：
  - 为资源特定检查添加 @PreAuthorize
  - 实现自定义 SpEL 函数（例如 @authorizeCheck.isSchoolMember(#appealId)）
```

---

## 10. CSRF 与 CORS

### 10.1 CSRF 策略

```text
CSRF_POLICY                               = 已启用（从当前 csrf.disable() 恢复）
CSRF_TOKEN_DELIVERY                       = CookieCsrfTokenRepository.withHttpOnlyFalse()
                                            CSRF 令牌存储在非 HttpOnly Cookie 中，
                                            以便 SPA JS 读取并通过 X-CSRF-TOKEN 头发送。

  理由：SPA 需要访问 CSRF 令牌以将其作为请求头发送。
        Cookie 必须是 SameSite=Lax 且非 HttpOnly。
        HttpOnly=false 在此处是可接受的，因为 CSRF 令牌不是秘密——
        它是一个会话绑定令牌，用于证明请求源自我们的 SPA。

COOKIE_POLICY                             =
  SESSION Cookie：
    Secure=true    （仅通过 HTTPS 发送；开发环境酌情处理）
    HttpOnly=true  （JS 不可访问 → XSS 免疫）
    SameSite=Lax   （随同站导航 + 顶级 GET 发送；在跨站 POST 上阻止）
    Path=/
    Domain=未设置  （绑定到确切的源）

  CSRF Cookie：
    Secure=true
    HttpOnly=false （SPA JS 需要读取此内容）
    SameSite=Lax   （或 Strict 如果我们的 SPA 从不从外部站点链接表单 POST）
```

### 10.2 CORS 策略

```text
CORS_POLICY                               = 显式白名单（不使用 allowOrigins("*")）
ALLOWED_ORIGINS_SOURCE                    = 来自 application.yml 或环境变量的配置属性。

  示例配置：
  campus-guinness:
    cors:
      allowed-origins: ${CORS_ORIGINS:http://localhost:5173}

  规则：
  - allowCredentials(true) 绝不与 allowOrigins("*") 结合
  - 允许的 HTTP 方法：GET、POST、OPTIONS（未来：PUT、DELETE）
  - 允许的头：Content-Type、X-CSRF-TOKEN、X-Requested-With
  - 预检请求缓存：maxAge=3600（1 小时）
  - 暴露的头：无（默认）

  注意：如果前后端同域提供服务（例如 Nginx 反向代理），
        则不需要 CORS。Cookie SameSite=Lax 就足够了。
```

---

## 11. 401 与 403 责任边界

### 11.1 责任分工

```text
AUTHENTICATION_ENTRY_POINT_REQUIRED       = YES
  Spring Security 过滤器链中自定义 AuthenticationEntryPoint。
  当未认证请求到达受保护端点时触发。
  返回：
    HTTP 401
    {"code":"UNAUTHORIZED","message":"需要登录后访问","traceId":"..."}

ACCESS_DENIED_HANDLER_REQUIRED            = YES
  Spring Security 过滤器链中自定义 AccessDeniedHandler。
  当已认证用户缺少所需角色/权限时触发。
  返回：
    HTTP 403
    {"code":"ACCESS_DENIED","message":"无权执行该操作","traceId":"..."}

GLOBAL_EXCEPTION_HANDLER_AUTH_SCOPE       = 补充性（非主要）
  GlobalExceptionHandler 仅处理进入 MVC 调度后抛出的异常
  （例如应用层 @PreAuthorize 失败）。
  这些由 MethodSecurityInterceptor 转换为 AccessDeniedException，
  如果未被 AccessDeniedHandler 捕获，则由 Spring 的默认处理程序返回 403。

  额外处理程序（添加到 GlobalExceptionHandler）：
  @ExceptionHandler(OptimisticLockingFailureException.class) → 409 CONFLICT
  （当前映射到 500——这是一个错误，但与本次任务无关——不修改）

SECURITY_ERROR_RESPONSE_SCHEMA            =
  统一错误响应（两个处理程序使用相同的模式）：
  {
    "code": "UNAUTHORIZED" | "ACCESS_DENIED",
    "message": "人类可读消息",
    "traceId": "UUID（用于日志关联）",
    "timestamp": "2026-07-16T..."
  }
  与现有 ApiErrorResponse(code, message, path, timestamp, details) 兼容。
```

### 11.2 日志安全

```text
AuthenticationEntryPoint 日志：
  - 请求路径
  - 远程 IP
  - 原因（无会话、过期会话）
  - 不记录：请求体、Cookie 值、Session ID

AccessDeniedHandler 日志：
  - 用户 ID 和用户名（来自 Authentication）
  - 请求路径和方法
  - 所需权限与拥有的权限
  - 不记录：请求体、Cookie 值、Session ID
```

---

## 12. 认证接口契约

### 12.1 登录端点

```text
LOGIN_ENDPOINT                             =
  POST /api/v1/auth/login
  Content-Type: application/json
  Request:
  {
    "username": "string（100 字符最大长度）",
    "password": "string（72 字符最大长度——BCrypt 限制）"
  }
  Response 200:
  {
    "userId": "uuid",
    "username": "string",
    "status": "NORMAL",
    "platformRole": "SUPER_ADMIN" | null
  }
  Set-Cookie: SESSION=xxxxx; Secure; HttpOnly; SameSite=Lax; Path=/

  失败响应：
  401 {"code":"UNAUTHORIZED","message":"用户名或密码无效"}
  （不存在用户和错误密码响应相同）

  429 {"code":"TOO_MANY_REQUESTS","message":"登录尝试次数过多，请稍后再试"}
  （如果实现速率限制）

  Cookie 行为：登录成功时设置新的 SESSION Cookie。
  响应体不包含会话 ID、令牌或 CSRF 令牌——仅限 Cookie。

  注意：响应体不包含密码哈希、凭据内部 ID、
        会话数据库 ID 或 Refresh Token 哈希。
```

### 12.2 当前用户端点

```text
CURRENT_USER_ENDPOINT                      =
  GET /api/v1/auth/me
  需要：已认证会话
  响应 200：
  {
    "userId": "uuid",
    "username": "string",
    "status": "NORMAL",
    "platformRole": "SUPER_ADMIN" | null,
    "memberships": [
      {
        "schoolId": "uuid",
        "schoolName": "string（查询加入的名称）",
        "roleInSchool": "SCHOOL_ADMIN" | "TEACHER" | "STUDENT",
        "status": "ACTIVE" | "ENDED"
      }
    ]
  }
  401 — 未认证或会话过期
```

### 12.3 退出端点

```text
LOGOUT_ENDPOINT                            =
  POST /api/v1/auth/logout
  需要：已认证会话
  Response 200:
  {"message":"已成功退出"}
  Set-Cookie: SESSION=; Max-Age=0; Secure; HttpOnly; SameSite=Lax; Path=/
  （清除会话 Cookie）

  服务器端：session.invalidate() + 从 spring_session 删除。
  CSRF 保护：需要有效的 CSRF 令牌。
  注意：当前实现使当前会话失效。
        未来增强：logout?allDevices=true 使该用户的所有会话失效。
```

### 12.4 会话续期端点

```text
REFRESH_OR_SESSION_RENEW_ENDPOINT          =
  POST /api/v1/auth/renew
  需要：已认证会话
  Response 200:
  {"message":"会话已续期","expiresAt":"2026-07-16T15:30:00Z"}
  行为：将会话 maxInactiveInterval 重置为其初始值。
        如果上次访问时间在空闲超时的一半以内，则无操作。
  401 — 会话已过期

  注意：用于纯基于 Cookie 的会话，这是会话续期 ping
        ——不是令牌刷新。SPA 在用户活动时定期 ping 此端点
        以保持会话活跃，超出默认空闲超时。
```

### 12.5 修改密码端点

```text
CHANGE_PASSWORD_ENDPOINT                   =
  POST /api/v1/auth/change-password
  需要：已认证会话
  Request:
  {
    "currentPassword": "string",
    "newPassword": "string（8-72 字符）"
  }
  Response 200:
  {"message":"密码修改成功"}
  行为：验证当前密码，将 password_hash 更新为新 BCrypt 哈希。
        可选：使该用户的所有其他会话失效
        （DELETE FROM spring_session WHERE principal_name = ? AND primary_id != current_session_id）。
  400 — 新密码与当前密码相同
  401 — 当前密码不正确
  403 — 用户状态不允许密码修改
```

---

## 13. 会话生命周期与撤销

### 13.1 超时配置

```text
AUTH_SESSION_LIFETIME                      =
IDLE_TIMEOUT                               = 30 分钟（可配置）
  server.servlet.session.timeout=30m
  会话在 30 分钟不活动后过期。
  SPA 可以通过在用户活动时调用 POST /api/v1/auth/renew 来延长。

ABSOLUTE_TIMEOUT                           = 8 小时（自定义逻辑，不是 Servlet 标准）
  无论活动如何，会话在创建 8 小时后过期。
  实现：将 creation_time 存储在会话属性中，在过滤器中检查。

REVOCATION_MECHANISM                       =
  1. 主动退出：POST /api/v1/auth/logout → session.invalidate()
  2. 用户禁用：在 UserApplicationService.disable() 中
     → DELETE FROM spring_session WHERE principal_name = ?
  3. 管理员强制下线：
     → DELETE FROM spring_session WHERE primary_id = ?
     （未来实现，专用管理端点）
  4. 不活动：Spring Session 自动清理（expiry_time 索引）
  5. 密码修改：可选使其他会话失效（DELETE ... WHERE principal_name = ?）

PASSWORD_CHANGE_SESSION_POLICY             =
  初始实现：使该用户的所有其他会话失效。
  当前会话已更新为新的密码哈希（如果我们将哈希存储在会话中——但我们不存储）。
  实际上：密码修改仅影响未来登录。旧会话保持有效，直到过期。
  增强（拆分任务）：在会话属性中存储 password_changed_at 时间戳。
  在每个请求上验证时间戳与当前密码哈希版本。

USER_DISABLE_SESSION_POLICY                =
  当 User.disable() 被调用时：
  1. UserApplicationService 调用 domainUser.disable()
  2. UserApplicationService 调用 SessionRevocationService.revokeAllSessions(userId)
  3. SessionRevocationService 执行：
     DELETE FROM spring_session WHERE principal_name = ?
  4. 用户的下一个请求将具有无效会话 → 401
  5. 登录尝试被拒绝 → 401（"账户已被禁用"）
```

### 13.2 Spring Session JDBC 清理

```text
Spring Session JDBC 通过 spring_session 表中的 expiry_time 列自动处理到期清理。
无需额外配置。
默认清理作业在后台运行，删除过期会话。

Spring Session 配置（application.yml）：
spring:
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: never     # 已由 V015 处理
    timeout: 30m                     # 空闲超时
```

---

## 14. 安全审计事件

### 14.1 审计事件清单

```text
AUTH_AUDIT_EVENTS                         =
  1. LOGIN_SUCCESS          — 成功的用户认证
  2. LOGIN_FAILURE          — 失败的登录尝试（用户名不存在或密码错误）
  3. LOGOUT                 — 用户主动退出
  4. SESSION_EXPIRED        — 会话因不活动而过期
  5. PASSWORD_CHANGED       — 用户修改了自己的密码
  6. ACCOUNT_LOCKED         — 登录失败次数过多触发账户锁定
  7. ACCOUNT_UNLOCKED       — 账户在锁定期限后或通过管理员解锁
  8. USER_DISABLED          — 管理员或系统禁用用户（触发会话撤销）
  9. USER_REENABLED         — 用户被重新启用
  10. FORCED_LOGOUT         — 管理员强制终止用户会话
  11. ACCESS_DENIED         — 高严重性授权失败（例如自我纠正尝试）
  12. SUSPICIOUS_ACTIVITY   — 异常模式（未来增强）

SENSITIVE_LOG_FIELDS_EXCLUDED             =
  原始密码、会话 ID、Access Token、Refresh Token、
  Cookie 值、密码哈希、用户个人数据（非必要用于审计）。

AUTH_AUDIT_ATOMICITY                      =
  审计记录不应与主业务事务原子提交。
  理由：审计是观察性关注点，不是业务不变量。
        失败的审计写入不应回滚用户操作。
  实现：使用 @TransactionalEventListener(phase = AFTER_COMMIT)
        在业务事务成功后异步写入审计记录。
  表：audit_records（V013）——已存在，具有所需的列。
```

### 14.2 审计记录结构

```java
// 使用现有 AuditRecordEntity（V013 表）
AuditRecordEntity record = new AuditRecordEntity();
record.setId(UUID.randomUUID());
record.setActorId(actorId);        // 谁执行了操作
record.setAction("LOGIN_SUCCESS"); // 标准化操作名称
record.setTargetType("USER");       // 目标实体类型
record.setTargetId(userId);        // 目标实体 ID
record.setSchoolId(null);           // 用于学校范围的操作
record.setDetail("{\"ip\":\"...\"}"); // JSONB——附加上下文
record.setIpAddress(request.getRemoteAddr());
record.setCreatedAt(Instant.now());
```

---

## 15. 测试计划

### 15.1 必需单元测试

```text
REQUIRED_UNIT_TESTS                       =

  CampusGuinnessUserDetailsService：
  ✓ 有效用户名 + 正确密码 → 返回 UserDetails
  ✓ 有效用户名 + 错误密码 → 抛出 UsernameNotFoundException（与不存在用户消息相同）
  ✓ 不存在用户名 → 抛出 UsernameNotFoundException（消息相同）
  ✓ PENDING_ACTIVATION 用户 → isEnabled() = false，登录被拒绝
  ✓ LOCKED 用户 → isAccountNonLocked() = false，登录被拒绝
  ✓ DISABLED 用户 → isEnabled() = false，登录被拒绝
  ✓ NORMAL 用户 → 完全可登录
  ✓ 未知 platformRole → 不授予平台 authority，已记录
  ✓ platformRole=SUPER_ADMIN → 授予 ROLE_SUPER_ADMIN
  ✓ roleInSchool=STUDENT → 授予 ROLE_STUDENT
  ✓ roleInSchool=TEACHER → 授予 ROLE_TEACHER
  ✓ roleInSchool=SCHOOL_ADMIN → 授予 ROLE_SCHOOL_ADMIN
  ✓ 未知 roleInSchool → 不授予 authority，已记录
  ✓ ENDED 成员资格 → 不授予 authority
  ✓ 成功登录将 loginFailures 重置为 0

  CurrentActor（HttpServletRequest 实现）：
  ✓ 已认证 → 返回 userId
  ✓ 未认证 → 抛出 AuthenticationException
  ✓ Principal 不是 CampusGuinnessUserDetails → 抛出 IllegalStateException

  BCryptPasswordEncoder：
  ✓ 对同一输入两次 encode() → 不同输出（盐值）
  ✓ matches(原始, 编码) → true
  ✓ matches(不同, 编码) → false

  SessionRevocationService：
  ✓ revokeAllSessions(userId) → 删除该用户的所有行
  ✓ revokeOtherSessions(userId, currentSessionId) → 仅保留当前会话
```

### 15.2 必需 HTTP 集成测试（@WebMvcTest + Mock Security）

```text
REQUIRED_SECURITY_HTTP_ITS                =

  认证流程：
  ✓ POST /api/v1/auth/login 正确凭据 → 200 + Set-Cookie
  ✓ POST /api/v1/auth/login 错误密码 → 401 + 无 Set-Cookie
  ✓ POST /api/v1/auth/login 不存在用户 → 401（与错误密码消息相同）
  ✓ POST /api/v1/auth/login 禁用用户 → 401
  ✓ GET /api/v1/auth/me 已认证 → 200 + 用户数据
  ✓ GET /api/v1/auth/me 未认证 → 401
  ✓ POST /api/v1/auth/logout 已认证 → 200 + 清除 Cookie
  ✓ POST /api/v1/auth/change-password 正确当前密码 → 200
  ✓ POST /api/v1/auth/change-password 错误当前密码 → 401

  授权流程：
  ✓ 未认证 GET /api/v1/schools → 401（假设端点是受保护的）
  ✓ NORMAL 用户无角色 GET /api/v1/score-appeals/X/begin-processing → 403
  ✓ SCHOOL_ADMIN 访问本校申诉 → 200
  ✓ SCHOOL_ADMIN 访问他校申诉 → 403（或 404，取决于策略）
  ✓ SUPER_ADMIN 访问任何学校 → 200
  ✓ STUDENT 尝试纠正 → 403
  ✓ 自我纠正（actorId == studentId）→ 403
  ✓ 请求体无法伪造 actorId → 拒绝或忽略

  CSRF：
  ✓ 无 CSRF 令牌的 POST → 403（如果已启用 CSRF）
  ✓ 带有效 CSRF 令牌的 POST → 200
```

### 15.3 必需 PostgreSQL 集成测试

```text
REQUIRED_POSTGRESQL_ITS                   =

  ✓ 登录成功 → 会话持久化到 spring_session
  ✓ 登录成功 → login_failures 重置为 0
  ✓ 登录失败 → login_failures 递增
  ✓ 第 5 次连续登录失败 → locked_until 设置，账户被锁定
  ✓ 锁定用户在 locked_until 过期后可以登录
  ✓ User.disable() → 所有会话从 spring_session 中删除
  ✓ 密码修改 → password_hash 使用 BCrypt 更新
  ✓ 退出 → 会话从 spring_session 中删除
  ✓ 会话到期 → 重新访问返回 401
  ✓ 审计记录在登录成功时写入 audit_records
  ✓ 审计记录在登录失败时写入 audit_records
```

### 15.4 必需并发测试

```text
REQUIRED_CONCURRENCY_TESTS                =

  ✓ 同一用户并发登录 → 均成功，创建两个会话（如允许多设备）
  ✓ 密码修改 + 其他设备上的并发请求 → 行为正确（如果实现了跨会话失效）
  ✓ 登录失败计数并发 → 无丢失更新（乐观锁或 DB 约束）
  ✓ 强制下线 + 用户主动请求 → 正确撤销
```

---

## 16. 数据库与依赖变化

```text
DATABASE_MIGRATION_REQUIRED                = NO
  当前模式（V001-V015）包含所有必需的列和表：
  - users.password_hash          → BCrypt 哈希存储
  - users.login_failures         → 失败跟踪
  - users.locked_until           → 临时锁定
  - school_memberships 表         → 学校角色
  - spring_session 表             → 会话持久化
  - audit_records 表              → 安全审计
  无需新迁移。

DEPENDENCY_CHANGES_REQUIRED               = NO
  当前 POM 包含：
  - spring-boot-starter-security    → 已存在
  - spring-session-jdbc             → 已存在
  - spring-security-test            → 已存在（测试范围）
  无需新依赖。

REQUIRED_PRODUCTION_FILES                 =
  新文件（在实施期间创建，不在此任务期间）：
  1. CampusGuinnessUserDetails.java                   — Principal 类
  2. CampusGuinnessUserDetailsService.java            — UserDetailsService 实现
  3. SecurityConfig.java（修改）                       — Csrf 启用、denyAll 迁移
  4. CookieCsrfTokenRepository 配置（或等效）           — CSRF 令牌交付
  5. CurrentActor.java + HttpServletRequest 实现       — actorId 抽象
  6. SchoolMembershipResolver.java                     — 学校成员资格查询
  7. ScoreAppealAuthorizationService.java              — 特定于 Path A 的授权
  8. AuthenticationEntryPoint 实现                     — 401 处理程序
  9. AccessDeniedHandler 实现                          — 403 处理程序
  10. AuthController.java                               — 登录/退出/me/修改密码/续期
  11. LoginRequest.java、ChangePasswordRequest.java 等  — 请求 DTO
  12. AuthResponse.java、CurrentUserResponse.java        — 响应 DTO
  13. SessionRevocationService.java                     — 会话撤销逻辑
  14. AuthAuditService.java                              — 审计事件发布

REQUIRED_TEST_FILES                       =
  1. CampusGuinnessUserDetailsServiceTest.java
  2. AuthControllerTest.java（WebMvcTest）
  3. AuthIntegrationTest.java（PostgreSQL + Testcontainers）
  4. ScoreAppealAuthorizationServiceTest.java
  5. SessionRevocationServiceTest.java
  6. SecurityConfigTest.java（验证 denyAll 兜底仍然有效）
```

---

## 17. 风险与缓解措施

| 风险 | 严重性 | 缓解措施 |
|------|--------|---------|
| `UserPersistenceMapper` 不设置 `passwordHash` — 任何用户保存都会清除密码 | 严重 | 实施中的第一个修复：更新映射器以使用 upsert 模式保留 `passwordHash`、`loginFailures`、`lockedUntil` |
| `UserRepository` 端口缺少 `findByUsername` | 高 | 添加 `findByUsername` 到端口，在适配器中公开 |
| CSRF 之前被禁用 — 重新启用可能破坏现有 SPA 集成 | 中 | SPA 必须添加 CSRF 令牌处理。实施前与前端团队协调。 |
| 所有 13 个控制器中的临时 actorId 字段 | 中 | 逐个迁移到 SecurityContext。临时接受请求体中的 actorId 直到认证到位。 |
| Spring Session JDBC 在多实例下无共享存储 | 中 | 使用粘性会话，直到需要 Redis/共享存储。监控。 |
| `SchoolMembership` 加载被延迟 | 低 | 为 `UserDetailsService` 和授权解析器实现专用的成员资格加载。 |

---

## 18. 实施任务拆分

### 18.1 推荐实施顺序

```text
第 1 阶段：核心认证（约 3-5 个实施会话）

TASK-AUTH-FOUNDATION-001：认证基础实现
  前置条件：TASK-AUTH-001 已批准
  实现：
    - CampusGuinnessUserDetails（Principal 类）
    - CampusGuinnessUserDetailsService（加载 UserEntity + 密码检查）
    - BCryptPasswordEncoder Bean
    - CurrentActor 接口 + HttpServletRequest 实现
    - 修复 UserPersistenceMapper 保留 passwordHash/loginFailures/lockedUntil
    - 为 findByUsername 扩展 UserRepository 端口
  验证：
    - 所有必需单元测试通过
    - UserDetailsService 正确加载/拒绝用户
    - PasswordEncoder Bean 已配置

TASK-AUTH-FOUNDATION-002：SecurityConfig + CSRF + CORS
  前置条件：TASK-AUTH-FOUNDATION-001
  实现：
    - 从 csrf.disable() 迁移到 CookieCsrfTokenRepository
    - 为 CORS 配置添加 CorsConfigurationSource Bean
    - 设置 SameSite、Secure、HttpOnly Cookie 属性
    - 保留 .anyRequest().denyAll() 作为最终兜底
  验证：
    - CSRF 保护已激活
    - CORS 允许白名单源
    - denyAll 兜底仍然有效

TASK-AUTH-FOUNDATION-003：认证端点
  前置条件：TASK-AUTH-FOUNDATION-002
  实现：
    - AuthController（login, logout, me, change-password, renew）
    - 请求/响应 DTO
    - SessionRevocationService
    - AuthenticationEntryPoint → 401
    - AccessDeniedHandler → 403
  验证：
    - WebMvcTest 全部覆盖
    - PostgreSQL IT 全部覆盖

第 2 阶段：授权（约 2-3 个实施会话）

TASK-AUTH-ROLE-MAPPING-001：角色映射
  前置条件：TASK-AUTH-FOUNDATION-003
  实现：
    - 在 UserDetailsService 中严格白名单映射
    - SchoolMembershipResolver（按用户 + 学校 ID 加载成员资格）
    - 成员资格加载（解决"延迟"注释）
  验证：
    - 所有角色映射的单元测试
    - 未知角色拒绝已测试

TASK-PATH-A-AUTHORIZATION-001：Path A 学校归属授权
  前置条件：TASK-AUTH-ROLE-MAPPING-001
  实现：
    - ScoreAppealAuthorizationService
    - 自我纠正防止
    - SUPER_ADMIN 旁路
    - 在控制器中从 SecurityContext 注入 actorId
  验证：
    - 所有授权规则的单元测试
    - 跨学校访问已测试

第 3 阶段：验证 + 审计（约 2-3 个实施会话）

TASK-PATH-A-CONCURRENCY-VERIFY-001：并发修正验证
  前置条件：TASK-PATH-A-AUTHORIZATION-001
  实现：
    - 双线程、双事务并发修正测试
    - 验证 @Version 乐观锁 → 409 映射
  验证：并发测试通过

TASK-SCORE-CORRECTION-AUDIT-001：成绩修正审计
  前置条件：TASK-PATH-A-AUTHORIZATION-001
  实现：
    - AuthAuditService（异步 AFTER_COMMIT 审计写入）
    - 审计所有认证事件
    - 审计成绩修正事件
  验证：
    - 审计记录正确写入 audit_records
    - 审计失败不中止业务操作

第 4 阶段：接口开放（后续任务）

TASK-PATH-A-ENDPOINT-001：Path A HTTP 端点
  前置条件：TASK-PATH-A-AUTHORIZATION-001
  （实现 path-a-http-exposure-plan.md 中定义的实际端点）
```

### 18.2 任务依赖关系图

```text
TASK-AUTH-001（本任务——计划）
    │
    ▼
TASK-AUTH-FOUNDATION-001（UserDetailsService + PasswordEncoder + CurrentActor）
    │
    ▼
TASK-AUTH-FOUNDATION-002（SecurityConfig + CSRF + CORS）
    │
    ▼
TASK-AUTH-FOUNDATION-003（AuthController + 会话生命周期 + 401/403 处理程序）
    │
    ├──────────────────────────┐
    ▼                          ▼
TASK-AUTH-ROLE-MAPPING-001    （用户/成员资格查询增强）
    │
    ▼
TASK-PATH-A-AUTHORIZATION-001（Path A 学校归属授权）
    │
    ├──────────────────────────┐
    ▼                          ▼
TASK-PATH-A-CONCURRENCY-      TASK-SCORE-CORRECTION-
VERIFY-001                    AUDIT-001
    │                          │
    └──────────┬───────────────┘
               ▼
        TASK-PATH-A-ENDPOINT-001
        （Path A HTTP 端点实现）
```

---

## 19. 完成门禁检查清单

```text
✅ 完成当前认证代码审计
✅ 完成 Session 与 Token 方案比较
✅ 作出认证方式裁决（Session + Cookie）
✅ actorId 映射契约明确
✅ User 状态登录规则明确
✅ 密码存储方案明确（BCrypt）
✅ 角色白名单映射明确
✅ 学校资源授权边界明确
✅ 401 与 403 责任边界明确
✅ SecurityConfig 保留 denyAll 兜底
✅ CSRF 和 CORS 策略明确
✅ 会话撤销策略明确
✅ 测试计划明确
✅ 实施任务已合理拆分
✅ 生产代码修改为 0
✅ 测试修改为 0
✅ 数据库修改为 0
✅ 依赖修改为 0
✅ 仅创建文档：docs/design/authentication-authorization-foundation-plan.md
```

---

## 20. 完成报告

### 摘要

```text
TASK-AUTH-001 = COMPLETED
MODE = READ_ONLY_ARCHITECTURE_AUDIT
```

### 交付物

```text
CODE_CHANGES              = 0
TEST_CHANGES              = 0
DATABASE_CHANGES          = 0
DEPENDENCY_CHANGES        = 0
DOCUMENT_CREATED_OR_UPDATED = docs/design/authentication-authorization-foundation-plan.md
```

### 部署前提

```text
FRONTEND_BACKEND_ORIGIN_MODEL              = UNKNOWN_REQUIRES_PRODUCT_DECISION
FIRST_PARTY_BROWSER_ONLY                   = LIKELY_YES
MOBILE_OR_THIRD_PARTY_CLIENT_REQUIRED      = UNKNOWN_REQUIRES_PRODUCT_DECISION
MULTI_INSTANCE_DEPLOYMENT                  = UNKNOWN_REQUIRES_PRODUCT_DECISION
SPRING_SESSION_JDBC_AVAILABLE              = YES
REDIS_AVAILABLE                             = NO
MULTI_DEVICE_LOGIN_REQUIRED                = UNKNOWN_REQUIRES_PRODUCT_DECISION
FORCED_LOGOUT_REQUIRED                     = LIKELY_YES（从 User.disable() 隐含）
```

### 认证裁决

```text
SELECTED_AUTHENTICATION_MODE               = SERVER_SESSION_WITH_SECURE_HTTPONLY_COOKIE
REJECTED_AUTHENTICATION_MODES              = BEARER_JWT_IN_LOCALSTORAGE
DEFERRED_AUTHENTICATION_MODES              = SHORT_LIVED_ACCESS_TOKEN_WITH_REFRESH_TOKEN
SELECTION_REASON                           = Spring Session JDBC 已配置，零新依赖，立即可撤销，安全 Cookie
SECURITY_TRADEOFFS                         = 需要 CSRF 重新启用；数据库会话空间；多实例需要粘性会话
```

### 认证主体

```text
PRINCIPAL_TYPE                             = CampusGuinnessUserDetails (implements UserDetails)
PRINCIPAL_FIELDS                           = userId, username, accountStatus, platformRole, authorities
ACTOR_ID_MAPPING                           = ((CampusGuinnessUserDetails) auth.getPrincipal()).getUserId()
SECURITY_CONTEXT_ACCESS_LOCATION           = CurrentActor 抽象（基础设施层）
CURRENT_ACTOR_ABSTRACTION_REQUIRED         = YES
```

### User 状态与密码

```text
LOGIN_ALLOWED_USER_STATUSES                = NORMAL
LOGIN_DENIED_USER_STATUSES                 = PENDING_ACTIVATION, LOCKED, DISABLED
ACTIVE_SESSION_REVOCATION_ON_DISABLE       = YES
REENABLE_LOGIN_BEHAVIOR                    = 用户必须重新登录
CURRENT_PASSWORD_STORAGE                   = users.password_hash varchar(255) NOT NULL（未使用，映射器缺陷）
SELECTED_PASSWORD_ENCODER                   = BCrypt (strength=12)
PASSWORD_POLICY                            = 8-72 字符
LOGIN_FAILURE_POLICY                       = 5 次失败 → 10 分钟锁定
PASSWORD_CHANGE_SUPPORT                    = YES
PASSWORD_RESET_SUPPORT                     = DEFERRED
```

### 角色模型

```text
PLATFORM_ROLE_MODEL                        = SUPER_ADMIN（或 null）——来自 users.platform_role
SCHOOL_ROLE_MODEL                          = SCHOOL_ADMIN, TEACHER, STUDENT——来自 school_memberships.role_in_school
STRING_TO_AUTHORITY_VALIDATION             = 严格白名单——未知值被拒绝并记录
UNKNOWN_ROLE_BEHAVIOR                      = 拒绝（无 authority 被授予）+ WARN 日志
ENUM_MIGRATION_REQUIRED                    = DEFERRED（拆分为 TASK-AUTH-ROLE-MAPPING-001）
```

### 资源授权

```text
AUTHORIZATION_SERVICE_BOUNDARY            = 应用层
RESOURCE_SCHOOL_RESOLUTION                = 每种资源类型暴露 schoolId
SUPER_ADMIN_BYPASS                        = YES
SCHOOL_ADMIN_RULE                         = 本校任何申诉
TEACHER_RULE                              = 本校任何申诉（未来可细化）
STUDENT_RULE                              = 禁止
SELF_CORRECTION_RULE                      = 禁止（actorId == studentId → 403）
RESOURCE_NOT_FOUND_DISCLOSURE_POLICY      = 未认证 → 401；已认证无访问权限 → 404
```

### SecurityConfig

```text
PUBLIC_ENDPOINTS                           = /actuator/health, /actuator/info, /api/v1/auth/login, /api/v1/auth/renew
AUTHENTICATED_ENDPOINTS                   = /api/v1/auth/**, 公共查询 GET 端点
ROLE_PROTECTED_ENDPOINTS                  = 管理命令（例如 correct-and-resolve）
FINAL_FALLBACK_RULE                        = .anyRequest().denyAll()（保留）
METHOD_SECURITY_REQUIRED                   = YES（@EnableMethodSecurity + @PreAuthorize）
```

### CSRF 与 CORS

```text
CSRF_POLICY                               = 已启用，CookieCsrfTokenRepository.withHttpOnlyFalse()
CSRF_TOKEN_DELIVERY                       = Cookie（非 HttpOnly）→ SPA 读取 → X-CSRF-TOKEN 头
COOKIE_POLICY                             = SESSION: Secure+HttpOnly+SameSite=Lax; CSRF: Secure+!HttpOnly+SameSite=Lax
CORS_POLICY                               = 显式白名单（不允许通配符）
ALLOWED_ORIGINS_SOURCE                    = 来自 application.yml 的 ${CORS_ORIGINS}
```

### 401/403

```text
AUTHENTICATION_ENTRY_POINT_REQUIRED       = YES（自定义 → 401 + 统一 JSON 错误）
ACCESS_DENIED_HANDLER_REQUIRED            = YES（自定义 → 403 + 统一 JSON 错误）
GLOBAL_EXCEPTION_HANDLER_AUTH_SCOPE       = 补充性（MVC 后异常）
SECURITY_ERROR_RESPONSE_SCHEMA            = {"code":"UNAUTHORIZED"|"ACCESS_DENIED","message":"...","traceId":"..."}
```

### 认证接口

```text
LOGIN_ENDPOINT                             = POST /api/v1/auth/login
CURRENT_USER_ENDPOINT                      = GET /api/v1/auth/me
LOGOUT_ENDPOINT                            = POST /api/v1/auth/logout
REFRESH_OR_SESSION_RENEW_ENDPOINT          = POST /api/v1/auth/renew
CHANGE_PASSWORD_ENDPOINT                   = POST /api/v1/auth/change-password
```

### 会话

```text
AUTH_SESSION_LIFETIME                      = 8 小时绝对
IDLE_TIMEOUT                               = 30 分钟
ABSOLUTE_TIMEOUT                           = 8 小时
REVOCATION_MECHANISM                       = 退出、禁用、管理员强制下线、不活动
PASSWORD_CHANGE_SESSION_POLICY             = 初始：仅当前会话。增强：使其他会话失效。
USER_DISABLE_SESSION_POLICY                = 立即删除 spring_session 中的所有用户会话
```

### 审计

```text
AUTH_AUDIT_EVENTS                         = 12 个事件已定义
SENSITIVE_LOG_FIELDS_EXCLUDED             = 密码、会话 ID、令牌、Cookie、哈希
AUTH_AUDIT_ATOMICITY                      = AFTER_COMMIT 异步——与业务事务分离
```

### 测试

```text
REQUIRED_UNIT_TESTS                        = 28+（UserDetailsService、CurrentActor、PasswordEncoder、Roles、Session）
REQUIRED_SECURITY_HTTP_ITS                = 18+（登录、退出、me、change-password、CSRF、Authorization）
REQUIRED_POSTGRESQL_ITS                   = 9+（会话持久化、失败计数、锁定、审计）
REQUIRED_CONCURRENCY_TESTS                = 4+（并发登录、密码修改竞争、强制下线）
```

### 变化总结

```text
DATABASE_MIGRATION_REQUIRED                = NO
DEPENDENCY_CHANGES_REQUIRED               = NO
```

### 产品决策

```text
PRODUCT_DECISIONS_REQUIRED                =
  1. 前端 URL / 源（必须 → CORS 白名单）
  2. 仅限第一方 SPA 还是也需要移动端/第三方？（影响 Bearer 令牌增强计划）
  3. 多实例部署？（影响会话存储架构）
  4. 允许多设备登录？（影响最大会话数）
  5. 教师是全校申诉访问还是项目级别访问？
```

### 阻塞项

```text
BLOCKERS                                  =
  UserPersistenceMapper 缺陷（不设置 passwordHash）——必须在 TASK-AUTH-FOUNDATION-001 中修复
  UserRepository 端口缺少 findByUsername ——必须添加到端口以进行登录
  SchoolMembership 加载延迟——授权需要成员资格查询
  SecurityConfig denyAll 阻止所有现有的 13 个控制器——端点必须逐个开放

  无架构阻塞项——所有发现均可通过拆分的实施任务解决。
```

### 风险

```text
RISKS                                     =
  1. 密码哈希映射器缺陷可能导致现有用户数据损坏（如果 save() 在修复前被调用）
  2. CSRF 重新启用可能破坏 SPA 集成（需要前端更改）
  3. 13 个控制器中的临时 actorId 字段——每个都必须迁移
  4. 多实例会话存储——当前 JDBC 存储需要粘性会话
```

### 实施任务拆分

```text
RECOMMENDED_IMPLEMENTATION_SPLIT           =
  第 1 阶段（核心认证）：3 个任务
  ├── TASK-AUTH-FOUNDATION-001：UserDetailsService + PasswordEncoder + CurrentActor
  ├── TASK-AUTH-FOUNDATION-002：SecurityConfig（CSRF、CORS、denyAll 保留）
  └── TASK-AUTH-FOUNDATION-003：AuthController + 会话生命周期 + 401/403 处理程序

  第 2 阶段（授权）：2 个任务
  ├── TASK-AUTH-ROLE-MAPPING-001：角色白名单 + 成员资格加载
  └── TASK-PATH-A-AUTHORIZATION-001：Path A 特定授权

  第 3 阶段（验证 + 审计）：2 个任务
  ├── TASK-PATH-A-CONCURRENCY-VERIFY-001：并发修正测试
  └── TASK-SCORE-CORRECTION-AUDIT-001：成绩修正审计

  第 4 阶段（HTTP 端点）：1 个任务
  └── TASK-PATH-A-ENDPOINT-001：Path A HTTP 端点（从 path-a-http-exposure-plan.md）

RECOMMENDED_IMPLEMENTATION_ORDER           =
  TASK-AUTH-FOUNDATION-001 → TASK-AUTH-FOUNDATION-002 → TASK-AUTH-FOUNDATION-003 →
  TASK-AUTH-ROLE-MAPPING-001 → TASK-PATH-A-AUTHORIZATION-001 →
  TASK-PATH-A-CONCURRENCY-VERIFY-001（并行）+ TASK-SCORE-CORRECTION-AUDIT-001（并行）→
  TASK-PATH-A-ENDPOINT-001
```

### 下一任务

```text
RECOMMENDED_NEXT_TASK                       =
  TASK-AUTH-FOUNDATION-001（AUTHENTICATION FOUNDATION IMPLEMENTATION）

  范围：实现核心认证基础：
  1. CampusGuinnessUserDetails（认证主体）
  2. CampusGuinnessUserDetailsService（UserDetailsService 实现）
  3. BCryptPasswordEncoder Bean
  4. CurrentActor 接口 + HttpServletRequest 实现
  5. 修复 UserPersistenceMapper 保留 passwordHash/loginFailures/lockedUntil
  6. 使用 findByUsername 扩展 UserRepository 端口
  7. 通过 findByUsername + 成员资格查询增强 UserRepositoryAdapter

  前置条件：TASK-AUTH-001 已批准
  估计工作量：1 个实施会话
  验证：mvnw clean verify 通过，所有新测试通过

ALLOW_RECOMMENDED_NEXT_TASK                 = NO
ALLOW_PRODUCTION_CODE_CHANGES              = NO
ALLOW_TEST_CODE_CHANGES                    = NO
ALLOW_DATABASE_MIGRATION                   = NO
ALLOW_DEPENDENCY_CHANGES                   = NO
ALLOW_GIT_COMMIT                           = NO

STATUS = AWAITING_EXPLICIT_APPROVAL
```

---

**审计完成。未修改生产代码、测试、迁移或依赖。** 等待 `TASK-AUTH-FOUNDATION-001` 或后续步骤的明确批准。
