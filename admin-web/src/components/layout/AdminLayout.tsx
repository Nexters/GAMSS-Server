import { useGetIdentity, useLogout } from '@refinedev/core'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { LayoutDashboard, LogOut, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface NavItem {
  label: string
  to: string
  icon: typeof Users
  disabled?: boolean
}

const NAV: NavItem[] = [
  { label: '대시보드', to: '/dashboard', icon: LayoutDashboard, disabled: true },
  { label: '회원 관리', to: '/members', icon: Users },
]

interface Identity {
  name: string
  email: string
}

export function AdminLayout() {
  const location = useLocation()
  const { mutate: logout } = useLogout()
  const { data: identity } = useGetIdentity<Identity>()

  return (
    <div className="flex min-h-screen bg-muted/30">
      <aside className="fixed inset-y-0 left-0 flex w-60 flex-col border-r bg-background">
        <div className="flex h-14 items-center gap-2 border-b px-6">
          <div className="flex h-6 w-6 items-center justify-center rounded bg-primary text-[11px] font-bold text-primary-foreground">
            G
          </div>
          <span className="text-sm font-semibold tracking-tight">GAMSS Admin</span>
        </div>

        <nav className="flex-1 space-y-1 p-3">
          {NAV.map((item) => {
            const active = location.pathname.startsWith(item.to)
            const content = (
              <span
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  item.disabled
                    ? 'cursor-not-allowed text-muted-foreground/50'
                    : active
                      ? 'bg-accent font-medium text-accent-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )}
              >
                <item.icon className="size-4" />
                {item.label}
                {item.disabled && <span className="ml-auto text-[10px]">준비중</span>}
              </span>
            )
            return item.disabled ? (
              <div key={item.to}>{content}</div>
            ) : (
              <Link key={item.to} to={item.to}>
                {content}
              </Link>
            )
          })}
        </nav>

        <div className="border-t p-3">
          <div className="mb-2 px-3">
            <p className="truncate text-sm font-medium">{identity?.name ?? '관리자'}</p>
            <p className="truncate text-xs text-muted-foreground">{identity?.email ?? ''}</p>
          </div>
          <Button variant="ghost" size="sm" className="w-full justify-start text-muted-foreground" onClick={() => logout()}>
            <LogOut className="size-4" />
            로그아웃
          </Button>
        </div>
      </aside>

      <div className="flex flex-1 flex-col pl-60">
        <main className="flex-1 p-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
