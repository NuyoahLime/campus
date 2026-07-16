# 接口层规划

> **版本**: v1.0 | **日期**: 2026-07-16 | **状态**: PLANNING_COMPLETED

---

## 1. 应用用例总清单 (45 use cases across 13 services)

| # | 聚合 | 用例 | Service方法 | 类型 | REST决策 |
|:-:|------|------|------|:--:|:--:|
| 1 | ChallengeProject | Create | `create(CreateChallengeProjectCommand)` | 修改 | PUBLIC |
| 2 | ChallengeProject | Find by ID | `findById(UUID)` | 查询 | PUBLIC |
| 3 | ChallengeProject | Publish | `publish(UUID)` | 修改 | PUBLIC |
| 4 | SchoolRegistration | Submit | `submit(SubmitSchoolRegistrationCommand)` | 修改 | PUBLIC |
| 5 | SchoolRegistration | Approve | `approve(UUID, UUID, String, UUID)` | 修改 | PUBLIC |
| 6 | SchoolRegistration | Reject | `reject(UUID, UUID, String)` | 修改 | PUBLIC |
| 7 | SchoolRegistration | Withdraw | `withdraw(UUID)` | 修改 | PUBLIC |
| 8 | School | Create | `create(...)` | 修改 | INTERNAL |
| 9 | School | Activate | `activate(UUID)` | 修改 | PUBLIC |
| 10 | School | Disable | `disable(UUID, String)` | 修改 | PUBLIC |
| 11 | School | Find by ID | `findById(UUID)` | 查询 | PUBLIC |
| 12 | ActivityApplication | Submit | `submit(SubmitActivityApplicationCommand)` | 修改 | PUBLIC |
| 13 | ActivityApplication | Approve | `approve(UUID, UUID, UUID)` | 修改 | PUBLIC |
| 14 | ActivityApplication | Reject | `reject(UUID, UUID, String)` | 修改 | PUBLIC |
| 15 | ActivityApplication | Withdraw | `withdraw(UUID)` | 修改 | PUBLIC |
| 16 | Activity | Create | `create(CreateActivityCommand)` | 修改 | PUBLIC |
| 17 | Activity | Publish | `publish(UUID)` | 修改 | PUBLIC |
| 18 | ScoreAttempt | Submit | `submit(SubmitScoreCommand)` | 修改 | PUBLIC |
| 19 | ActivityResult | Create | `create(UUID, UUID)` | 修改 | INTERNAL |
| 20 | ActivityResult | PublishInternal | `publishInternal(UUID)` | 修改 | PUBLIC |
| 21 | Media | Register | `register(RegisterMediaCommand)` | 修改 | PUBLIC |
| 22 | Media | SubmitInternalReview | `submitForInternalReview(UUID)` | 修改 | PUBLIC |
| 23 | Media | ApproveInternal | `approveInternal(UUID)` | 修改 | PUBLIC |
| 24 | RankingDefinition | Create | `create(Layer, String, UUID, UUID, UUID)` | 修改 | PUBLIC |
| 25 | RankingDefinition | Enable | `enable(UUID)` | 修改 | PUBLIC |
| 26 | RankingDefinition | Disable | `disable(UUID)` | 修改 | PUBLIC |
| 27 | L3Authorization | Submit | `submit(UUID, UUID, UUID)` | 修改 | PUBLIC |
| 28 | L3Authorization | Approve | `approve(UUID, UUID, String)` | 修改 | PUBLIC |
| 29 | L3Authorization | Withdraw | `withdraw(UUID, String)` | 修改 | PUBLIC |
| 30 | ScoreAppeal | Submit | `submit(UUID, UUID, UUID, String, String)` | 修改 | PUBLIC |
| 31 | ScoreAppeal | BeginProcessing | `beginProcessing(UUID, UUID)` | 修改 | PUBLIC |
| 32 | ScoreAppeal | Reject | `reject(UUID, String)` | 修改 | PUBLIC |
| 33 | ScoreAppeal | Withdraw | `withdraw(UUID)` | 修改 | PUBLIC |
| 34 | ScoreAppeal | Resolve | `resolve(UUID, String)` | 修改 | PUBLIC |
| 35 | Feedback | Submit | `submit(UUID, UUID, String, String)` | 修改 | PUBLIC |
| 36 | Feedback | BeginProcessing | `beginProcessing(UUID, UUID)` | 修改 | PUBLIC |
| 37 | Feedback | Resolve | `resolve(UUID, String)` | 修改 | PUBLIC |
| 38 | Feedback | Close | `close(UUID, String)` | 修改 | PUBLIC |
| 39 | User | Create | `create(String)` | 修改 | PUBLIC |
| 40 | User | Activate | `activate(UUID)` | 修改 | PUBLIC |
| 41 | User | Disable | `disable(UUID)` | 修改 | PUBLIC |
| 42 | User | ReEnable | `reEnable(UUID)` | 修改 | PUBLIC |
| 43 | ChallengeProject | Find by ID | `findById` | 查询 | PUBLIC |
| 44 | School | Find by ID | `findById` | 查询 | PUBLIC |
| 45 | ActivityResult | Create | `create` | 修改 | INTERNAL |

**Decision summary:**
- PUBLIC: 42 use cases
- INTERNAL: 3 use cases (School.create, ActivityResult.create — triggered by other use case flows)

---

## 2. Controller划分 (10 controllers)

| Controller | 聚合 | 公开用例数 | 职责 |
|------|------|:--:|------|
| ChallengeProjectController | ChallengeProject | 3 | CRUD + publish |
| SchoolRegistrationController | SchoolRegistration | 4 | Registration workflow |
| SchoolController | School | 2 | Activate/disable (create is internal) |
| ActivityApplicationController | ActivityApplication | 4 | Application workflow |
| ActivityController | Activity | 2 | Create + publish |
| ScoreAttemptController | ScoreAttempt | 1 | Submit score |
| ActivityResultController | ActivityResult | 1 | Publish result |
| MediaController | Media | 3 | Register + review |
| RankingController | RankingDef + L3Auth | 6 | Definitions + authorizations |
| AppealController | ScoreAppeal | 5 | Appeal lifecycle |
| FeedbackController | Feedback | 4 | Feedback lifecycle |
| UserController | User | 4 | Account management |

SchoolRegistration + School are separate controllers (different lifecycle concerns).
RankingDefinition + L3Authorization are merged into RankingController (same module, closely related).

---

## 3. URL设计

**Base path:** `/api/v1`

| 用例 | Method | URL | Status |
|------|:--:|------|:--:|
| Create Project | POST | `/api/v1/projects` | 201 |
| Get Project | GET | `/api/v1/projects/{id}` | 200 |
| Publish Project | POST | `/api/v1/projects/{id}/publish` | 200 |
| Submit Registration | POST | `/api/v1/school-registrations` | 201 |
| Approve Registration | POST | `/api/v1/school-registrations/{id}/approve` | 200 |
| Reject Registration | POST | `/api/v1/school-registrations/{id}/reject` | 200 |
| Withdraw Registration | POST | `/api/v1/school-registrations/{id}/withdraw` | 200 |
| Activate School | POST | `/api/v1/schools/{id}/activate` | 200 |
| Disable School | POST | `/api/v1/schools/{id}/disable` | 200 |
| Get School | GET | `/api/v1/schools/{id}` | 200 |
| Submit Activity App | POST | `/api/v1/activity-applications` | 201 |
| Approve Activity App | POST | `/api/v1/activity-applications/{id}/approve` | 200 |
| Reject Activity App | POST | `/api/v1/activity-applications/{id}/reject` | 200 |
| Withdraw Activity App | POST | `/api/v1/activity-applications/{id}/withdraw` | 200 |
| Create Activity | POST | `/api/v1/activities` | 201 |
| Publish Activity | POST | `/api/v1/activities/{id}/publish` | 200 |
| Submit Score | POST | `/api/v1/score-attempts` | 201 |
| Publish Result | POST | `/api/v1/activity-results/{id}/publish` | 200 |
| Register Media | POST | `/api/v1/media` | 201 |
| Submit Internal Review | POST | `/api/v1/media/{id}/internal-review` | 200 |
| Approve Internal | POST | `/api/v1/media/{id}/internal-approve` | 200 |
| Create Ranking Def | POST | `/api/v1/ranking-definitions` | 201 |
| Enable Ranking Def | POST | `/api/v1/ranking-definitions/{id}/enable` | 200 |
| Disable Ranking Def | POST | `/api/v1/ranking-definitions/{id}/disable` | 200 |
| Submit L3 Auth | POST | `/api/v1/l3-authorizations` | 201 |
| Approve L3 Auth | POST | `/api/v1/l3-authorizations/{id}/approve` | 200 |
| Withdraw L3 Auth | POST | `/api/v1/l3-authorizations/{id}/withdraw` | 200 |
| Submit Appeal | POST | `/api/v1/score-appeals` | 201 |
| Begin Processing | POST | `/api/v1/score-appeals/{id}/processing` | 200 |
| Reject Appeal | POST | `/api/v1/score-appeals/{id}/reject` | 200 |
| Withdraw Appeal | POST | `/api/v1/score-appeals/{id}/withdraw` | 200 |
| Resolve Appeal | POST | `/api/v1/score-appeals/{id}/resolve` | 200 |
| Submit Feedback | POST | `/api/v1/feedbacks` | 201 |
| Begin Processing | POST | `/api/v1/feedbacks/{id}/processing` | 200 |
| Resolve Feedback | POST | `/api/v1/feedbacks/{id}/resolve` | 200 |
| Close Feedback | POST | `/api/v1/feedbacks/{id}/close` | 200 |
| Create User | POST | `/api/v1/users` | 201 |
| Activate User | POST | `/api/v1/users/{id}/activate` | 200 |
| Disable User | POST | `/api/v1/users/{id}/disable` | 200 |
| ReEnable User | POST | `/api/v1/users/{id}/re-enable` | 200 |

---

## 4. 异常响应契约

```json
{
  "code": "SCORE_APPEAL_NOT_FOUND",
  "message": "Score appeal was not found",
  "traceId": "..."
}
```

| 异常类别 | HTTP | 错误码示例 |
|------|:--:|------|
| 参数格式/校验 | 400 | `VALIDATION_ERROR` |
| 资源不存在 | 404 | `{MODULE}_NOT_FOUND` |
| 领域状态冲突 | 409 | `{MODULE}_STATE_CONFLICT` |
| 唯一性冲突 | 409 | `{MODULE}_DUPLICATE` |
| 持久化损坏 | 500 | `INTERNAL_DATA_CORRUPTION` |
| 未知异常 | 500 | `INTERNAL_ERROR` |

- 领域非法状态转换 → 409 Conflict
- Bean Validation → 400 Bad Request
- Persistence Mapping异常不得返回404
- 异常响应不得泄漏堆栈、SQL或Entity字段

---

## 5. 安全占位边界

| 未来主体 | 数量 | 当前处理 |
|------|:--:|------|
| PUBLIC | 0 | — |
| AUTHENTICATED_USER | 20 | TEMPORARY_EXPLICIT_ACTOR_ID |
| SCHOOL_OPERATOR | 14 | TEMPORARY_EXPLICIT_ACTOR_ID |
| PLATFORM_OPERATOR | 4 | TEMPORARY_EXPLICIT_ACTOR_ID |
| INTERNAL_SYSTEM | 3 | INTERNAL_ONLY |

**临时契约**: 涉及操作者ID的接口使用显式 `actorId` 字段，待认证实现后从安全上下文获取。

---

## 6. 实施批次

| 批次 | 范围 | Controller | 公开用例 | 风险 |
|:--:|------|------|:--:|------|
| BATCH-01 | 基础契约 | ChallengeProject, School, SchoolRegistration | 9 | 低 — 简单状态机 |
| BATCH-02 | 活动域 | ActivityApplication, Activity | 6 | 中 — 双状态机 |
| BATCH-03 | 成绩域 | ScoreAttempt, ActivityResult | 2 | 中 — ScoreValue序列化 |
| BATCH-04 | 媒体域 | Media | 3 | 低 |
| BATCH-05 | 排名域 | RankingDefinition, L3Authorization | 6 | 中 — 授权边界 |
| BATCH-06 | 申诉+反馈 | ScoreAppeal, Feedback | 9 | 中 — ScoreAppeal 13状态 |
| BATCH-07 | 用户 | User | 4 | 高 — 认证缺失 |
| BATCH-08 | 最终门禁 | 架构测试、集成测试 | — | 低 |

BATCH-07 (User) 因认证缺失推迟到最后。

---

## 7. 风险清单

| 风险 | 严重级别 | 影响接口 | 阻塞？ |
|------|:--:|------|:--:|
| 认证授权缺失 | HIGH | 全部 | 否 — 临时显式actorId |
| 列表查询能力缺失 | MEDIUM | GET列表类 | 是 — 需先补充应用层查询 |
| 分页能力缺失 | MEDIUM | GET列表类 | 是 |
| 幂等机制缺失 | MEDIUM | POST创建类 | 否 — DB约束兜底 |
| 乐观锁缺失 | LOW | PUT/PATCH类 | 否 — JPA @Version存在 |
| 领域事件未投递 | MEDIUM | 全部 | 否 — 后续批次 |
| 文件上传未实现 | MEDIUM | Media | 是 — Media.register需文件元数据 |
| 通知未实现 | LOW | 无直接接口 | 否 |
| 排名计算未实现 | MEDIUM | 排行榜查询 | 是 — 延期 |

---

## 8. 未创建生产代码

```text
PRODUCTION_CODE_ADDED = 0
CONTROLLERS_ADDED = 0
DTOS_ADDED = 0
MIGRATIONS_ADDED = 0
```

---

## 9. 测试策略

- Controller单元测试: `@WebMvcTest` + MockMvc, Mock Application Service
- DTO序列化测试: Jackson round-trip
- 集成冒烟测试: 1-2个 `@SpringBootTest` per batch
- 异常响应测试: 验证错误码、状态码、无敏感信息泄露
