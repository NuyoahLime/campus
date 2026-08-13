import { createRouter, createWebHistory } from 'vue-router';
import { resolveRoleHome, roleHomeLocation } from './roleHome';
import { useAuthStore } from '../stores/auth';
import LoginView from '../views/LoginView.vue';
import RoleUnavailableView from '../views/RoleUnavailableView.vue';
import SchoolAdminActivationView from '../views/SchoolAdminActivationView.vue';
import SchoolAdminWorkspaceView from '../views/SchoolAdminWorkspaceView.vue';
import StudentApplicationRejectedView from '../views/StudentApplicationRejectedView.vue';
import StudentApplicationResubmitView from '../views/StudentApplicationResubmitView.vue';
import StudentIdentityReviewView from '../views/StudentIdentityReviewView.vue';
import StudentRegistrationView from '../views/StudentRegistrationView.vue';
import StudentWorkspaceView from '../views/StudentWorkspaceView.vue';
import SuperAdminWorkspaceView from '../views/SuperAdminWorkspaceView.vue';
import SuperAdminSchoolRegistrationDetailView from '../views/SuperAdminSchoolRegistrationDetailView.vue';
import SuperAdminSchoolRegistrationListView from '../views/SuperAdminSchoolRegistrationListView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'role-dispatch',
      component: RoleUnavailableView
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/register',
      name: 'student-register',
      component: StudentRegistrationView,
      meta: { guestOnly: true }
    },
    {
      path: '/school-admin/activate',
      name: 'school-admin-activate',
      component: SchoolAdminActivationView,
      meta: { guestOnly: true }
    },
    {
      path: '/student/application/rejected',
      name: 'student-application-rejected',
      component: StudentApplicationRejectedView,
      meta: { guestOnly: true }
    },
    {
      path: '/student/application/resubmit',
      name: 'student-application-resubmit',
      component: StudentApplicationResubmitView,
      meta: { guestOnly: true }
    },
    {
      path: '/school-admin/student-applications',
      name: 'student-identity-review',
      component: StudentIdentityReviewView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/super-admin',
      name: 'super-admin-workspace',
      component: SuperAdminWorkspaceView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/school-registrations',
      name: 'super-admin-school-registrations',
      component: SuperAdminSchoolRegistrationListView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/school-registrations/:id',
      name: 'super-admin-school-registration-detail',
      component: SuperAdminSchoolRegistrationDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/school-admin',
      name: 'school-admin-workspace',
      component: SchoolAdminWorkspaceView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/student',
      name: 'student-workspace',
      component: StudentWorkspaceView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/role-unavailable',
      name: 'role-unavailable',
      component: RoleUnavailableView,
      meta: { requiresAuth: true }
    }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.initialized) {
    await auth.restoreSession();
  }

  if (to.name === 'role-dispatch') {
    return auth.isAuthenticated
      ? roleHomeLocation(auth.currentUser)
      : { name: 'login' };
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  const roleHome = auth.isAuthenticated ? resolveRoleHome(auth.currentUser) : null;

  if (roleHome?.status === 'unavailable') {
    return to.name === 'role-unavailable' ? true : { name: roleHome.routeName };
  }

  if (to.name === 'role-unavailable' && roleHome?.status === 'resolved') {
    return { name: roleHome.routeName };
  }

  const requiredAuthority = typeof to.meta.requiredAuthority === 'string'
    ? to.meta.requiredAuthority
    : null;
  if (requiredAuthority && !auth.currentUser?.authorities.includes(requiredAuthority)) {
    return roleHomeLocation(auth.currentUser);
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    return roleHomeLocation(auth.currentUser);
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return roleHomeLocation(auth.currentUser);
  }

  return true;
});

export default router;
