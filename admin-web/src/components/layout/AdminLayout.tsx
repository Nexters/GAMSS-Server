import { useGetIdentity, useLogout } from '@refinedev/core'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { Coins, LayoutDashboard, LogOut, ShieldCheck, Sparkles, Users } from 'lucide-react'
import { Avatar } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const MOCK_MODE = import.meta.env.VITE_AUTH_MODE === 'mock'

interface NavItem {
  label: string
  to: string
  icon: typeof Users
  disabled?: boolean
}

const NAV: NavItem[] = [
  { label: '대시보드', to: '/dashboard', icon: LayoutDashboard },
  { label: '회원 관리', to: '/members', icon: Users },
  { label: '관리자 관리', to: '/admin-accounts', icon: ShieldCheck },
  { label: '토큰 사용량', to: '/token-usage', icon: Coins },
  { label: 'LLM 설정', to: '/llm-settings', icon: Sparkles },
]

interface Identity {
  name: string
  email: string
  avatar?: string
}

function sectionOf(pathname: string): string {
  if (pathname.startsWith('/members')) {
    return '회원 관리'
  }
  if (pathname.startsWith('/admin-accounts')) {
    return '관리자 관리'
  }
  if (pathname.startsWith('/dashboard')) {
    return '대시보드'
  }
  if (pathname.startsWith('/llm-settings')) {
    return 'LLM 설정'
  }
  if (pathname.startsWith('/token-usage')) {
    return '토큰 사용량'
  }
  return ''
}

export function AdminLayout() {
  const location = useLocation()
  const { mutate: logout } = useLogout()
  const { data: identity } = useGetIdentity<Identity>()
  const section = sectionOf(location.pathname)

  return (
    <div className="min-h-screen bg-muted/30">
      <aside className="fixed inset-y-0 left-0 flex w-64 flex-col border-r bg-background">
        <div className="flex h-14 items-center gap-2.5 border-b px-5">
          <div className="flex size-7 items-center justify-center rounded-lg bg-primary text-xs font-bold text-primary-foreground shadow-sm">
            G
          </div>
          <div className="flex items-baseline gap-1.5">
            <span className="text-sm font-semibold tracking-tight">GAMSS</span>
            <span className="text-xs font-medium text-muted-foreground">Admin</span>
          </div>
        </div>

        <nav className="flex-1 space-y-0.5 p-3">
          <p className="px-3 pb-1.5 pt-2 text-[11px] font-medium uppercase tracking-wider text-muted-foreground/70">
            메뉴
          </p>
          {NAV.map((item) => {
            const active = !item.disabled && location.pathname.startsWith(item.to)
            const inner = (
              <span
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  item.disabled
                    ? 'cursor-not-allowed text-muted-foreground/40'
                    : active
                      ? 'bg-secondary font-medium text-foreground'
                      : 'text-muted-foreground hover:bg-secondary/60 hover:text-foreground',
                )}
              >
                <item.icon className="size-4" />
                {item.label}
                {item.disabled && (
                  <span className="ml-auto rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                    준비중
                  </span>
                )}
              </span>
            )
            return item.disabled ? (
              <div key={item.to}>{inner}</div>
            ) : (
              <Link key={item.to} to={item.to}>
                {inner}
              </Link>
            )
          })}
        </nav>

        <div className="border-t p-3">
          <div className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
            <Avatar name={identity?.name} className="size-8 text-xs" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{identity?.name ?? '관리자'}</p>
              <p className="truncate text-xs text-muted-foreground">{identity?.email ?? ''}</p>
            </div>
            <Button
              variant="ghost"
              size="icon"
              className="size-8 shrink-0 text-muted-foreground hover:text-foreground"
              onClick={() => logout()}
              title="로그아웃"
            >
              <LogOut className="size-4" />
            </Button>
          </div>
        </div>
      </aside>

      <div className="flex min-h-screen flex-col pl-64">
        <header className="sticky top-0 z-10 flex h-14 items-center justify-between border-b bg-background/80 px-8 backdrop-blur">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-muted-foreground">GAMSS Admin</span>
            {section && (
              <>
                <span className="text-muted-foreground/40">/</span>
                <span className="font-medium text-foreground">{section}</span>
              </>
            )}
          </div>
          {MOCK_MODE && (
            <span className="rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700">
              로컬 모드
            </span>
          )}
        </header>

        <main className="flex-1 px-8 py-8">
          <div className="mx-auto max-w-6xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
