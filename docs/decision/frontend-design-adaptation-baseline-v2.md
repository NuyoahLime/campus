# Frontend Design Adaptation Baseline v2

> Stage 17 decision record
> Baseline: `4b83f8b5fab77bfb3aac6dbcb0ebd836267c83fe`
> Branch: `docs/stage17-design-capability-baseline-v2`
> Scope: design convergence and frontend structure only; no new business workflow

## 1. Authority And Role Baseline

The current product authority is defined by the current identity and authorization
baseline, then the current business specification, then this document. Historical
design images are evidence of information architecture and visual language, not
authority to restore old workflows.

The only current authenticated roles are `SUPER_ADMIN`, `SCHOOL_ADMIN`, and
`STUDENT`. The ordinary Teacher role has been removed from the current product model.
Pages 12-15 and any Teacher operation shown in the source images remain historical
evidence and are not current routes, permissions, or navigation. No Teacher duty is
silently reassigned to `SCHOOL_ADMIN`.

Priority:

1. Current identity/authorization baseline and approved business specifications
2. Real API contracts and accepted frontend flows
3. This shared design baseline
4. Historical design images

## 2. 24-Page Design Mapping

| Page | Original purpose | Current formal role | Current route | Status | Reuse / gap |
| --- | --- | --- | --- | --- | --- |
| 01 | Platform and challenge overview | Anonymous | None | OBSOLETE | Historical product context only |
| 02 | Historical users and scope | Anonymous and historical roles | None | OBSOLETE | Five-role model conflicts with current identity |
| 03 | Role definitions | Current three roles only | None | OBSOLETE | Teacher section is removed; do not implement |
| 04 | Historical permission matrix | Current authorization baseline | None | OBSOLETE | Use current endpoint and scope contracts |
| 05 | Public home | Anonymous | Missing | MISSING | Reuse public information hierarchy after real APIs exist |
| 06 | Project library | Anonymous | `/projects` | IMPLEMENTED | Current public project query and real records |
| 07 | Project detail | Anonymous | `/projects/:id` | IMPLEMENTED | Current public rule/detail contract |
| 08 | Public activities | Anonymous | Missing | MISSING | Wait for public activity query and real records |
| 09 | Login | Anonymous | `/login` | IMPLEMENTED | Accepted auth visual anchor |
| 10 | Student workspace | STUDENT | `/student` | SHELL_ONLY | Keep identity-only shell; no fake scores or activity data |
| 11 | Student scores/ranking | STUDENT | Missing | MISSING | Query and product decisions are incomplete |
| 12 | Student feedback/appeal | STUDENT | Missing | MISSING | Read models and frontend are incomplete |
| 13 | Teacher workspace | No current role | None | OBSOLETE | Removed role; do not create a replacement route |
| 14 | Teacher score entry | No current role | None | OBSOLETE | Score operator decision remains unresolved |
| 15 | Teacher materials/feedback | No current role | None | OBSOLETE | Media and feedback operator decisions remain unresolved |
| 16 | School-admin workspace | SCHOOL_ADMIN | `/school-admin` | SHELL_ONLY | Keep shared management shell and real identity context |
| 17 | School/user management | SCHOOL_ADMIN | Missing | MISSING | Historical account CRUD conflicts with current onboarding |
| 18 | Activity management | SCHOOL_ADMIN | Missing | MISSING | Backend is partial; no product route yet |
| 19 | Score/ranking management | SCHOOL_ADMIN | Missing | MISSING | Operator and query contracts are incomplete |
| 20 | Content/feedback center | SCHOOL_ADMIN | Missing | MISSING | Split into bounded routes after query closure |
| 21 | Project governance | SUPER_ADMIN | `/super-admin/projects*` | IMPLEMENTED | Stage 16 lifecycle and rule-version UI |
| 22 | School/account governance | SUPER_ADMIN | `/super-admin/schools*` and registrations | IMPLEMENTED | Stage 14-15 governance routes; no generic user CRUD |
| 23 | Platform workspace | SUPER_ADMIN | `/super-admin` | SHELL_ONLY | Identity shell only until real queue queries exist |
| 24 | Platform content review | SUPER_ADMIN | Missing | MISSING | Audit/query product does not exist |

## 3. Shared Shell Strategy

`PublicShell.vue` is the single public structure for public product routes. Stage 17
connects the project list and detail views to it. It owns the public brand, project
navigation, login entry, bounded page frame, mobile wrapping, and minimal footer.
Unavailable destinations are not shown as clickable fake links.

`WorkspaceShell.vue` remains the authenticated management foundation for
`SUPER_ADMIN` and `SCHOOL_ADMIN`. It is not rewritten to imitate every historical
sidebar. Student remains a lighter self-service shell and currently exposes only
the identity context already supported by the API.

## 4. Design System Rules

Global tokens live in `frontend/src/styles/tokens.css`. Shared operational patterns
live in `base.css`, `forms.css`, and `workspace.css`. A page may add a uniquely
named, page-scoped selector, but must not redefine global `.primary-button`,
`.secondary-button`, `header`, `table`, or `dialog` semantics.

Frozen values: primary `#1769e8`, neutral page `#f3f8ff`/`#f5f7fa`, white surfaces,
success `#159a68`, warning `#d97706`, error `#dc3d4b`, compact radius 6-8px,
operational controls 40-42px, auth controls 50px, workspace sidebar 248px, topbar
72px, and content width 1180px. Do not add gradients, glassmorphism, glow, neon,
giant radii, fake KPI cards, fake notifications, or emoji navigation.

Use dense tables for comparison, bounded cards for records, explicit loading/empty/
error states, and dialogs only for bounded actions. `403` must be an explicit
authorization state or role-home redirect; `404` must be a real not-found route/state,
not a blank screen. Status and role labels shown to users must be localized; raw
authorities are not product copy.

Responsive baseline: 1440, 1366, 1024, and 375px. At 1100px wide tables may become
cards; at 820px management navigation becomes in-flow; at 520px forms, actions,
public navigation, and details stack. No horizontal scroll or layout shift during
loading/error states.

## 5. Student Navigation Recommendation

Keep `/student` as a compact identity/self-service entry. Add student navigation only
when a self-scoped query API and browser-tested flow exist for the destination. Scores,
ranking, appeals, feedback, and activities are not implemented by this stage. Never
copy school-wide values from historical page 10-12 into the student shell.

## 6. Explicit Conflicts And Future Gates

- Historical Teacher screens are removed from the product role model.
- Historical five-role permission tables do not override the current three-role model.
- Screenshot KPI, ranking, activity, message, and notification values are illustrative
  and must never be copied as runtime data.
- Historical broad SUPER_ADMIN school-internal operations remain constrained by current
  resource authorization.
- Public home and public activities require real query contracts before routes are added.
- Activity application, score writing, and media upload remain identity/product-decision
  gated; they are not assigned to `SCHOOL_ADMIN` in this stage.

## 7. Stage 17 Outcome

The design baseline is converged around one public shell and one authenticated
management shell, while the capability inventory is refreshed against the post-Stage
16 code. This document does not authorize Stage 18 implementation.
