import { getFirebaseAuth } from './firebase'

// 같은 도메인(admin.gamss.kr)에 배포하면 비워두고 상대경로(/api)를 쓴다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const TOKEN_KEY = 'gamss-admin-token'

// mock(로컬) 모드: Firebase 없이 백엔드 dev-login 으로 로그인한다.
const MOCK_MODE = import.meta.env.VITE_AUTH_MODE === 'mock'
const DEV_ADMIN_EMAIL: string = import.meta.env.VITE_DEV_ADMIN_EMAIL ?? ''

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function clearStoredToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

function saveToken(body: { success?: boolean; data?: { accessToken?: string }; error?: { code?: string } }): string {
  if (!body?.success || !body.data?.accessToken) {
    throw new Error(body?.error?.code ?? 'LOGIN_FAILED')
  }
  const token = body.data.accessToken
  localStorage.setItem(TOKEN_KEY, token)
  return token
}

/**
 * Firebase ID 토큰을 백엔드 관리자 토큰으로 교환한다(배포/구글 로그인 경로).
 * 허용목록에 없으면 NOT_ADMIN, Firebase 세션이 없으면 NO_FIREBASE_USER 를 던진다.
 */
export async function exchangeAdminToken(): Promise<string> {
  const user = getFirebaseAuth().currentUser
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
  if (!res.ok) {
    throw new Error(body?.error?.code === 'NOT_ADMIN' ? 'NOT_ADMIN' : 'LOGIN_FAILED')
  }
  return saveToken(body)
}

/** 로컬 개발용 로그인. Firebase 없이 허용목록 이메일로 토큰을 받는다. */
export async function devLoginToken(email: string): Promise<string> {
  const res = await fetch(`${BASE_URL}/api/admin/auth/dev-login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email }),
  })
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    throw new Error(body?.error?.code === 'NOT_ADMIN' ? 'NOT_ADMIN' : 'LOGIN_FAILED')
  }
  return saveToken(body)
}

// 진행 중인 갱신을 캐싱한다. 여러 요청이 동시에 만료를 겪어도 재발급은 한 번만 하고,
// 나머지는 같은 Promise를 함께 기다린다(중복 호출·토큰 덮어쓰기 방지).
let refreshInFlight: Promise<string> | null = null

function refreshToken(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = (MOCK_MODE ? devLoginToken(DEV_ADMIN_EMAIL) : exchangeAdminToken()).finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

/**
 * 관리자 토큰을 실어 API를 호출하고 ApiResponse의 data를 돌려준다.
 * 토큰 만료(401)면 재발급 후 한 번만 재시도한다. 403(권한 부족)은 재발급해도 소용없어 즉시 실패시킨다.
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
  if (res.status === 401) {
    const refreshed = await refreshToken()
    res = await request(refreshed)
  }

  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) {
    throw new Error(body?.error?.code ?? `HTTP_${res.status}`)
  }
  return body.data as T
}
