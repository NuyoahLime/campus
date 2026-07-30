import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // Public routes
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/projects',
      name: 'projects',
      component: () => import('@/views/projects/ProjectListView.vue'),
    },
    {
      path: '/projects/:projectId',
      name: 'project-detail',
      component: () => import('@/views/projects/ProjectDetailView.vue'),
      props: true,
    },
    {
      path: '/activities',
      name: 'activities',
      component: () => import('@/views/activities/ActivityListView.vue'),
    },
    {
      path: '/activities/:activityId',
      name: 'activity-detail',
      component: () => import('@/views/activities/ActivityDetailView.vue'),
      props: true,
    },
    // Auth routes
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/activate-account',
      name: 'activate',
      component: () => import('@/views/ActivateAccountView.vue'),
    },
    {
      path: '/workspaces',
      name: 'workspaces',
      component: () => import('@/views/WorkspacesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/account/no-access',
      name: 'no-access',
      component: () => import('@/views/NoAccessView.vue'),
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: () => import('@/views/ForbiddenView.vue'),
    },
    // Student workbench
    {
      path: '/student',
      component: () => import('@/layouts/StudentWorkbenchLayout.vue'),
      meta: { requiresAuth: true, roles: ['STUDENT'] },
      children: [
        { path: '', name: 'student-home', component: () => import('@/views/workbench/StudentDashboard.vue') },
        { path: 'activities', name: 'student-activities', component: () => import('@/views/workbench/StudentActivityList.vue') },
        { path: 'activities/:activityId', name: 'student-activity-detail', component: () => import('@/views/workbench/StudentActivityDetail.vue') },
        { path: 'projects', name: 'student-projects', component: () => import('@/views/workbench/StudentProjectList.vue') },
        { path: 'projects/:activityProjectId', name: 'student-project-detail', component: () => import('@/views/workbench/StudentProjectDetail.vue') },
        { path: 'scores', name: 'student-scores', component: () => import('@/views/workbench/StudentScoreList.vue') },
        { path: 'scores/:attemptId', name: 'student-score-detail', component: () => import('@/views/workbench/StudentScoreDetail.vue') },
        { path: 'rankings', name: 'student-rankings', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'appeals', name: 'student-appeals', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'achievements', name: 'student-achievements', component: () => import('@/views/NotImplementedView.vue') },
      ],
    },
    // Teacher workbench
    {
      path: '/teacher',
      component: () => import('@/layouts/WorkbenchLayout.vue'),
      meta: { requiresAuth: true, roles: ['TEACHER'] },
      children: [
        { path: '', name: 'teacher-home', component: () => import('@/views/workbench/TeacherDashboard.vue') },
        { path: 'applications', name: 'teacher-applications', component: () => import('@/views/workbench/TeacherApplicationList.vue') },
        { path: 'applications/new', name: 'teacher-app-create', component: () => import('@/views/workbench/TeacherApplicationCreate.vue') },
        { path: 'applications/:applicationId', name: 'teacher-app-detail', component: () => import('@/views/workbench/TeacherApplicationDetail.vue') },
        { path: 'applications/:applicationId/edit', name: 'teacher-app-edit', component: () => import('@/views/workbench/TeacherApplicationEdit.vue') },
        { path: 'projects', name: 'teacher-projects', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'responsible', name: 'teacher-responsible', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'scores', name: 'teacher-scores', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'review', name: 'teacher-review', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'appeals', name: 'teacher-appeals', component: () => import('@/views/NotImplementedView.vue') },
      ],
    },
    // School admin workbench
    {
      path: '/school-admin',
      component: () => import('@/layouts/WorkbenchLayout.vue'),
      meta: { requiresAuth: true, roles: ['SCHOOL_ADMIN'] },
      children: [
        { path: '', name: 'school-admin-home', component: () => import('@/views/workbench/SchoolAdminHomeView.vue') },
        { path: 'activities', name: 'school-admin-activities', component: () => import('@/views/workbench/SchoolAdminActivityList.vue') },
        { path: 'activities/new', name: 'school-admin-activity-create', component: () => import('@/views/workbench/SchoolAdminActivityCreate.vue') },
        { path: 'activities/:activityId', name: 'school-admin-activity-detail', component: () => import('@/views/workbench/SchoolAdminActivityDetail.vue'), props: (route) => ({ activityId: String(route.params.activityId ?? '') }) },
        { path: 'participants', name: 'school-admin-participants', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'projects', name: 'school-admin-projects', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'teachers', name: 'school-admin-teachers', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'scores', name: 'school-admin-scores', component: () => import('@/views/workbench/SchoolAdminScoreReview.vue') },
        { path: 'rankings', name: 'school-admin-rankings', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'accounts', name: 'school-admin-accounts', component: () => import('@/views/workbench/SchoolAdminAccounts.vue') },
      ],
    },
    // Super admin workbench
    {
      path: '/admin',
      component: () => import('@/layouts/WorkbenchLayout.vue'),
      meta: { requiresAuth: true, roles: ['SUPER_ADMIN'] },
      children: [
        { path: '', name: 'admin-home', component: () => import('@/views/workbench/AdminDashboard.vue') },
        { path: 'applications', name: 'admin-applications', component: () => import('@/views/workbench/AdminApplicationList.vue') },
        { path: 'applications/:applicationId', name: 'admin-app-detail', component: () => import('@/views/workbench/AdminApplicationDetail.vue') },
        { path: 'projects', name: 'admin-projects', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'public-review', name: 'admin-public-review', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'schools', name: 'admin-schools', component: () => import('@/views/NotImplementedView.vue') },
        { path: 'schools/:schoolId/administrators', name: 'admin-school-admins', component: () => import('@/views/workbench/AdminSchoolAdministrators.vue') },
        { path: 'operations', name: 'admin-operations', component: () => import('@/views/NotImplementedView.vue') },
      ],
    },
    // 404
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
    },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

// Navigation guard
router.beforeEach(async (to) => {
  const auth = useAuthStore();

  // Initialize auth state on first navigation
  if (!auth.initialized) {
    await auth.restoreSession();
  }

  // guestOnly: redirect authenticated users to workspace
  if (to.meta.guestOnly && auth.authenticated) {
    const redirect = (to.query.redirect as string) || auth.defaultWorkspaceRoute();
    return { path: redirect, replace: true };
  }

  // requiresAuth: redirect guests to login
  if (to.meta.requiresAuth && !auth.authenticated) {
    return { path: '/login', query: { redirect: to.fullPath }, replace: true };
  }

  // role check — use primaryRole only, not roles array
  if (to.meta.roles && Array.isArray(to.meta.roles) && (to.meta.roles as string[]).length > 0) {
    const primaryRole = auth.user?.primaryRole;
    if (!primaryRole) return { path: '/account/no-access', replace: true };
    const required = to.meta.roles as string[];
    if (!required.includes(primaryRole)) {
      return { path: '/forbidden', replace: true };
    }
  }

  return true;
});

export default router;
