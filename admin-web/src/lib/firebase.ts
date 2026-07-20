import { initializeApp } from 'firebase/app'
import { GoogleAuthProvider, getAuth, onAuthStateChanged } from 'firebase/auth'

// apiKey 등은 공개값이라 프론트에 포함해도 안전하다(Firebase 콘솔 > 프로젝트 설정 > SDK 설정).
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const app = initializeApp(firebaseConfig)

export const auth = getAuth(app)

export const googleProvider = new GoogleAuthProvider()

// Firebase는 새로고침 직후 currentUser가 잠깐 null이다. 세션 복원이 끝날 때까지 기다리는 프로미스.
export const authInitialized = new Promise<void>((resolve) => {
  const unsubscribe = onAuthStateChanged(auth, () => {
    unsubscribe()
    resolve()
  })
})
