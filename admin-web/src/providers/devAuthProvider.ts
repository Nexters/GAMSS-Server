import type { AuthProvider } from '@refinedev/core'
import { clearStoredToken, devLoginToken, getStoredToken } from '@/lib/api'

// 로컬 개발용 관리자 이메일. .env.local 의 VITE_DEV_ADMIN_EMAIL 로 지정한다.
const DEV_ADMIN_EMAIL: string = import.meta.env.VITE_DEV_ADMIN_EMAIL ?? ''

/**
 * 로컬 개발용 인증(mock 모드). Firebase 없이 백엔드 dev-login(허용목록 검증)으로 로그인한다.
 * 백엔드도 local 프로필에서만 dev-login 을 열어주므로 배포 환경에서는 동작하지 않는다.
 */
export const devAuthProvider: AuthProvider = {
  login: async () => {
    try {
      await devLoginToken(DEV_ADMIN_EMAIL)
      return { success: true, redirectTo: '/members' }
    } catch (error) {
      clearStoredToken()
      const notAdmin = error instanceof Error && error.message === 'NOT_ADMIN'
      return {
        success: false,
        error: {
          name: notAdmin ? '접근 권한 없음' : '로그인 실패',
          message: notAdmin
            ? `${DEV_ADMIN_EMAIL} 이 허용목록(admin.emails)에 없습니다.`
            : '백엔드(dev-login)에 연결하지 못했습니다. 로컬 서버가 켜져 있는지 확인하세요.',
        },
      }
    }
  },

  logout: async () => {
    clearStoredToken()
    return { success: true, redirectTo: '/login' }
  },

  check: async () => {
    if (getStoredToken()) {
      return { authenticated: true }
    }
    return { authenticated: false, redirectTo: '/login' }
  },

  getIdentity: async () => {
    if (!getStoredToken()) {
      return null
    }
    return { id: DEV_ADMIN_EMAIL, name: '로컬 관리자', email: DEV_ADMIN_EMAIL }
  },

  onError: async (error) => {
    const message = error instanceof Error ? error.message : String(error?.message ?? '')
    if (message === 'NOT_ADMIN' || message === 'HTTP_401') {
      return { logout: true, redirectTo: '/login', error }
    }
    return { error }
  },
}
