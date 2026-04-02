import { http, unwrap } from './http'
import type { MediaFile } from '@/types/api'

export interface UploadMediaOptions {
  mediaType?: string
  width?: number
  height?: number
  durationSeconds?: number
  coverUrl?: string
}

export function uploadMedia(file: File, mediaType?: string, extra?: UploadMediaOptions) {
  const fd = new FormData()
  fd.append('file', file)
  const mt = extra?.mediaType ?? mediaType
  if (mt) fd.append('mediaType', mt)
  if (extra?.width != null) fd.append('width', String(extra.width))
  if (extra?.height != null) fd.append('height', String(extra.height))
  if (extra?.durationSeconds != null) {
    fd.append('durationSeconds', String(extra.durationSeconds))
  }
  if (extra?.coverUrl) fd.append('coverUrl', extra.coverUrl)
  // 勿手动设置 Content-Type（需带 boundary）；单独拉长超时，避免默认 30s 掐断大文件导致服务端 EOF
  return unwrap<MediaFile>(
    http.post('/upload/media', fd, { timeout: 600_000 })
  )
}
