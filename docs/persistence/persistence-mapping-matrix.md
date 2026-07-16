# 持久化映射矩阵 v1.2

> **状态**: TASK-PERSISTENCE-001 进行中

## 聚合根Entity清单

| 表 | 模块 | Entity | Repository | 状态 |
|------|------|------|:--:|:--:|
| users | identity | UserEntity | UserRepository | ✅ |
| schools | school | SchoolEntity | SchoolRepository | ✅ |
| school_registrations | school | SchoolRegistrationEntity | SchoolRegistrationRepository | ✅ |
| school_memberships | identity | SchoolMembershipEntity | — (内部) | ✅ |
| challenge_projects | project | ChallengeProjectEntity | ChallengeProjectRepository | ✅ |
| activity_applications | activity | ActivityApplicationEntity | ActivityApplicationRepository | ⏳ |
| activities | activity | ActivityEntity | ActivityRepository | ✅ |
| score_attempts | score | ScoreAttemptEntity | ScoreAttemptRepository | ✅ |
| ranking_definitions | ranking | RankingDefinitionEntity | RankingDefinitionRepository | ⏳ |
| l3_authorizations | ranking | L3AuthorizationEntity | L3AuthorizationRepository | ⏳ |
| score_appeals | appeal | ScoreAppealEntity | ScoreAppealRepository | ⏳ |
| media | media | MediaEntity | MediaRepository | ⏳ |
| activity_results | result | ActivityResultEntity | ActivityResultRepository | ⏳ |
| feedbacks | feedback | FeedbackEntity | FeedbackRepository | ⏳ |
| notifications | notification | NotificationEntity | — (基础设施) | ⏳ |
| audit_records | audit | AuditRecordEntity | — (基础设施) | ⏳ |
| task_records | infrastructure | TaskRecordEntity | — (基础设施) | ⏳ |

**统计**: 17个聚合根Entity，13个需要Repository，4个基础设施实体不需要业务Repository。
