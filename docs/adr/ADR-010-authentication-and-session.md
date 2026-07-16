# ADR-010: 认证与会话管理

> **状态**: Accepted
> **日期**: 2026-07-14

---

## 背景

系统是浏览器管理后台，需要账号锁定/停用即时生效、学校暂停即时撤权和超管治理上下文切换。

## FACT

- R12: 账号密码登录(V1)
- R13: 账号锁定/停用即时生效；学校暂停即时权限撤销
- R3: 学校数据隔离，服务端确定school上下文
- V1不实现外部登录(P1)

## 候选方案

### 方案A: Spring Security + Spring Session JDBC + HttpOnly Secure Cookie (推荐)
### 方案B: JWT access + refresh token

---

## TECH_DECISION

**选择方案A: 服务端Session + HttpOnly Secure SameSite Cookie + PostgreSQL存储。**

| 项目 | 决定 |
|------|------|
| 认证框架 | Spring Security |
| Session存储 | Spring Session JDBC → PostgreSQL |
| 凭证 | HttpOnly Secure SameSite Cookie |
| CSRF | Spring Security CSRF防护 |
| 密码 | BCrypt或Argon2哈希 |

## 三层安全责任

### 1. 每次请求认证
Spring Security从服务端Session恢复认证信息。

### 2. 敏感操作实时复核
以下操作不得只依赖Session创建时的状态，必须从权威数据库重新检查:
- 审批(活动/成绩/素材/成果)
- 发布(活动/排行榜/成果)
- 成绩审核和更正
- 申诉处理
- L3排行榜生成
- 权限管理
- 学校状态管理
- 超管进入学校治理上下文
- 文件下载和公开操作

复核字段: account_status, school_status, 当前角色, school归属, 权限范围。

### 3. 主动会话失效
以下事件触发相关Session失效:
- 账号停用/锁定/密码重置/权限撤销
- 学校暂停/停用
- 严重安全事件
- 管理员主动强制退出

Session索引与user_id关联，支持按user_id批量失效。学校事件支持按school_id批量失效。失效失败时，敏感请求实时数据库复核仍必须默认拒绝。Session失效是加速撤权机制，数据库状态复核才是最终安全边界。

## school上下文

1. Cookie和Session ID只标识会话，不保存school_id/角色/权限
2. 当前user_id来自服务端认证上下文
3. 当前用户所属school和权限范围来自服务端权威数据
4. 请求中的school_id只能是目标资源标识，不是权限事实
5. 服务端必须验证目标资源school_id在用户权限范围内
6. 普通学校用户不得切换到其他学校上下文
7. 超管没有默认校内业务权限
8. 超管进入目标学校治理上下文必须显式指定、重新鉴权并审计
9. 超管不得借上下文切换执行仅校管/老师/学生的业务命令

## 拒绝方案

**方案B(JWT)**: 账号/学校状态即时生效需要黑名单或短expiry+实时校验。使用JWT后仍每次查询状态则失去无状态令牌的主要收益。浏览器管理系统Session更合适。JWT保留为未来移动端/API候选，不进入V1。
