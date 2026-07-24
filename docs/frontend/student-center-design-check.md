# Student Center Design Check

Date: 2026-07-24
Source: docs/source/pages/page_09.png, page_10.png
Analysis method: Pillow pixel sampling + color quantization

## Design Image Analysis

### Page 09 (5.6 学生个人中心)
- Dimensions: 1192 × 1684 px
- Sidebar: **#ffffff (white)** 
- Content area: #dfecfb (soft blue-gray)
- Card backgrounds: #e5f0fc (light blue tint)
- Stat cards: #dfedfc
- Overall palette: white + very soft blue (#eff6fd, #f0f5fd, #f9fafd)

### Page 10 (5.7 我的成绩与排名)
- Dimensions: 1192 × 1684 px
- Sidebar: **#ffffff (white)**
- Content area: #f2f8fe (very light blue)
- Card backgrounds: #f2f8fe
- Same white + soft blue palette

## Route → Design Mapping

| Route | Design Page | Status |
|-------|------------|--------|
| /student | page_09 (5.6 学生个人中心) | Implemented |
| /student/activities | Derived from 5.6 | Implemented |
| /student/activities/:id | Derived from 5.6 | Implemented |
| /student/projects | Derived from 5.6 | Implemented |
| /student/projects/:id | Derived from 5.6 | Implemented |
| /student/scores | page_10 (5.7 我的成绩与排名) | Implemented |
| /student/scores/:id | Derived from 5.7 | Implemented |

## Design Fidelity Items

### Matched
- [x] Stat card layout (4 cards in a row)
- [x] List-card pattern for activities/projects/scores
- [x] Tag-based status display with color coding
- [x] Pagination at bottom
- [x] Responsive grid (el-row/el-col)

### Mismatched (preexisting WorkbenchLayout constraint)
- [ ] **Sidebar color**: Implemented uses #304156 (dark navy from admin template); design uses #ffffff (white)
  - *Root cause*: WorkbenchLayout.vue shared by all 4 roles; dark sidebar is intentional for admin/teacher areas
  - *Decision*: For student workspace specifically, the white sidebar would be more appropriate per design. However, changing the sidebar color for one role would require a StudentWorkbenchLayout separate from the shared WorkbenchLayout. This is deferred to a dedicated UX task.
- [ ] **Content background**: Implemented uses #f5f7fa (Element Plus gray); design uses #dfecfb/#f2f8fe (soft blue)
  - *Partial fix*: Content area background changed to match design
- [ ] **Card background tint**: Implemented uses white cards; design uses slightly blue-tinted cards (#e5f0fc)
  - *Decision*: Minor difference; white cards on soft blue background already provide sufficient contrast

### Cannot Replicate (data-driven constraints)
- [ ] Profile avatar section in design sidebar — no user avatar data in backend
- [ ] Grade/class display — student_profiles is a deferred table
- [ ] Multi-tab layout in page_10 (~10 tabs) — scoped to current task features only

### Responsive Adjustments
- 1440px: Full sidebar + 4-column stat grid
- 1024px: 2-column stat grid
- 390px: Single column, collapsed sidebar

## Action Items
1. [x] Replace content-area gray (#f5f7fa) with soft blue (#eef4fb) in StudentDashboard and student pages
2. [x] Add subtle blue tint to stat cards
3. [ ] Separate StudentWorkbenchLayout with white sidebar (deferred)
4. [x] Ensure mobile breakpoints work
