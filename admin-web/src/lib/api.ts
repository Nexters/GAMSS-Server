import { auth } from './firebase'

// 같은 도메인(admin.gamss.kr)에 배포하면 비워두고 상대경로(/api)를 쓴다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_KEY = 'gamss-admin-token'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * Firebase ID 토큰을 백엔드 관리자 토큰으로 교환해 저장한다.
 * 허용목록에 없으면 NOT_ADMIN, Firebase 세션이 없으면 NO_FIREBASE_USER 를 던진다.
 */
export async function exchangeAdminToken(): Promise<string> {
  const user = auth.currentUser
  if (!user) {
    throw new Error('NO_FIREBASE_USER')
  }
  const idToken = await user.getIdToken()
  const res = await fetch(`${BASE_URL}/api/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) {
    throw new Error(body?.error?.code === 'NOT_ADMIN' ? 'NOT_ADMIN' : 'LOGIN_FAILED')
  }
  const token: string = body.data.accessToken
  localStorage.setItem(TOKEN_KEY, token)
  return token
}

/**
 * 관리자 토큰을 실어 API를 호출하고 ApiResponse의 data를 돌려준다.
 * 토큰이 만료/무효(401·403)면 Firebase 세션으로 재발급 후 한 번만 재시도한다.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const request = (token: string | null) =>
    fetch(`${BASE_URL}${path}`, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    })

  let res = await request(getStoredToken())
  if (res.status === 401 || res.status === 403) {
    const refreshed = await exchangeAdminToken()
    res = await request(refreshed)
  }

  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) {
    throw new Error(body?.error?.code ?? `HTTP_${res.status}`)
  }
  return body.data as T
}
