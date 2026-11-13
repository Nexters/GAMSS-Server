import { useCustom } from '@refinedev/core'
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { AlertTriangle, ArrowDownRight, ArrowUpRight, Minus, ShieldCheck } from 'lucide-react'
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
    return <span className="text-xs text-muted-foreground">이전 기간 데이터 없음</span>
  }
  const diff = Math.round((current - previous) * 10) / 10
  const Icon = diff > 0 ? ArrowUpRight : diff < 0 ? ArrowDownRight : Minus
  return (
    <span
      className={cn(
        'inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-medium',
        diff > 0 ? 'bg-rose-50 text-rose-700' : diff < 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-muted text-muted-foreground',
      )}
    >
      <Icon className="size-3.5" />
      {diff === 0 ? '변화 없음' : `${Math.abs(diff)}%p`}
      <span className="ml-1 font-normal opacity-70">이전 기간 대비</span>
    </span>
  )
}

function TrustBadge({ agreement, pairs }: { agreement: number | null; pairs: number }) {
  if (agreement === null) {
    return (
      <div className="flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
        <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
        <span>사람 판정이 없어 AI 채점을 믿을 수 있는지 아직 모릅니다. 판정하기에서 정답 세트를 만들어 주세요.</span>
      </div>
    )
  }
  const weak = pairs < MIN_TRUSTED_PAIRS || agreement < 70
  return (
    <div className={cn('flex items-start gap-2 rounded-lg px-3 py-2 text-xs', weak ? 'bg-amber-50 text-amber-800' : 'bg-emerald-50 text-emerald-800')}>
      {weak ? <AlertTriangle className="mt-0.5 size-3.5 shrink-0" /> : <ShieldCheck className="mt-0.5 size-3.5 shrink-0" />}
      <span>
        AI-사람 일치율 <span className="font-semibold">{agreement}%</span> · {pairs}쌍 비교
        {pairs < MIN_TRUSTED_PAIRS ? ` · 표본이 ${MIN_TRUSTED_PAIRS}쌍보다 적어 아직 참고만 하세요` : agreement < 70 ? ' · 낮아서 위 숫자를 믿기 어렵습니다' : ''}
      </span>
    </div>
  )
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
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-[248px] lg:col-span-2" />
        <Skeleton className="h-[248px]" />
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
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="flex flex-col p-6 lg:col-span-2">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">어색한 응답 비율</p>
              <div className="flex flex-wrap items-baseline gap-3">
                <span className={cn('text-5xl font-semibold tracking-tight tabular-nums', stats.awkwardRate !== null && stats.awkwardRate >= 20 && 'text-rose-600')}>
                  {stats.awkwardRate === null ? '-' : `${stats.awkwardRate}%`}
                </span>
                <Delta current={stats.awkwardRate} previous={stats.previousAwkwardRate} />
              </div>
              <p className="text-xs text-muted-foreground">
                최근 {stats.days}일 · AI가 채점한 응답 {stats.judgedCount.toLocaleString()}건 중 {stats.awkwardCount.toLocaleString()}건
              </p>
            </div>
            <div className="flex flex-wrap gap-3 text-xs">
              {(Object.keys(MARKER_META) as MarkerKind[]).map((kind) => (
                <span key={kind} className="flex items-center gap-1.5 text-muted-foreground">
                  <span className="h-3 w-0.5 rounded-full" style={{ background: MARKER_META[kind].color }} />
                  {MARKER_META[kind].label}
                </span>
              ))}
            </div>
          </div>

          <div className="mt-4 h-[180px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trend} margin={{ top: 16, right: 8, bottom: 0, left: 0 }}>
                <defs>
                  <linearGradient id="rateFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={RATE_COLOR} stopOpacity={0.18} />
                    <stop offset="100%" stopColor={RATE_COLOR} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
                <YAxis unit="%" tickLine={false} axisLine={false} width={44} tick={{ fontSize: 11, fill: '#a1a1aa' }} allowDecimals={false} />
                <Tooltip content={<RateTooltip />} cursor={{ stroke: 'hsl(var(--border))' }} />
                {markersByDate.map(([date, markers]) => {
                  const kind = markers.some((m) => m.kind === 'JUDGE') ? 'JUDGE' : 'GENERATION'
                  return (
                    <ReferenceLine
                      key={date}
                      x={shortDate(date)}
                      stroke={MARKER_META[kind].color}
                      strokeDasharray={kind === 'JUDGE' ? '4 3' : undefined}
                      label={{ value: '●', position: 'top', fill: MARKER_META[kind].color, fontSize: 9 }}
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
                  dot={{ r: 2.5, fill: RATE_COLOR, strokeWidth: 0 }}
                  activeDot={{ r: 4 }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          {markersByDate.length > 0 && (
            <div className="mt-3 flex flex-wrap gap-2">
              {markersByDate.map(([date, markers]) => (
                <span key={date} className="inline-flex items-center gap-1.5 rounded-md border bg-muted/30 px-2 py-1 text-[11px] text-muted-foreground">
                  <span className="font-medium text-foreground">{shortDate(date)}</span>
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

        <Card className="flex flex-col gap-4 p-6">
          <div>
            <p className="text-sm text-muted-foreground">채점이 믿을 만한가</p>
            <div className="mt-2">
              <TrustBadge agreement={stats.aiHumanAgreement} pairs={stats.agreementPairs} />
            </div>
          </div>
          <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <div>
              <dt className="text-xs text-muted-foreground">채점 모델</dt>
              <dd className="mt-0.5 truncate font-medium" title={stats.judgeModel}>
                {stats.judgeModel}
              </dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">채점 프롬프트</dt>
              <dd className="mt-0.5 font-medium">v{stats.judgePromptVersion}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">채점 비용</dt>
              <dd className="mt-0.5 font-medium tabular-nums">{formatUsd(stats.judgeCostUsd)}</dd>
              <dd className="text-[11px] text-muted-foreground">≈ {formatKrw(stats.judgeCostUsd)}</dd>
            </div>
            <div>
              <dt className="text-xs text-muted-foreground">채점된 응답</dt>
              <dd className="mt-0.5 font-medium tabular-nums">{stats.judgedCount.toLocaleString()}건</dd>
            </div>
          </dl>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <Card className="p-6">
          <div className="flex items-baseline justify-between gap-3">
            <p className="text-sm font-medium">캐릭터별</p>
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
                    <span>{meta.emoji}</span>
                    {meta.label}
                    {worst === c.emotionType && <span className="rounded bg-rose-50 px-1 text-[10px] font-medium text-rose-600">가장 높음</span>}
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

        <Card className="p-6">
          <p className="text-sm font-medium">어색함 유형</p>
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
