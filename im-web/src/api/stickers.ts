import { http, unwrap } from './http'
import type {
  CreateStickerItemRequest,
  CreateStickerPackRequest,
  StickerPackDetailVO,
} from '@/types/api'

export function listStickerPacks() {
  return unwrap<StickerPackDetailVO[]>(http.get('/stickers/packs'))
}

export function createStickerPack(body: CreateStickerPackRequest) {
  return unwrap<StickerPackDetailVO>(http.post('/stickers/packs', body))
}

export function createStickerItem(body: CreateStickerItemRequest) {
  return unwrap<void>(http.post('/stickers/items', body))
}
