import { http, unwrap } from './http'
import type { MessageReportAdminVO } from '@/types/api'

export function listReports(limit = 50) {
  return unwrap<MessageReportAdminVO[]>(
    http.get('/admin/reports', { params: { limit } })
  )
}
