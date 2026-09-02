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
import SuperAdminSchoolAdminsView from '../views/SuperAdminSchoolAdminsView.vue';
import SuperAdminSchoolDetailView from '../views/SuperAdminSchoolDetailView.vue';
import SuperAdminSchoolListView from '../views/SuperAdminSchoolListView.vue';
import PublicProjectsView from '../views/PublicProjectsView.vue';
import PublicProjectDetailView from '../views/PublicProjectDetailView.vue';
import PublicHomeView from '../views/PublicHomeView.vue';
import PublicActivitiesView from '../views/PublicActivitiesView.vue';
import PublicActivityDetailView from '../views/PublicActivityDetailView.vue';
import PublicSchoolRegistrationView from '../views/PublicSchoolRegistrationView.vue';
import SuperAdminProjectListView from '../views/SuperAdminProjectListView.vue';
import SuperAdminProjectDetailView from '../views/SuperAdminProjectDetailView.vue';
import SchoolAdminActivityListView from '../views/SchoolAdminActivityListView.vue';
import SchoolAdminActivityDetailView from '../views/SchoolAdminActivityDetailView.vue';
import SchoolAdminActivityParticipantsView from '../views/SchoolAdminActivityParticipantsView.vue';
import SchoolAdminActivityScoresView from '../views/SchoolAdminActivityScoresView.vue';
import StudentScoresView from '../views/StudentScoresView.vue';
import StudentScoreDetailView from '../views/StudentScoreDetailView.vue';
import StudentActivitiesView from '../views/StudentActivitiesView.vue';
import StudentActivityDetailView from '../views/StudentActivityDetailView.vue';
import StudentAppealsView from '../views/StudentAppealsView.vue';
import StudentAppealCreateView from '../views/StudentAppealCreateView.vue';
import StudentAppealDetailView from '../views/StudentAppealDetailView.vue';
import StudentFeedbackView from '../views/StudentFeedbackView.vue';
import StudentFeedbackCreateView from '../views/StudentFeedbackCreateView.vue';
import StudentFeedbackDetailView from '../views/StudentFeedbackDetailView.vue';
import SchoolAdminAppealsView from '../views/SchoolAdminAppealsView.vue';
import SchoolAdminAppealDetailView from '../views/SchoolAdminAppealDetailView.vue';
import SchoolAdminFeedbackView from '../views/SchoolAdminFeedbackView.vue';
import SchoolAdminFeedbackDetailView from '../views/SchoolAdminFeedbackDetailView.vue';
import RankingListView from '../views/RankingListView.vue';
import RankingDetailView from '../views/RankingDetailView.vue';
import SchoolAdminRankingManagementView from '../views/SchoolAdminRankingManagementView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'public-home',
      component: PublicHomeView
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
      path: '/projects',
      name: 'public-projects',
      component: PublicProjectsView
    },
    {
      path: '/projects/:id',
      name: 'public-project-detail',
      component: PublicProjectDetailView
    },
    {
      path: '/activities',
      name: 'public-activities',
      component: PublicActivitiesView
    },
    {
      path: '/activities/:id',
      name: 'public-activity-detail',
      component: PublicActivityDetailView
    },
    {
      path: '/school-registration',
      name: 'public-school-registration',
      component: PublicSchoolRegistrationView
    },
    {
      path: '/rankings',
      name: 'public-rankings',
      component: RankingListView,
      props: { mode: 'public' }
    },
    {
      path: '/rankings/:id',
      name: 'public-ranking-detail',
      component: RankingDetailView,
      props: { mode: 'public' }
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
      path: '/school-admin/activities',
      name: 'school-admin-activities',
      component: SchoolAdminActivityListView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/activities/new',
      name: 'school-admin-activity-new',
      component: SchoolAdminActivityDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/activities/:id/edit',
      name: 'school-admin-activity-edit',
      component: SchoolAdminActivityDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/activities/:id/participants',
      name: 'school-admin-activity-participants',
      component: SchoolAdminActivityParticipantsView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/activities/:id/scores',
      name: 'school-admin-activity-scores',
      component: SchoolAdminActivityScoresView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/activities/:id',
      name: 'school-admin-activity-detail',
      component: SchoolAdminActivityDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/appeals',
      name: 'school-admin-appeals',
      component: SchoolAdminAppealsView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/appeals/:id',
      name: 'school-admin-appeal-detail',
      component: SchoolAdminAppealDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/feedback',
      name: 'school-admin-feedback',
      component: SchoolAdminFeedbackView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/feedback/:id',
      name: 'school-admin-feedback-detail',
      component: SchoolAdminFeedbackDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/rankings',
      name: 'school-admin-rankings',
      component: RankingListView,
      props: { mode: 'school-admin' },
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/ranking-management',
      name: 'school-admin-ranking-management',
      component: SchoolAdminRankingManagementView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/school-admin/rankings/:id',
      name: 'school-admin-ranking-detail',
      component: RankingDetailView,
      props: { mode: 'school-admin' },
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
      path: '/super-admin/schools',
      name: 'super-admin-schools',
      component: SuperAdminSchoolListView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/schools/:id',
      name: 'super-admin-school-detail',
      component: SuperAdminSchoolDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/schools/:id/admins',
      name: 'super-admin-school-admins',
      component: SuperAdminSchoolAdminsView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/projects',
      name: 'super-admin-projects',
      component: SuperAdminProjectListView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/super-admin/projects/:id',
      name: 'super-admin-project-detail',
      component: SuperAdminProjectDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SUPER_ADMIN' }
    },
    {
      path: '/school-admin',
      name: 'school-admin-workspace',
      component: SchoolAdminWorkspaceView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_SCHOOL_ADMIN' }
    },
    {
      path: '/student/scores',
      name: 'student-scores',
      component: StudentScoresView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/scores/:id',
      name: 'student-score-detail',
      component: StudentScoreDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/activities',
      name: 'student-activities',
      component: StudentActivitiesView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/activities/:id',
      name: 'student-activity-detail',
      component: StudentActivityDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/appeals',
      name: 'student-appeals',
      component: StudentAppealsView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/appeals/new',
      name: 'student-appeal-new',
      component: StudentAppealCreateView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/appeals/:id',
      name: 'student-appeal-detail',
      component: StudentAppealDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/feedback',
      name: 'student-feedback',
      component: StudentFeedbackView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/feedback/new',
      name: 'student-feedback-new',
      component: StudentFeedbackCreateView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/feedback/:id',
      name: 'student-feedback-detail',
      component: StudentFeedbackDetailView,
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/rankings',
      name: 'student-rankings',
      component: RankingListView,
      props: { mode: 'student' },
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
    },
    {
      path: '/student/rankings/:id',
      name: 'student-ranking-detail',
      component: RankingDetailView,
      props: { mode: 'student' },
      meta: { requiresAuth: true, requiredAuthority: 'ROLE_STUDENT' }
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
