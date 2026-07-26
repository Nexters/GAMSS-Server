import { useCustom } from '@refinedev/core'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AlertTriangle, Coins, Gauge, RefreshCw } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { MetricCard } from '@/components/metric-card'

interface DailyGeneration {
  date: string
  success: number
  failed: number
}

interface QualityStats {
  totalGenerations: number
  successGenerations: number
  failedGenerations: number
  successRate: number | null
  totalLlmCalls: number
  retryRate: number | null
  avgLatencyMs: number
  p95LatencyMs: number
  totalTokens: number
  stuckPending: number
  dailyGeneration: DailyGeneration[]
}

const SUCCESS_COLOR = '#10b981'
const FAILED_COLOR = '#ef4444'

function latency(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

function percent(value: number | null): string {
  if (value === null) {
    return '—'
  }
  return `${value}%`
}

function short(date: string): string {
  const [, month, day] = date.split('-')
  return `${Number(month)}/${Number(day)}`
}

function GenerationTooltip(props: {
  active?: boolean
  payload?: { name?: string; value?: number; color?: string }[]
  label?: string
}) {
  if (!props.active || !props.payload?.length) {
    return null
  }
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <p className="mb-1 font-medium">{props.label}</p>
      {props.payload.map((p) => (
        <p key={p.name} className="flex items-center gap-1.5 text-muted-foreground">
          <span className="size-2 rounded-full" style={{ background: p.color }} />
          {p.name} {p.value ?? 0}
        </p>
      ))}
    </div>
  )
}

export function QualitySection() {
  const { data, isLoading } = useCustom<QualityStats>({ url: '/api/admin/dashboard/quality', method: 'get' })

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-[92px]" />
          ))}
        </div>
        <Skeleton className="h-[248px]" />
      </div>
    )
  }

  const stats = data?.data
  if (!stats) {
    return null
  }

  const successTone = stats.successRate === null ? 'default' : stats.successRate < 90 ? 'danger' : 'success'
  const pendingTone = stats.stuckPending > 0 ? 'danger' : 'default'
  const trend = stats.dailyGeneration.map((d) => ({ label: short(d.date), success: d.success, failed: d.failed }))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="생성 성공률"
          value={percent(stats.successRate)}
          tone={successTone}
          hint={`성공 ${stats.successGenerations.toLocaleString()} / 총 ${stats.totalGenerations.toLocaleString()}`}
        />
        <MetricCard
          label="실패한 생성"
          value={stats.failedGenerations.toLocaleString()}
          tone={stats.failedGenerations > 0 ? 'danger' : 'default'}
        />
        <MetricCard
          label="막힌 PENDING"
          value={stats.stuckPending.toLocaleString()}
          tone={pendingTone}
          hint={pendingTone === 'danger' ? '고아 생성 — 확인 필요' : '고아 생성 없음'}
          icon={<AlertTriangle className="size-4" />}
        />
        <MetricCard
          label="재시도율"
          value={percent(stats.retryRate)}
          hint={`LLM 호출 ${stats.totalLlmCalls.toLocaleString()}회`}
          icon={<RefreshCw className="size-4" />}
        />
        <MetricCard label="평균 지연" value={latency(stats.avgLatencyMs)} icon={<Gauge className="size-4" />} />
        <MetricCard label="p95 지연" value={latency(stats.p95LatencyMs)} hint="상위 5% 대기시간" />
        <MetricCard label="토큰 사용량" value={stats.totalTokens.toLocaleString()} hint="최근 기간 누적" icon={<Coins className="size-4" />} />
        <MetricCard label="총 생성 요청" value={stats.totalGenerations.toLocaleString()} hint="최근 기간" />
      </div>

      <Card className="p-5">
        <div className="flex items-baseline justify-between">
          <p className="text-sm font-medium">생성 성공·실패 추이</p>
          <p className="text-xs text-muted-foreground">최근 {trend.length}일</p>
        </div>
        <div className="mt-2 h-[200px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
              <YAxis allowDecimals={false} tickLine={false} axisLine={false} width={28} tick={{ fontSize: 11, fill: '#a1a1aa' }} />
              <Tooltip content={<GenerationTooltip />} cursor={{ fill: 'hsl(var(--muted))', opacity: 0.4 }} />
              <Bar dataKey="success" name="성공" stackId="gen" fill={SUCCESS_COLOR} radius={[0, 0, 0, 0]} />
              <Bar dataKey="failed" name="실패" stackId="gen" fill={FAILED_COLOR} radius={[2, 2, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-2 flex items-center justify-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full" style={{ background: SUCCESS_COLOR }} />
            성공
          </span>
          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full" style={{ background: FAILED_COLOR }} />
            실패
          </span>
        </div>
      </Card>
    </div>
  )
}
