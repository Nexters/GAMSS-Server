import { initializeApp } from 'firebase/app'
import { GoogleAuthProvider, type Auth, getAuth, onAuthStateChanged } from 'firebase/auth'

// 지연 초기화: getAuth 를 모듈 로드 시점이 아니라 실제 사용 시점에 호출한다.
// 이렇게 해야 mock(로컬) 모드에서 Firebase 설정이 없어도 앱이 흰 화면으로 죽지 않는다.
let cachedAuth: Auth | null = null

export function getFirebaseAuth(): Auth {
  if (cachedAuth) {
    return cachedAuth
  }
  const app = initializeApp({
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
  })
  cachedAuth = getAuth(app)
  return cachedAuth
}

// 로그인 팝업용 provider. 앱 초기화가 필요 없어 안전하다.
export const googleProvider = new GoogleAuthProvider()

// Firebase는 새로고침 직후 currentUser가 잠깐 null이다. 세션 복원이 끝날 때까지 기다린다.
let initPromise: Promise<void> | null = null

export function whenAuthInitialized(): Promise<void> {
  if (!initPromise) {
    initPromise = new Promise((resolve) => {
      const unsubscribe = onAuthStateChanged(getFirebaseAuth(), () => {
        unsubscribe()
        resolve()
      })
    })
  }
  return initPromise
}
