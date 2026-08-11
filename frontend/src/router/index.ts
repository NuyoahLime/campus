import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import SchoolAdminActivationView from '../views/SchoolAdminActivationView.vue';
import StudentApplicationRejectedView from '../views/StudentApplicationRejectedView.vue';
import StudentApplicationResubmitView from '../views/StudentApplicationResubmitView.vue';
import StudentIdentityReviewView from '../views/StudentIdentityReviewView.vue';
import StudentRegistrationView from '../views/StudentRegistrationView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true }
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
    }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.initialized) {
    await auth.restoreSession();
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }

  const requiredAuthority = typeof to.meta.requiredAuthority === 'string'
    ? to.meta.requiredAuthority
    : null;
  if (requiredAuthority && !auth.currentUser?.authorities.includes(requiredAuthority)) {
    return { name: 'home' };
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'home' };
  }

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'home' };
  }

  return true;
});

export default router;
