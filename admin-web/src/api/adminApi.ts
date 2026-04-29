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

/** 举报列表（分页） */
export function fetchReports(page = 1, size = 20) {
  return http.get("/admin/reports", { params: { page, size } }).then(res => {
    const body = res.data as { code: number; message?: string; data: { records: MessageReportAdminVO[]; total: number } }
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  })
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

/** 管理员发布系统公告 */
export function createAnnouncement(title: string, content: string, targetUserIds?: SnowflakeId[]) {
  const params: Record<string, string | number | boolean> = { title, content };
  // Spring Boot @RequestParam List<Long> 接受逗号分隔或重复参数
  if (targetUserIds && targetUserIds.length > 0) {
    params.targetUserIds = targetUserIds.join(",");
  }
  return unwrap<void>(http.post("/admin/notifications/announcement", null, { params }));
}
