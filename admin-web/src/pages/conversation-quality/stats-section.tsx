import { useCustom } from '@refinedev/core'
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AlertTriangle, ArrowDownRight, ArrowUpRight, Coins, MessageCircleWarning, Minus, ShieldCheck } from 'lucide-react'
import { MetricCard } from '@/components/metric-card'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatKrw, formatUsd } from '@/lib/currency'
import { AWKWARD_TYPE_META, CHARACTER_META, type AwkwardType, type Emotion } from '@/lib/quality'
import { cn } from '@/lib/utils'
import { SectionError, shortDate } from '@/pages/dashboard/chart-shared'

type MarkerKind = 'GENERATION' | 'JUDGE'

interface Stats {
  days: number
  judgedCount: number
  awkwardCount: number
  awkwardRate: number | null
  previousAwkwardRate: number | null
  byCharacter: { emotionType: Emotion; judged: number; awkward: number; rate: number | null }[]
  byType: { type: AwkwardType; count: number }[]
  daily: { date: string; judged: number; awkward: number; rate: number | null }[]
  markers: { date: string; kind: MarkerKind; target: string; value: string }[]
  aiHumanAgreement: number | null
  agreementPairs: number
  judgeModel: string
  judgePromptVersion: number
  judgeCostUsd: number
}

const RATE_COLOR = '#e11d48'
const MARKER_META: Record<MarkerKind, { color: string; label: string }> = {
  GENERATION: { color: '#6366f1', label: '생성 설정 변경' },
  JUDGE: { color: '#d97706', label: '채점 기준 변경' },
}
const TARGET_LABEL: Record<string, string> = {
  COMMON: '공통 프롬프트',
  COMMENT: '댓글 프롬프트',
  REPLY: '답글 프롬프트',
  QUALITY_JUDGE: '채점 프롬프트',
  APP_MODEL: '앱 모델',
  JUDGE_MODEL: '채점 모델',
}
// 이보다 적은 쌍으로 잰 일치율은 우연에 크게 흔들린다.
const MIN_TRUSTED_PAIRS = 30

function Delta({ current, previous }: { current: number | null; previous: number | null }) {
  if (current === null || previous === null) {
    return <>이전 기간 데이터 없음</>
  }
  const diff = Math.round((current - previous) * 10) / 10
  const Icon = diff > 0 ? ArrowUpRight : diff < 0 ? ArrowDownRight : Minus
  return (
    <span className="inline-flex items-center gap-1">
      <span className={cn('inline-flex items-center font-medium', diff > 0 ? 'text-rose-600' : diff < 0 ? 'text-emerald-600' : 'text-muted-foreground')}>
        <Icon className="size-3.5" />
        {diff === 0 ? '변화 없음' : `${Math.abs(diff)}%p`}
      </span>
      이전 기간 대비
    </span>
  )
}

function AgreementHint({ agreement, pairs }: { agreement: number | null; pairs: number }) {
  if (agreement === null) {
    return <span className="text-amber-700">사람 판정이 없어 아직 모릅니다</span>
  }
  if (pairs < MIN_TRUSTED_PAIRS) {
    return (
      <span className="inline-flex items-center gap-1 text-amber-700">
        <AlertTriangle className="size-3.5 shrink-0" />
        {pairs}쌍 비교 · 표본이 적어 참고만
      </span>
    )
  }
  return agreement < 70 ? <span className="text-amber-700">{pairs}쌍 비교 · 낮아서 비율을 믿기 어렵습니다</span> : <>{pairs}쌍 비교</>
}

function RateTooltip(props: { active?: boolean; payload?: { payload: { date: string; rate: number | null; judged: number; awkward: number } }[] }) {
  const point = props.payload?.[0]?.payload
  if (!props.active || !point) {
    return null
  }
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <p className="font-medium">{point.date}</p>
      <p className="text-muted-foreground">
        {point.rate === null ? '채점된 응답 없음' : `어색함 ${point.rate}% · ${point.awkward} / ${point.judged}건`}
      </p>
    </div>
  )
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
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-[92px]" />
          ))}
        </div>
        <Skeleton className="h-[260px]" />
      </div>
    )
  }

  const trend = stats.daily.map((d) => ({ ...d, label: shortDate(d.date) }))
  const markersByDate = Object.entries(
    stats.markers.reduce<Record<string, Stats['markers']>>((acc, m) => {
      ;(acc[m.date] ??= []).push(m)
      return acc
    }, {}),
  )
  const characters = [...stats.byCharacter].sort((a, b) => (b.rate ?? -1) - (a.rate ?? -1))
  const worst = characters[0]?.rate ? characters[0].emotionType : null
  const maxTypeCount = Math.max(1, ...stats.byType.map((t) => t.count))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <MetricCard
          label="어색한 응답 비율"
          value={stats.awkwardRate === null ? '-' : `${stats.awkwardRate}%`}
          tone={stats.awkwardRate !== null && stats.awkwardRate >= 20 ? 'danger' : 'default'}
          hint={<Delta current={stats.awkwardRate} previous={stats.previousAwkwardRate} />}
          icon={<MessageCircleWarning className="size-4" />}
        />
        <MetricCard label="어색한 응답" value={stats.awkwardCount.toLocaleString()} hint={`AI가 채점한 ${stats.judgedCount.toLocaleString()}건 중`} />
        <MetricCard
          label="AI-사람 일치율"
          value={stats.aiHumanAgreement === null ? '-' : `${stats.aiHumanAgreement}%`}
          tone={stats.aiHumanAgreement !== null && stats.agreementPairs >= MIN_TRUSTED_PAIRS && stats.aiHumanAgreement >= 70 ? 'success' : 'default'}
          hint={<AgreementHint agreement={stats.aiHumanAgreement} pairs={stats.agreementPairs} />}
          icon={<ShieldCheck className="size-4" />}
        />
        <MetricCard
          label="채점 비용"
          value={formatUsd(stats.judgeCostUsd)}
          hint={
            <span className="block truncate" title={`${stats.judgeModel} · 채점 프롬프트 v${stats.judgePromptVersion}`}>
              ≈ {formatKrw(stats.judgeCostUsd)} · {stats.judgeModel} · v{stats.judgePromptVersion}
            </span>
          }
          icon={<Coins className="size-4" />}
        />
      </div>

      <Card className="p-5">
        <div className="flex items-baseline justify-between">
          <p className="text-sm font-medium">어색한 응답 비율 추이</p>
          <p className="text-xs text-muted-foreground">최근 {trend.length}일</p>
        </div>
        <div className="mt-2 h-[200px]">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="rateFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={RATE_COLOR} stopOpacity={0.16} />
                  <stop offset="100%" stopColor={RATE_COLOR} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
              <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
              <YAxis unit="%" tickLine={false} axisLine={false} width={40} tick={{ fontSize: 11, fill: '#a1a1aa' }} allowDecimals={false} />
              <Tooltip content={<RateTooltip />} cursor={{ stroke: 'hsl(var(--border))' }} />
              {markersByDate.map(([date, markers]) => {
                const kind = markers.some((m) => m.kind === 'JUDGE') ? 'JUDGE' : 'GENERATION'
                return (
                  <ReferenceLine
                    key={date}
                    x={shortDate(date)}
                    stroke={MARKER_META[kind].color}
                    strokeOpacity={0.7}
                    strokeDasharray={kind === 'JUDGE' ? '4 3' : undefined}
                  />
                )
              })}
              <Area
                type="linear"
                dataKey="rate"
                stroke={RATE_COLOR}
                strokeWidth={2}
                fill="url(#rateFill)"
                connectNulls
                isAnimationActive={false}
                dot={{ r: 2.5, fill: RATE_COLOR, strokeWidth: 0 }}
                activeDot={{ r: 4 }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-2 flex flex-wrap items-center justify-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full" style={{ background: RATE_COLOR }} />
            어색한 응답 비율
          </span>
          {(Object.keys(MARKER_META) as MarkerKind[]).map((kind) => (
            <span key={kind} className="flex items-center gap-1.5">
              <span className="h-3 w-0.5 rounded-full" style={{ background: MARKER_META[kind].color }} />
              {MARKER_META[kind].label}
            </span>
          ))}
        </div>
        {markersByDate.length > 0 && (
          <div className="mt-4 flex flex-wrap gap-2 border-t pt-4">
            {markersByDate.map(([date, markers]) => (
              <span key={date} className="inline-flex items-center gap-2 rounded-md border px-2 py-1 text-xs text-muted-foreground">
                <span className="font-medium tabular-nums text-foreground">{shortDate(date)}</span>
                {markers.map((m) => (
                  <span key={m.target} className="inline-flex items-center gap-1">
                    <span className="size-1.5 rounded-full" style={{ background: MARKER_META[m.kind].color }} />
                    {TARGET_LABEL[m.target] ?? m.target} {m.value}
                  </span>
                ))}
              </span>
            ))}
          </div>
        )}
      </Card>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card className="p-5">
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-sm font-medium">캐릭터별 어색한 비율</p>
            <p className="text-xs text-muted-foreground">{selectedCharacter ? '다시 누르면 필터를 해제합니다' : '누르면 아래 목록이 걸러집니다'}</p>
          </div>
          <div className="mt-4 space-y-1">
            {characters.map((c) => {
              const meta = CHARACTER_META[c.emotionType]
              const active = selectedCharacter === c.emotionType
              const dimmed = selectedCharacter !== '' && !active
              return (
                <button
                  key={c.emotionType}
                  type="button"
                  onClick={() => onSelectCharacter(active ? '' : c.emotionType)}
                  className={cn(
                    'grid w-full grid-cols-[8.5rem_1fr_7.5rem] items-center gap-3 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-muted/60',
                    active && 'bg-muted',
                    dimmed && 'opacity-50',
                  )}
                >
                  <span className="flex items-center gap-1.5">
                    <span className="size-2 rounded-full" style={{ background: meta.color }} />
                    {meta.label}
                    {worst === c.emotionType && <span className="rounded-full bg-rose-50 px-1.5 py-px text-[10px] font-medium text-rose-700 ring-1 ring-inset ring-rose-600/10">가장 높음</span>}
                  </span>
                  <span className="relative h-2 overflow-hidden rounded-full bg-muted">
                    <span className="absolute inset-y-0 left-0 rounded-full" style={{ width: `${c.rate ?? 0}%`, background: meta.color }} />
                  </span>
                  <span className="text-right tabular-nums">
                    <span className="font-medium">{c.rate === null ? '-' : `${c.rate}%`}</span>
                    <span className="ml-1.5 text-xs text-muted-foreground">
                      {c.awkward}/{c.judged}
                    </span>
                  </span>
                </button>
              )
            })}
          </div>
        </Card>

        <Card className="p-5">
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-sm font-medium">어색함 유형</p>
            <p className="text-xs text-muted-foreground">한 응답에 여러 유형이 붙을 수 있습니다</p>
          </div>
          <div className="mt-4 space-y-2.5">
            {stats.byType.map((t) => (
              <div key={t.type} className={cn('grid grid-cols-[8.5rem_1fr_2.5rem] items-center gap-3 text-sm', t.count === 0 && 'opacity-40')} title={AWKWARD_TYPE_META[t.type].hint}>
                <span className="truncate">{AWKWARD_TYPE_META[t.type].label}</span>
                <span className="relative h-2 overflow-hidden rounded-full bg-muted">
                  <span className="absolute inset-y-0 left-0 rounded-full bg-rose-400" style={{ width: `${(t.count / maxTypeCount) * 100}%` }} />
                </span>
                <span className="text-right text-xs tabular-nums text-muted-foreground">{t.count}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}
