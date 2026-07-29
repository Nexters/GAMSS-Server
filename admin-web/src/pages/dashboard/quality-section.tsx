import { useCustom } from '@refinedev/core'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AlertTriangle, Coins, Gauge, RefreshCw } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { MetricCard } from '@/components/metric-card'
import { formatKrw, formatUsd, USD_TO_KRW_LABEL } from '@/lib/currency'
import { SectionError, SeriesTooltip, shortDate } from './chart-shared'

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
  cachedTokens: number
  cacheHitRate: number | null
  estimatedCostUsd: number
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

export function QualitySection() {
  const { data, isLoading, isError, refetch } = useCustom<QualityStats>({ url: '/api/admin/dashboard/quality', method: 'get' })

  if (isError && !data?.data) {
    return <SectionError onRetry={() => refetch()} />
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 10 }).map((_, i) => (
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
  const trend = stats.dailyGeneration.map((d) => ({ label: shortDate(d.date), success: d.success, failed: d.failed }))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">
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
        <MetricCard
          label="예상 비용"
          value={formatUsd(stats.estimatedCostUsd)}
          hint={`≈ ${formatKrw(stats.estimatedCostUsd)} · 환율 ${USD_TO_KRW_LABEL}원`}
          icon={<Coins className="size-4" />}
        />
        <MetricCard label="총 토큰" value={stats.totalTokens.toLocaleString()} hint="처리된 전체 토큰(입력+출력)" />
        <MetricCard
          label="캐시 토큰"
          value={stats.cachedTokens.toLocaleString()}
          hint={stats.cacheHitRate === null ? '캐시 적중 없음' : `입력의 ${stats.cacheHitRate}% 적중(할인 과금)`}
        />
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
              <Tooltip content={<SeriesTooltip />} cursor={{ fill: 'hsl(var(--muted))', opacity: 0.4 }} />
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
