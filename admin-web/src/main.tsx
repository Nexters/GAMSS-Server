import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// 로컬(mock)은 Firebase가 필요 없다. 배포(구글 로그인) 모드인데 설정이 비어 있으면
// App(→ getAuth) 이 로그인 시점에 터지므로, 그 전에 안내를 보여준다.
const mockMode = import.meta.env.VITE_AUTH_MODE === 'mock'
const hasFirebaseConfig = Boolean(import.meta.env.VITE_FIREBASE_API_KEY)

function ConfigMissing() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-6">
      <div className="max-w-md rounded-lg border bg-background p-6 text-sm">
        <h1 className="text-base font-semibold">설정이 필요합니다</h1>
        <p className="mt-2 text-muted-foreground">
          로컬 개발이면 <code>admin-web/.env.local</code> 에 <code>VITE_AUTH_MODE=mock</code> 을 넣으세요
          (Firebase 불필요). 배포용 구글 로그인을 쓰려면 <code>VITE_FIREBASE_*</code> 값을 채우세요.
          템플릿은 <code>.env.example</code> 에 있습니다.
        </p>
      </div>
    </div>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>{mockMode || hasFirebaseConfig ? <App /> : <ConfigMissing />}</StrictMode>,
)
