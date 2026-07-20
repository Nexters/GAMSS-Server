import type { AuthProvider } from '@refinedev/core'
import { signInWithPopup, signOut } from 'firebase/auth'
import { auth, authInitialized, googleProvider } from '@/lib/firebase'
import { clearStoredToken, exchangeAdminToken, getStoredToken } from '@/lib/api'

/**
 * 구글 로그인 기반 관리자 인증.
 * 구글로 로그인 → Firebase ID 토큰을 백엔드 관리자 토큰으로 교환하며, 이 교환에서
 * 허용목록(admin.emails) 검증이 이뤄진다. 허용되지 않은 계정은 로그인에 실패한다.
 */
export const authProvider: AuthProvider = {
  login: async () => {
    try {
      await signInWithPopup(auth, googleProvider)
      await exchangeAdminToken()
      return { success: true, redirectTo: '/members' }
    } catch (error) {
      await signOut(auth).catch(() => undefined)
      clearStoredToken()
      const notAdmin = error instanceof Error && error.message === 'NOT_ADMIN'
      return {
        success: false,
        error: {
          name: notAdmin ? '접근 권한 없음' : '로그인 실패',
          message: notAdmin
            ? '백오피스 접근이 허용된 계정이 아닙니다. 관리자에게 문의하세요.'
            : '로그인에 실패했습니다. 다시 시도해주세요.',
        },
      }
    }
  },

  logout: async () => {
    await signOut(auth).catch(() => undefined)
    clearStoredToken()
    return { success: true, redirectTo: '/login' }
  },

  check: async () => {
    await authInitialized
    if (!auth.currentUser) {
      return { authenticated: false, redirectTo: '/login' }
    }
    // Firebase 세션은 있는데 관리자 토큰이 없으면(새 탭·만료) 재발급을 시도한다.
    if (!getStoredToken()) {
      try {
        await exchangeAdminToken()
      } catch {
        await signOut(auth).catch(() => undefined)
        clearStoredToken()
        return { authenticated: false, redirectTo: '/login' }
      }
    }
    return { authenticated: true }
  },

  getIdentity: async () => {
    await authInitialized
    const user = auth.currentUser
    if (!user) {
      return null
    }
    return {
      id: user.uid,
      name: user.displayName ?? '관리자',
      email: user.email ?? '',
      avatar: user.photoURL ?? undefined,
    }
  },

  onError: async (error) => {
    const message = error instanceof Error ? error.message : String(error?.message ?? '')
    if (message === 'NOT_ADMIN' || message === 'HTTP_401' || message === 'NO_FIREBASE_USER') {
      return { logout: true, redirectTo: '/login', error }
    }
    return { error }
  },
}
