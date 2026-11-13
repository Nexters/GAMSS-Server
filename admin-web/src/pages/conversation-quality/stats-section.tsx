import { useCustom } from '@refinedev/core'
import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { MetricCard } from '@/components/metric-card'
import { formatKrw, formatUsd } from '@/lib/currency'
import { AWKWARD_TYPE_META, CHARACTER_META, type AwkwardType, type Emotion } from '@/lib/quality'
import { cn } from '@/lib/utils'
import { SectionError, SeriesTooltip, shortDate } from '@/pages/dashboard/chart-shared'

interface Stats {
  days: number
  judgedCount: number
  awkwardCount: number
  awkwardRate: number | null
  previousAwkwardRate: number | null
  byCharacter: { emotionType: Emotion; judged: number; awkward: number; rate: number | null }[]
  byType: { type: AwkwardType; count: number }[]
  daily: { date: string; judged: number; awkward: number; rate: number | null }[]
  markers: { date: string; kind: 'GENERATION' | 'JUDGE'; label: string }[]
  aiHumanAgreement: number | null
  agreementPairs: number
  judgeModel: string
  judgePromptVersion: number
  judgeCostUsd: number
}

const RATE_COLOR = '#ef4444'
const MARKER_COLOR = { GENERATION: '#6366f1', JUDGE: '#f59e0b' }

function delta(current: number | null, previous: number | null) {
  if (current === null || previous === null) {
    return '이전 기간과 비교할 수 없음'
  }
  const diff = Math.round((current - previous) * 10) / 10
  if (diff === 0) {
    return '이전 기간과 같음'
  }
  return `이전 기간보다 ${diff > 0 ? '▲' : '▼'} ${Math.abs(diff)}%p`
}

export function StatsSection({
  days,
  selectedCharacter,
  onSelectCharacter,
}: {
  days: number
  selectedCharacter: Emotion | ''
  onSelectCharacter: (emotion: Emotion | '') => void
}) {
  const { data, isLoading, isError, refetch } = useCustom<Stats>({ url: `/api/admin/conversation-quality/stats?days=${days}`, method: 'get' })
  const stats = data?.data

  if (isError && !stats) {
    return <SectionError onRetry={() => refetch()} />
  }
  if (isLoading || !stats) {
    return <Skeleton className="h-[360px]" />
  }

  const trend = stats.daily.map((d) => ({ label: shortDate(d.date), rate: d.rate }))
  const maxTypeCount = Math.max(1, ...stats.byType.map((t) => t.count))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="어색한 응답 비율"
          value={stats.awkwardRate === null ? '-' : `${stats.awkwardRate}%`}
          tone={stats.awkwardRate !== null && stats.awkwardRate >= 20 ? 'danger' : 'default'}
          hint={delta(stats.awkwardRate, stats.previousAwkwardRate)}
        />
        <MetricCard label="AI가 채점한 응답" value={stats.judgedCount.toLocaleString()} hint={`어색함 ${stats.awkwardCount.toLocaleString()}건`} />
        <MetricCard
          label="AI-사람 일치율"
          value={stats.aiHumanAgreement === null ? '-' : `${stats.aiHumanAgreement}%`}
          tone={stats.aiHumanAgreement !== null && stats.aiHumanAgreement < 70 ? 'danger' : 'default'}
          hint={
            stats.agreementPairs === 0
              ? '사람 판정이 아직 없어 믿을 수 있는지 모릅니다'
              : `${stats.agreementPairs}쌍 비교 · 낮으면 위 숫자를 믿지 마세요`
          }
        />
        <MetricCard
          label="채점 비용"
          value={formatUsd(stats.judgeCostUsd)}
          hint={`≈ ${formatKrw(stats.judgeCostUsd)} · ${stats.judgeModel} · 채점 프롬프트 v${stats.judgePromptVersion}`}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card className="p-5">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-medium">캐릭터별 어색한 응답 비율</p>
            <p className="text-xs text-muted-foreground">막대를 누르면 아래 목록이 그 캐릭터로 걸러집니다</p>
          </div>
          <div className="mt-3 space-y-2">
            {stats.byCharacter.map((c) => {
              const meta = CHARACTER_META[c.emotionType]
              const active = selectedCharacter === c.emotionType
              return (
                <button
                  key={c.emotionType}
                  type="button"
                  onClick={() => onSelectCharacter(active ? '' : c.emotionType)}
                  className={cn('flex w-full items-center gap-3 rounded-md px-2 py-1 text-left text-sm hover:bg-muted/60', active && 'bg-muted')}
                >
                  <span className="w-16 shrink-0">
                    {meta.emoji} {meta.label}
                  </span>
                  <span className="relative h-2.5 flex-1 overflow-hidden rounded-full bg-muted">
                    <span className="absolute inset-y-0 left-0 rounded-full" style={{ width: `${c.rate ?? 0}%`, background: meta.color }} />
                  </span>
                  <span className="w-28 shrink-0 text-right text-xs tabular-nums text-muted-foreground">
                    {c.rate === null ? '-' : `${c.rate}%`} ({c.awkward}/{c.judged})
                  </span>
                </button>
              )
            })}
          </div>
        </Card>

        <Card className="p-5">
          <p className="text-sm font-medium">어색함 유형 순위</p>
          <div className="mt-3 space-y-2">
            {stats.byType.map((t) => (
              <div key={t.type} className="flex items-center gap-3 text-sm" title={AWKWARD_TYPE_META[t.type].hint}>
                <span className="w-28 shrink-0">{AWKWARD_TYPE_META[t.type].label}</span>
                <span className="relative h-2 flex-1 overflow-hidden rounded-full bg-muted">
                  <span className="absolute inset-y-0 left-0 rounded-full bg-red-400" style={{ width: `${(t.count / maxTypeCount) * 100}%` }} />
                </span>
                <span className="w-10 text-right text-xs tabular-nums text-muted-foreground">{t.count}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card className="p-5">
        <div className="flex items-baseline justify-between">
          <p className="text-sm font-medium">일별 어색한 응답 비율</p>
          <div className="flex gap-3 text-xs text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-0.5" style={{ background: MARKER_COLOR.GENERATION }} />
              생성 설정 변경
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-3 w-0.5" style={{ background: MARKER_COLOR.JUDGE }} />
              채점 기준 변경
            </span>
          </div>
        </div>
        <div className="mt-2 h-[220px]">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
              <YAxis unit="%" tickLine={false} axisLine={false} width={40} tick={{ fontSize: 11, fill: '#a1a1aa' }} />
              <Tooltip content={<SeriesTooltip />} />
              {stats.markers.map((m, i) => (
                <ReferenceLine key={`${m.date}-${i}`} x={shortDate(m.date)} stroke={MARKER_COLOR[m.kind]} strokeDasharray={m.kind === 'JUDGE' ? '4 3' : undefined} />
              ))}
              <Line type="monotone" dataKey="rate" name="어색함(%)" stroke={RATE_COLOR} strokeWidth={2} dot={false} connectNulls />
            </LineChart>
          </ResponsiveContainer>
        </div>
        {stats.markers.length > 0 && (
          <ul className="mt-3 space-y-1 text-xs text-muted-foreground">
            {stats.markers.map((m, i) => (
              <li key={`${m.date}-${i}`} className="flex items-center gap-2">
                <span className="h-3 w-0.5" style={{ background: MARKER_COLOR[m.kind] }} />
                {shortDate(m.date)} {m.label}
                {m.kind === 'JUDGE' && <span className="text-amber-600">(이 날 전후 숫자는 판정 기준이 달라 비교하면 안 됩니다)</span>}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
