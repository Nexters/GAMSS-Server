import { useLogin } from '@refinedev/core'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'

const MOCK_MODE = import.meta.env.VITE_AUTH_MODE === 'mock'

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.27-4.74 3.27-8.1Z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84A11 11 0 0 0 12 23Z"
      />
      <path fill="#FBBC05" d="M5.84 14.1a6.6 6.6 0 0 1 0-4.2V7.06H2.18a11 11 0 0 0 0 9.88l3.66-2.84Z" />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1A11 11 0 0 0 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z"
      />
    </svg>
  )
}

export function LoginPage() {
  const { mutate: login, isLoading } = useLogin()

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-muted/40 px-4">
      {/* 은은한 배경: 상단 중앙 하이라이트 + 미세 그리드 */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 [background-image:radial-gradient(circle_at_50%_0%,hsl(var(--foreground)/0.04),transparent_55%)]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 opacity-[0.35] [background-image:linear-gradient(to_right,hsl(var(--border))_1px,transparent_1px),linear-gradient(to_bottom,hsl(var(--border))_1px,transparent_1px)] [background-size:44px_44px] [mask-image:radial-gradient(ellipse_at_center,black,transparent_70%)]"
      />

      <div className="relative w-full max-w-sm">
        <div className="mb-7 flex flex-col items-center gap-3">
          <div className="flex size-12 items-center justify-center rounded-xl bg-primary text-lg font-bold text-primary-foreground shadow-sm">
            G
          </div>
          <div className="text-center">
            <h1 className="text-lg font-semibold tracking-tight">GAMSS Admin</h1>
            <p className="mt-0.5 text-sm text-muted-foreground">백오피스 관리자 콘솔</p>
          </div>
        </div>

        <Card className="shadow-lg shadow-black/[0.03]">
          <CardContent className="p-6">
            <Button variant="outline" className="w-full" disabled={isLoading} onClick={() => login({})}>
              {!MOCK_MODE && <GoogleIcon />}
              {isLoading ? '로그인 중…' : MOCK_MODE ? '로컬 관리자로 로그인' : 'Google 계정으로 로그인'}
            </Button>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              {MOCK_MODE
                ? '로컬 개발 모드 — 허용목록 이메일로 로그인합니다.'
                : '접근이 허용된 이메일만 로그인할 수 있습니다.'}
            </p>
          </CardContent>
        </Card>

        <p className="mt-6 text-center text-xs text-muted-foreground/70">GAMSS · 감정 일기 서비스</p>
      </div>
    </div>
  )
}
