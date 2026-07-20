import type { AuthProvider } from '@refinedev/core'

// 목(mock) 인증 프로바이더. 실제 admin 인증(/api/admin/**, admin 롤)은 후속 작업.
// 지금은 어떤 값이든 로그인 성공 처리하고, localStorage로 세션 유무만 흉내 낸다.
const AUTH_KEY = 'gamss-admin-auth'

export const mockAuthProvider: AuthProvider = {
  login: async ({ email }: { email?: string }) => {
    localStorage.setItem(AUTH_KEY, JSON.stringify({ email: email ?? 'admin@gamss.kr' }))
    return { success: true, redirectTo: '/members' }
  },

  logout: async () => {
    localStorage.removeItem(AUTH_KEY)
    return { success: true, redirectTo: '/login' }
  },

  check: async () => {
    if (localStorage.getItem(AUTH_KEY)) {
      return { authenticated: true }
    }
    return { authenticated: false, redirectTo: '/login' }
  },

  getIdentity: async () => {
    const raw = localStorage.getItem(AUTH_KEY)
    if (!raw) {
      return null
    }
    const { email } = JSON.parse(raw) as { email: string }
    return { id: 1, name: '관리자', email }
  },

  onError: async (error) => {
    return { error }
  },
}
