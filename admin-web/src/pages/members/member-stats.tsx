import { useCustom } from '@refinedev/core'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

interface DailySignup {
  date: string
  count: number
}

interface MemberStatsData {
  total: number
  active: number
  withdrawn: number
  dailySignups: DailySignup[]
}

const ACTIVE_COLOR = '#10b981'
const WITHDRAWN_COLOR = '#e4e4e7'
const LINE_COLOR = '#18181b'

function TrendTooltip(props: { active?: boolean; payload?: { value?: number }[]; label?: string }) {
  if (!props.active || !props.payload?.length) {
    return null
  }
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <p className="font-medium">{props.label}</p>
      <p className="text-muted-foreground">{props.payload[0]?.value ?? 0}명 가입</p>
    </div>
  )
}

function RatioTooltip(props: { active?: boolean; payload?: { name?: string; value?: number }[] }) {
  if (!props.active || !props.payload?.length) {
    return null
  }
  const item = props.payload[0]
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <span className="font-medium">{item?.name}</span>
      <span className="ml-2 text-muted-foreground">{item?.value ?? 0}명</span>
    </div>
  )
}

function Metric({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card className="p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1.5 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </Card>
  )
}

export function MemberStats() {
  const { data, isLoading } = useCustom<MemberStatsData>({
    url: '/api/admin/members/stats',
    method: 'get',
  })

  if (isLoading) {
    return (
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-[104px] lg:col-span-1" />
        <Skeleton className="h-[104px] lg:col-span-1" />
        <Skeleton className="h-[104px] lg:col-span-1" />
        <Skeleton className="h-[264px] lg:col-span-1" />
        <Skeleton className="h-[264px] lg:col-span-2" />
      </div>
    )
  }

  const stats = data?.data
  if (!stats) {
    return null
  }

  const { total, active, withdrawn } = stats
  const activeRatio = total > 0 ? Math.round((active / total) * 100) : 0
  // 회원이 0명이면 비율이 무의미하므로 —로 표시한다(0명인데 "전체의 100%" 같은 모순 방지).
  const activeHint = total > 0 ? `전체의 ${activeRatio}%` : '—'
  const withdrawnHint = total > 0 ? `전체의 ${100 - activeRatio}%` : '—'
  const pieData = [
    { name: '활성', value: active, color: ACTIVE_COLOR },
    { name: '탈퇴', value: withdrawn, color: WITHDRAWN_COLOR },
  ]
  const trend = stats.dailySignups.map((d) => {
    const [, month, day] = d.date.split('-')
    return { label: `${Number(month)}/${Number(day)}`, count: d.count }
  })

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3">
        <Metric label="전체 회원" value={total.toLocaleString()} />
        <Metric label="활성 회원" value={active.toLocaleString()} hint={activeHint} />
        <Metric label="탈퇴 회원" value={withdrawn.toLocaleString()} hint={withdrawnHint} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-1">
          <p className="text-sm font-medium">회원 상태 비율</p>
          <div className="relative mt-2 h-[200px]">
            {total > 0 ? (
              <>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieData}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={62}
                      outerRadius={86}
                      paddingAngle={pieData.every((d) => d.value > 0) ? 2 : 0}
                      strokeWidth={0}
                      startAngle={90}
                      endAngle={-270}
                    >
                      {pieData.map((d) => (
                        <Cell key={d.name} fill={d.color} />
                      ))}
                    </Pie>
                    <Tooltip content={<RatioTooltip />} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="text-2xl font-semibold tracking-tight tabular-nums">{activeRatio}%</span>
                  <span className="text-xs text-muted-foreground">활성</span>
                </div>
              </>
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                데이터 없음
              </div>
            )}
          </div>
          <div className="mt-2 flex items-center justify-center gap-4 text-xs">
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: ACTIVE_COLOR }} />
              활성 {active.toLocaleString()}
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: WITHDRAWN_COLOR }} />
              탈퇴 {withdrawn.toLocaleString()}
            </span>
          </div>
        </Card>

        <Card className="p-5 lg:col-span-2">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-medium">가입 추이</p>
            <p className="text-xs text-muted-foreground">최근 {trend.length}일</p>
          </div>
          <div className="mt-2 h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                <defs>
                  <linearGradient id="signupFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={LINE_COLOR} stopOpacity={0.12} />
                    <stop offset="100%" stopColor={LINE_COLOR} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} tick={{ fontSize: 11, fill: '#a1a1aa' }} />
                <Tooltip content={<TrendTooltip />} cursor={{ stroke: 'hsl(var(--border))' }} />
                <Area type="monotone" dataKey="count" stroke={LINE_COLOR} strokeWidth={2} fill="url(#signupFill)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>
    </div>
  )
}
