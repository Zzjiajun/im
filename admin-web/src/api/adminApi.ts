import { http, unwrap } from "./http";
import type {
  AdminDashboardVO,
  AdminUserPageVO,
  MessageReportAdminVO,
  MessageSearchPageVO,
  SnowflakeId,
} from "@/types";

export function fetchDashboard() {
  return unwrap<AdminDashboardVO>(http.get("/admin/dashboard"));
}

export function fetchUsers(page: number, size: number, keyword?: string) {
  return unwrap<AdminUserPageVO>(http.get("/admin/users", { params: { page, size, keyword } }));
}

export function banUser(userId: SnowflakeId) {
  return unwrap<void>(http.post(`/admin/users/${userId}/ban`));
}

export function unbanUser(userId: SnowflakeId) {
  return unwrap<void>(http.post(`/admin/users/${userId}/unban`));
}

export function fetchReports(limit = 100) {
  return unwrap<MessageReportAdminVO[]>(http.get("/admin/reports", { params: { limit } }));
}

export function adminSearchMessages(
  keyword: string,
  opts?: { conversationId?: SnowflakeId; beforeMessageId?: SnowflakeId; size?: number }
) {
  return unwrap<MessageSearchPageVO>(
    http.get("/admin/messages/search", {
      params: {
        keyword,
        ...(opts?.conversationId ? { conversationId: opts.conversationId } : {}),
        ...(opts?.beforeMessageId ? { beforeMessageId: opts.beforeMessageId } : {}),
        ...(opts?.size != null ? { size: opts.size } : {}),
      },
    })
  );
}
