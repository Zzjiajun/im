import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { getToken } from "@/utils/token";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/LoginView.vue"),
    },
    {
      path: "/",
      component: () => import("@/views/LayoutView.vue"),
      meta: { requiresAuth: true },
      children: [
        { path: "", name: "dashboard", component: () => import("@/views/DashboardView.vue") },
        { path: "users", name: "users", component: () => import("@/views/UsersView.vue") },
        { path: "reports", name: "reports", component: () => import("@/views/ReportsView.vue") },
        { path: "messages", name: "messages", component: () => import("@/views/MessageSearchView.vue") },
        { path: "notifications", name: "notifications", component: () => import("@/views/NotificationsView.vue") },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (getToken() && !auth.nickname) {
    await auth.tryRestore();
  }
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
  if (to.name === "login" && auth.isLoggedIn) {
    return { name: "dashboard" };
  }
  return true;
});

export default router;
