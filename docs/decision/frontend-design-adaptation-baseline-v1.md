# Frontend Design Adaptation Baseline v1

> Status: Stage 13 design freeze
> Baseline: `8da48bdd4cabe43b6096f8c3eeee537901533c81`
> Scope: design interpretation and implementation planning only

This document defines how repository design evidence may be used after Stage 13. The
screens are design and information-architecture inputs, not authority for identity,
authorization, state transitions, or API behavior.

## 1. Source Assets

The audit inspected these sources:

- `docs/source/需求分析 .pdf`: the 24-page source PDF (the similarly named 34-byte
  file is not the complete source document).
- `docs/source/pages/page_01.jpg` through `page_24.jpg`: 24 canonical rendered pages.
- Matching PNG renders and the images under `docs/source/embedded/`: duplicate/high
  resolution evidence, not separate designs.
- `docs/需求规格化分析报告.md`: historical screenshot analysis and page index.
- `docs/design/`: implementation plans, not visual authority by themselves.
- Current accepted frontend under `frontend/src`.

`IMAGE_VISUAL_INSPECTION_NOT_AVAILABLE` does not apply. All 24 canonical JPG pages
were visually inspected in six contact sheets; UI pages were also inspected at their
source resolution where necessary.

## 2. Authority Priority

When sources conflict, use this order:

1. Current decision documents, especially
   `docs/decision/current-identity-authorization-baseline-v1.3.md`.
2. Frozen specifications under `docs/business-spec/`.
3. Accepted domain state machines, application services, authorization contracts, API
   contracts, tests, and UAT evidence.
4. Business descriptions in the original requirements PDF.
5. Visual language and information architecture in the original design images.

Higher levels override lower levels. A design image never creates a role, endpoint,
state transition, authority, or data-scope rule.

## 3. Design Page Mapping

Allowed frontend status values are `IMPLEMENTED`, `PARTIAL`, `SHELL_ONLY`, `MISSING`,
and `OBSOLETE`. Allowed backend support values are `READY`, `PARTIAL`, `DOMAIN_ONLY`,
`API_ONLY`, `MISSING`, and `DEFERRED`.

| Design page | Original page name | Current formal role | Original role | Current route | Frontend status | Backend support | Business spec | Reuse status | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 | PRD overview | N/A | N/A | N/A | `OBSOLETE` | `DEFERRED` | All | `DO_NOT_USE` | Source narrative, not a UI screen |
| 02 | Goals and page scope | N/A | Visitor/Student/Teacher/Admin | N/A | `OBSOLETE` | `DEFERRED` | All | `DO_NOT_USE` | Historical scope evidence only |
| 03 | Historical role descriptions | Three-role baseline | Five-role model | N/A | `OBSOLETE` | `DEFERRED` | Identity baseline | `OBSOLETE_BUSINESS` | Teacher is not a formal identity |
| 04 | Historical permission matrix | Three-role baseline | Five-role model | N/A | `OBSOLETE` | `DEFERRED` | Identity baseline | `OBSOLETE_BUSINESS` | Never use as current authorization matrix |
| 05 | Home | Anonymous/all | Visitor | Missing | `MISSING` | `PARTIAL` | 03, 04, 06, 08 | `ADAPT_REQUIRED` | Useful public IA; screenshot contains unimplemented content |
| 06 | Challenge project library | Anonymous/all | All | Missing | `MISSING` | `PARTIAL` | 03 | `ADAPT_REQUIRED` | Public backend list/detail is partial |
| 07 | Challenge project detail | Anonymous/all | All | Missing | `MISSING` | `PARTIAL` | 03 | `ADAPT_REQUIRED` | Reuse detail hierarchy, not fake metadata |
| 08 | School activity display | Anonymous/all | All | Missing | `MISSING` | `PARTIAL` | 04, 08 | `ADAPT_REQUIRED` | Public activity list exists; detail/result display does not |
| 09 | Login | Anonymous | Student/Teacher/Admin | `/login` | `IMPLEMENTED` | `READY` | 01 | `DIRECT_REFERENCE` | Current login is the accepted visual anchor |
| 10 | Student personal center | `STUDENT` | Student | `/student` | `SHELL_ONLY` | `PARTIAL` | 01, 05, 06, 07, 09 | `ADAPT_REQUIRED` | Current shell has identity only; no fake KPI |
| 11 | Student scores and ranking | `STUDENT` | Student | Missing | `MISSING` | `PARTIAL` | 05, 06 | `ADAPT_REQUIRED` | Score/ranking read product is incomplete |
| 12 | Student feedback and appeal | `STUDENT` | Student | Missing | `MISSING` | `PARTIAL` | 07, 09 | `ADAPT_REQUIRED` | Backend mutations exist; queries/UI absent |
| 13 | Teacher workspace | None | Teacher | None | `OBSOLETE` | `DEFERRED` | Identity baseline | `OBSOLETE_BUSINESS` | `DEFERRED_BY_CURRENT_IDENTITY_MODEL` |
| 14 | Teacher score entry | None | Teacher | None | `OBSOLETE` | `DEFERRED` | 05 | `OBSOLETE_BUSINESS` | Operator assignment requires product decision |
| 15 | Teacher media and feedback | None | Teacher | None | `OBSOLETE` | `DEFERRED` | 08, 09 | `OBSOLETE_BUSINESS` | Do not transfer wholesale to SCHOOL_ADMIN |
| 16 | School-admin workspace | `SCHOOL_ADMIN` | School Admin | `/school-admin` | `SHELL_ONLY` | `PARTIAL` | 02, 04-09 | `ADAPT_REQUIRED` | Shared shell is implemented; business areas are mostly absent |
| 17 | School data and user management | `SCHOOL_ADMIN` | School Admin | Missing | `MISSING` | `PARTIAL` | 02 | `ADAPT_REQUIRED` | Must use explicit same-school contracts |
| 18 | Activity management | `SCHOOL_ADMIN` | School Admin | Missing | `MISSING` | `PARTIAL` | 04 | `ADAPT_REQUIRED` | Keep workflow layout; do not invent Teacher assignment |
| 19 | Score and ranking center | `SCHOOL_ADMIN` | School Admin | Missing | `MISSING` | `PARTIAL` | 05, 06 | `ADAPT_REQUIRED` | Score entry remains identity-deferred |
| 20 | Content and feedback center | `SCHOOL_ADMIN` | School Admin | Missing | `MISSING` | `PARTIAL` | 08, 09 | `ADAPT_REQUIRED` | Query and operator semantics are incomplete |
| 21 | Project resource management | `SUPER_ADMIN` | Super Admin | Missing | `MISSING` | `PARTIAL` | 03, 10 | `ADAPT_REQUIRED` | Target after backend project lifecycle closure |
| 22 | School and account management | `SUPER_ADMIN` | Super Admin | `/super-admin/school-registrations*` | `PARTIAL` | `PARTIAL` | 02, 10 | `ADAPT_REQUIRED` | Registration review exists; school/admin governance does not |
| 23 | Super-admin workspace | `SUPER_ADMIN` | Super Admin | `/super-admin` | `SHELL_ONLY` | `PARTIAL` | 10 | `ADAPT_REQUIRED` | Use real queues only; no screenshot KPI placeholders |
| 24 | Audit and feedback center | `SUPER_ADMIN` | Super Admin | Missing | `MISSING` | `PARTIAL` | 09, 10 | `ADAPT_REQUIRED` | Audit is write-only; feedback lacks query product |

Design pages mapped: 24. UI designs mapped to future/current product surfaces: 20.

## 4. Role Mapping

| Original role | Current interpretation | Rule |
| --- | --- | --- |
| Visitor | Anonymous user | Public routes only; no implicit account |
| Student | `STUDENT` | Self and same-school published data only |
| Teacher | No formal role | `DEFERRED_BY_CURRENT_IDENTITY_MODEL` |
| School Admin | `SCHOOL_ADMIN` | Same-school administration only |
| Super Admin | `SUPER_ADMIN` | Platform governance, not ordinary school mutation |

Teacher workspace, score entry, activity application, media upload, and teacher feedback
designs remain historical evidence. They cannot be reassigned to SCHOOL_ADMIN unless a
current business decision explicitly assigns the individual operation.

## 5. Current Route Mapping

Current frontend route count: 13.

| Route | Role | View | API | Current state | Design reference | Missing interaction | Backend dependency |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `/` | Anonymous/authenticated | `RoleUnavailableView` dispatcher | `/auth/me` | `IMPLEMENTED` | None | None | Auth session |
| `/login` | Anonymous | `LoginView` | login, me | `IMPLEMENTED` | page 09 | Password recovery | Auth |
| `/register` | Anonymous | `StudentRegistrationView` | schools, student register | `IMPLEMENTED` | None | None for accepted scope | Student identity |
| `/school-admin/activate` | Anonymous invited admin | `SchoolAdminActivationView` | school-admin activate | `IMPLEMENTED` | None | None for accepted scope | Invitation activation |
| `/student/application/rejected` | Rejected applicant | `StudentApplicationRejectedView` | none | `IMPLEMENTED` | None | Direct navigation to resubmit | Login state contract |
| `/student/application/resubmit` | Rejected applicant | `StudentApplicationResubmitView` | schools, resubmit | `IMPLEMENTED` | None | None for accepted scope | Student identity |
| `/school-admin/student-applications` | `SCHOOL_ADMIN` | `StudentIdentityReviewView` | pending list/detail/approve/reject | `IMPLEMENTED` | No direct reference | Search/history outside pending queue | Student review query |
| `/super-admin` | `SUPER_ADMIN` | `SuperAdminWorkspaceView` | me | `SHELL_ONLY` | page 23 | Real governance summary/queues | School/account/project queries |
| `/super-admin/school-registrations` | `SUPER_ADMIN` | `SuperAdminSchoolRegistrationListView` | registration list | `IMPLEMENTED` | page 22 (adapted) | Applicant-side intake not here | Registration query |
| `/super-admin/school-registrations/:id` | `SUPER_ADMIN` | `SuperAdminSchoolRegistrationDetailView` | detail/supplement/approve/reject | `IMPLEMENTED` | page 22 (adapted) | None for accepted review scope | Registration review |
| `/school-admin` | `SCHOOL_ADMIN` | `SchoolAdminWorkspaceView` | me | `SHELL_ONLY` | page 16 | School/activity/score/content product routes | Same-school queries |
| `/student` | `STUDENT` | `StudentWorkspaceView` | me | `SHELL_ONLY` | page 10 | Scores/ranking/appeal/feedback | Self read models |
| `/role-unavailable` | Authenticated unsupported identity | `RoleUnavailableView` | me | `IMPLEMENTED` | None | None | Login identity contract |

## 6. Existing UI Audit

### Key page alignment

| Current page | Assessment | Evidence |
| --- | --- | --- |
| Login | `DESIGN_ALIGNED` | Same split brand/form hierarchy, blue public identity and restrained card |
| Student registration | `NO_DIRECT_REFERENCE` | Uses accepted auth language; original designs have no formal self-registration page |
| SuperAdmin workspace | `PARTIALLY_ALIGNED` | Sidebar/topbar follow admin language; real dashboard content is not implemented |
| School registration list | `PARTIALLY_ALIGNED` | Dense admin table/card adaptation; original page 22 covers broader governance |
| School registration detail | `PARTIALLY_ALIGNED` | Operational detail/actions fit admin language; no direct original screen |
| SchoolAdmin workspace | `PARTIALLY_ALIGNED` | Shared admin shell matches page 16 structure; most modules are placeholders |
| Student review | `NO_DIRECT_REFERENCE` | Product-specific queue built from current contract, not an original design |
| Student workspace | `PARTIALLY_ALIGNED` | Identity shell uses page 10 hierarchy without fake scores/KPI |

Counts for these eight review pages:

```text
DESIGN_ALIGNED: 1
PARTIALLY_ALIGNED: 5
NOT_ALIGNED: 0
NO_DIRECT_REFERENCE: 2
```

### Visual system disposition

| Area | Decision | Baseline |
| --- | --- | --- |
| Color | `KEEP` | Existing blue primary plus distinct success/warning/error; do not create a blue-only UI |
| Background | `ADAPT` | Use neutral `#f5f7fa` for workspaces and white content surfaces |
| Typography | `KEEP` | Compact operational hierarchy; no viewport-scaled display type |
| Buttons | `ADAPT` | Normalize command buttons to 40-42px; auth primary may remain 50px |
| Tables | `KEEP` | Dense headers, restrained row hover, card fallback on narrow screens |
| Forms | `ADAPT` | Keep 50px auth controls; use 40-44px operational controls |
| Dialog | `KEEP` | 560px default, 680px detail, bounded viewport height |
| Sidebar | `KEEP` | 248px shared management navigation |
| Topbar | `KEEP` | 72px account/context bar inside authenticated workspaces |
| Cards | `ADAPT` | Use only for records, dialogs, and bounded tools; avoid dashboard card filler |
| Loading | `KEEP` | Stable dimensions and inline spinner/status text |
| Empty | `KEEP` | Plain actionable state; no decorative illustration required |
| Error | `KEEP` | Inline business-safe message with retry where appropriate |
| 403 | `ADAPT` | Router redirects are correct; add a dedicated state only where resource semantics require it |
| 404 | `REPLACE` | No general frontend not-found route currently exists |
| Responsive | `KEEP` | 1100 table-to-card, 820 shell collapse, 520 compact stacking |

## 7. Visual Conflicts in Original Designs

Eight conflicts are frozen:

1. Five historical roles conflict with the current three-role identity model.
2. Teacher workspaces and operations cannot be treated as current product routes.
3. Public top navigation, student top navigation, and multiple admin sidebars are
   inconsistent; authenticated management must share one shell.
4. Several dashboards contain illustrative KPI values and charts unsupported by query
   APIs. They must not be copied as fake data.
5. Some original screens imply broad SUPER_ADMIN access to school-internal activity,
   score, media, and feedback operations, which conflicts with platform governance.
6. Historical school/user management implies generic Teacher/Student account CRUD,
   which conflicts with current invitation and student-application onboarding.
7. Original school activation presentation does not show the two-active-admin
   precondition or reason/audit requirements.
8. The screenshots use varying sidebar colors, decorative banners, rounded treatments,
   and density. They are references, not separate page-level design systems.

## 8. Unified Visual Principles

The target is a campus operations product: clear, dense, calm, and suitable for repeated
administrative work. It is not an AI SaaS dashboard and not a marketing site.

| Token/metric | Frozen recommendation | Source |
| --- | --- | --- |
| Primary | `#1769e8`; hover `#0d5bd1` | Current accepted frontend |
| Accent | `#f59e0b`, used sparingly | Current frontend and original multicolor categories |
| Neutral page | `#f5f7fa` | Current workspace |
| Surface | `#ffffff` | Current workspace/design images |
| Primary text | `#10233f` / operational `#202b3c` | Current frontend |
| Secondary text | `#5f718a` | Current tokens |
| Border | `#d9e3f0`; workspace `#dde3ea` | Current frontend |
| Status colors | success `#159a68`, warning `#d97706`, error `#dc3d4b`, info `#2563eb` | Current tokens |
| Radius | 6px compact, 8px controls/cards, 10px bounded panels | Current accepted workspace |
| Shadow | none or soft `0 5px 18px rgba(38,91,152,.08)` | Current tokens |
| Spacing | 4px base; common 8/12/16/20/24/32 | Existing CSS rhythm |
| Sidebar width | 248px | `workspace.css` |
| Topbar height | 72px | `workspace.css` |
| Content max width | 1180px | `workspace.css` |
| Table density | 48-56px rows, 12px headers, 13-14px cells | Existing admin tables |
| Button height | 40-42px operational; 50px auth primary | Existing forms/workspace |
| Input height | 40-44px operational; 50px auth | Existing forms |
| Modal width | 560px default; 680px detail | Existing review UI |
| Breakpoints | 1100px data-card switch, 820px shell collapse, 520px compact | Existing workspace |

No negative letter spacing, viewport-driven font scaling, glassmorphism, glow, neon,
large decorative gradients, giant corner radii, 3D icons, AI stars, or emoji navigation.

## 9. SuperAdmin Layout Decision

1. Keep the existing `WorkspaceShell` sidebar and topbar. The public TopNav from pages
   5-8 must not be used for platform management.
2. Organize School Governance as separate routes under one sidebar group:
   - school registrations;
   - school master data;
   - school-admin invitations/accounts;
   - school lifecycle/audit.
3. Use tabs only for facets of one resource detail. Do not place registrations, schools,
   and accounts into one giant tabbed page because they have different identifiers,
   queries, permissions, and lifecycle actions.
4. The workspace home may show real queue counts and recent governance events only after
   query APIs exist. Until then, keep the current identity/context shell. Never copy
   screenshot KPI values.

## 10. SchoolAdmin Layout Decision

- Keep the shared management sidebar/topbar and lock the active school context to the
  authenticated ACTIVE SCHOOL_ADMIN membership.
- Use separate routes for student identity, school profile, activities, scores/ranking,
  media/results, appeals/feedback, and account operations.
- Hide or mark unavailable any capability whose backend or identity contract is not
  complete. A disabled navigation label is acceptable; a clickable fake page is not.
- Do not introduce Teacher navigation or silently assign Teacher-only actions.

## 11. Student Layout Decision

- Preserve a lighter, self-service experience while reusing the same tokens and account
  topbar. A full admin-style dense sidebar is not required for a small number of routes.
- Add routes only when self-scoped read APIs exist: scores/ranking, appeal, feedback, and
  published activities.
- Never show school-wide controls or values inferred from another user.
- Use page 10-12 for information hierarchy, not as evidence that the backend exists.

## 12. Public Pages Layout Decision

- Public home, project library/detail, activity display, login, registration, and school
  registration may use a compact public top navigation distinct from management shell.
- Page 5-8 imagery and content density are references. Real records and media are
  required; do not ship fake projects, activities, rankings, or badges.
- The login page remains the current public visual anchor.

## 13. Component Reuse Strategy

| Existing asset | Decision |
| --- | --- |
| `WorkspaceShell` | Reuse and evolve incrementally; no rewrite in a feature stage |
| auth store and router role guards | Reuse as the only session/role dispatch path |
| `apiRequest` and CSRF handling | Reuse for every API; no JWT/localStorage fork |
| form styles | Reuse auth states; extract operational variants only when duplicated |
| workspace tables/cards | Reuse list/detail/loading/empty/error patterns |
| review dialogs | Reuse confirmation/reason/count/error behavior |
| tokens | Consolidate future CSS toward the frozen values; do not introduce a UI framework |

Add a shared component only when at least two real product views need the same behavior.
Do not build a speculative component catalog.

## 14. Responsive Baseline

- Desktop acceptance widths: 1440, 1366, and 1024.
- Mobile acceptance width: 375; use 812 as the standard UAT height.
- No horizontal page scroll, overlap, hidden command, or unstable control width.
- At 1100px, wide operational tables may become record cards.
- At 820px, the management sidebar becomes an in-flow navigation block.
- At 520px, navigation, account header, forms, details, and action groups stack.
- Fixed-format controls need stable min-height/width constraints so loading and errors do
  not move neighboring content.

## 15. Do / Don't

### Do

- Use current decisions/specifications before copying a design interaction.
- Reuse original information hierarchy, density, table/form placement, and workflow
  rhythm when the business capability is real.
- Show localized role/status labels in product UI.
- Use real API data and explicit loading/empty/error states.
- Keep school and self scope visible without allowing the user to forge it.

### Don't

- Restore `TEACHER` or `REGISTERED_USER`.
- Copy client-provided actor/reviewer/operator fields from historical designs.
- Add fake KPI, rankings, activities, messages, notifications, or account data.
- expose school activation before the two-admin semantic gap is fixed.
- Create a different navigation system for every screenshot.
- Add Element Plus, Ant Design, Vuetify, a new icon library, or a second auth model merely
  to match a mockup.
- Use gradients, glassmorphism, glow, giant radii, AI decoration, or emoji navigation.

## 16. Migration Strategy

1. Keep accepted auth and identity pages stable.
2. Finish high-value backend read models before creating navigation to them.
3. Add one authorized vertical slice per stage, using the current shared shell.
4. Adapt the corresponding design page only after mapping its business operations to
   current specs and APIs.
5. Run responsive browser UAT and authorization regression for every new route.
6. Consolidate tokens/components only when feature work creates proven duplication.

## Design to Implementation Matrix

| Design | Business function | Formal role | Current route | Current implementation | Decision | Target stage |
| --- | --- | --- | --- | --- | --- | --- |
| 05 | Public home | Anonymous/all | Missing | Missing | Adapt after real public queries | After Stage 16 |
| 06-07 | Project library/detail | Anonymous/all | Missing | Backend partial | Build/adapt | Stage 16 |
| 08 | Public activities/results | Anonymous/all | Missing | Backend partial | Adapt after API closure | Later |
| 09 | Login | Anonymous | `/login` | Complete | Reuse | Frozen |
| 10 | Student home | `STUDENT` | `/student` | Shell | Adapt incrementally | Later |
| 11 | Student score/ranking | `STUDENT` | Missing | Backend partial/deferred | Wait for operator/read decisions | Later |
| 12 | Student appeal/feedback | `STUDENT` | Missing | Backend partial | Build after queries | Later |
| 13-15 | Teacher workspace/functions | None | None | Deferred | Do not use as product routes | Product decision |
| 16 | School-admin home | `SCHOOL_ADMIN` | `/school-admin` | Shell | Reuse shell/adapt content | Later |
| 17 | School/user management | `SCHOOL_ADMIN` | Missing | Backend partial/legacy conflict | Re-scope before build | Later |
| 18 | Activity management | `SCHOOL_ADMIN` | Missing | Backend partial | Adapt after query closure | Later |
| 19 | Score/ranking management | `SCHOOL_ADMIN` | Missing | Backend partial/deferred | Wait for score decision | Later |
| 20 | Content/feedback | `SCHOOL_ADMIN` | Missing | Backend partial/deferred | Split into bounded routes | Later |
| 21 | Project management | `SUPER_ADMIN` | Missing | Backend partial | Build/adapt | Stage 16 |
| 22 | School/account management | `SUPER_ADMIN` | Registration routes only | Partial | Split into governance routes | Stage 14-15 |
| 23 | Platform workspace | `SUPER_ADMIN` | `/super-admin` | Shell | Real queues only | Stage 14+ |
| 24 | Platform audit/feedback | `SUPER_ADMIN` | Missing | Backend partial | Build after audit/query APIs | Later |

## Recommended Stage Sequence

### Stage 14

SUPER_ADMIN School Master Read plus School-Admin Account Read/Provisioning Foundation.
This includes real school/invitation/account queries and separate governance routes. It
does not expose unsafe school activation.

### Stage 15

School Lifecycle Governance. Implement the two-active-admin eligibility rule, reasoned
state transitions, audit records, concurrency protection, and then lifecycle UI.

### Stage 16

Challenge Project Resource Library. Complete project lifecycle/detail/resources, then
build public library/detail and SUPER_ADMIN project management from pages 6, 7, and 21.

Activity, Score, and Media must not jump ahead of unresolved identity decisions merely
because their historical design screens are visually complete.
