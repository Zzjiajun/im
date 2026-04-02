const KEY = 'im_token'

let cached: string | null = typeof localStorage !== 'undefined' ? localStorage.getItem(KEY) : null

export function getToken(): string | null {
  return cached
}

export function setToken(token: string | null) {
  cached = token
  if (typeof localStorage === 'undefined') return
  if (token) localStorage.setItem(KEY, token)
  else localStorage.removeItem(KEY)
}
