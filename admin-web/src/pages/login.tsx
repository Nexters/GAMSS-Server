import { useState } from 'react'
import { useLogin } from '@refinedev/core'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'

export function LoginPage() {
  const { mutate: login, isLoading } = useLogin<{ email: string; password: string }>()
  const [email, setEmail] = useState('admin@gamss.kr')
  const [password, setPassword] = useState('')

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    login({ email, password })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center gap-2">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
            G
          </div>
          <h1 className="text-lg font-semibold tracking-tight">GAMSS Admin</h1>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>로그인</CardTitle>
            <CardDescription>관리자 계정으로 로그인하세요.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={onSubmit}>
              <div className="space-y-1.5">
                <label htmlFor="email" className="text-sm font-medium">
                  이메일
                </label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@gamss.kr"
                  autoComplete="username"
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="password" className="text-sm font-medium">
                  비밀번호
                </label>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                />
              </div>
              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading ? '로그인 중…' : '로그인'}
              </Button>
            </form>
            <p className="mt-4 text-center text-xs text-muted-foreground">
              현재 인증은 목(mock)입니다 — 아무 값이나 입력해도 로그인됩니다.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
