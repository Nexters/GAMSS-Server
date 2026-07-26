import { useCustom } from '@refinedev/core'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { MessagesSquare, Sparkles, UserPlus, Users } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { MetricCard } from '@/components/metric-card'
import { SectionError, SeriesTooltip, shortDate } from './chart-shared'

interface EmotionCount {
  emotion: string
  label: string
  count: number
}

interface DailyActivity {
  date: string
  conversations: number
  messages: number
  cards: number
}

interface UsageStats {
  todayConversations: number
  todayUserMessages: number
  avgMessagesPerUser: number | null
  todayCards: number
  todaySignups: number
  dau: number
  wau: number
  emotionDistribution: EmotionCount[]
  dailyActivity: DailyActivity[]
}

// 감정별 고정 색상(카테고리형이라 구분되는 팔레트를 쓴다).
const EMOTION_COLOR: Record<string, string> = {
  JOY: '#f59e0b',
  ANGER: '#ef4444',
  ANXIETY: '#8b5cf6',
  GRUMPY: '#f97316',
  WARM: '#ec4899',
  QUIRKY: '#14b8a6',
}
const FALLBACK_COLOR = '#a1a1aa'
const CONVERSATION_COLOR = '#18181b'
const CARD_COLOR = '#10b981'
const MESSAGE_COLOR = '#c4b5fd'

function EmotionTooltip(props: { active?: boolean; payload?: { name?: string; value?: number }[] }) {
  if (!props.active || !props.payload?.length) {
    return null
  }
  const item = props.payload[0]
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <span className="font-medium">{item?.name}</span>
      <span className="ml-2 text-muted-foreground">{item?.value ?? 0}개</span>
    </div>
  )
}

export function UsageSection() {
  const { data, isLoading, isError, refetch } = useCustom<UsageStats>({ url: '/api/admin/dashboard/usage', method: 'get' })

  if (isError && !data?.data) {
    return <SectionError onRetry={() => refetch()} />
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-[92px]" />
          ))}
        </div>
        <div className="grid gap-4 lg:grid-cols-3">
          <Skeleton className="h-[268px] lg:col-span-1" />
          <Skeleton className="h-[268px] lg:col-span-2" />
        </div>
      </div>
    )
  }

  const stats = data?.data
  if (!stats) {
    return null
  }

  const emotionData = stats.emotionDistribution.map((e) => ({
    name: e.label,
    value: e.count,
    color: EMOTION_COLOR[e.emotion] ?? FALLBACK_COLOR,
  }))
  const hasEmotion = emotionData.some((e) => e.value > 0)
  const trend = stats.dailyActivity.map((d) => ({
    label: shortDate(d.date),
    conversations: d.conversations,
    messages: d.messages,
    cards: d.cards,
  }))

  return (
    <div className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6">
        <MetricCard label="오늘 대화" value={stats.todayConversations.toLocaleString()} icon={<MessagesSquare className="size-4" />} />
        <MetricCard
          label="유저당 평균 메시지"
          value={stats.avgMessagesPerUser === null ? '—' : stats.avgMessagesPerUser.toFixed(1)}
          hint={`유저발화 ${stats.todayUserMessages.toLocaleString()} ÷ 유저 ${stats.dau.toLocaleString()}`}
        />
        <MetricCard label="오늘 카드" value={stats.todayCards.toLocaleString()} icon={<Sparkles className="size-4" />} />
        <MetricCard label="오늘 가입" value={stats.todaySignups.toLocaleString()} icon={<UserPlus className="size-4" />} />
        <MetricCard label="DAU" value={stats.dau.toLocaleString()} hint="오늘 활동 회원" icon={<Users className="size-4" />} />
        <MetricCard label="WAU" value={stats.wau.toLocaleString()} hint="최근 7일 활동 회원" />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-1">
          <p className="text-sm font-medium">감정 분포</p>
          <p className="text-xs text-muted-foreground">최근 카드 기준</p>
          <div className="mt-2 h-[200px]">
            {hasEmotion ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={emotionData} dataKey="value" nameKey="name" innerRadius={50} outerRadius={80} paddingAngle={2} strokeWidth={0}>
                    {emotionData.map((d) => (
                      <Cell key={d.name} fill={d.color} />
                    ))}
                  </Pie>
                  <Tooltip content={<EmotionTooltip />} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-muted-foreground">데이터 없음</div>
            )}
          </div>
          <div className="mt-2 grid grid-cols-3 gap-x-2 gap-y-1 text-xs">
            {hasEmotion &&
              emotionData.map((d) => (
                <span key={d.name} className="flex min-w-0 items-center gap-1.5">
                <span className="size-2 shrink-0 rounded-full" style={{ background: d.color }} />
                <span className="truncate">
                  {d.name} {d.value.toLocaleString()}
                </span>
              </span>
            ))}
          </div>
        </Card>

        <Card className="p-5 lg:col-span-2">
          <div className="flex items-baseline justify-between">
            <p className="text-sm font-medium">활동 추이</p>
            <p className="text-xs text-muted-foreground">최근 {trend.length}일</p>
          </div>
          <div className="mt-2 h-[200px]">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trend} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
                <defs>
                  <linearGradient id="messageFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={MESSAGE_COLOR} stopOpacity={0.3} />
                    <stop offset="100%" stopColor={MESSAGE_COLOR} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="hsl(var(--border))" />
                <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#a1a1aa' }} minTickGap={16} />
                <YAxis yAxisId="left" allowDecimals={false} tickLine={false} axisLine={false} width={28} tick={{ fontSize: 11, fill: '#a1a1aa' }} />
                <YAxis yAxisId="right" orientation="right" allowDecimals={false} tickLine={false} axisLine={false} width={32} tick={{ fontSize: 11, fill: '#c4b5fd' }} />
                <Tooltip content={<SeriesTooltip />} cursor={{ stroke: 'hsl(var(--border))' }} />
                <Area yAxisId="right" type="monotone" dataKey="messages" name="메시지" stroke={MESSAGE_COLOR} strokeWidth={2} fill="url(#messageFill)" />
                <Line yAxisId="left" type="monotone" dataKey="conversations" name="대화" stroke={CONVERSATION_COLOR} strokeWidth={2} dot={false} />
                <Line yAxisId="left" type="monotone" dataKey="cards" name="카드" stroke={CARD_COLOR} strokeWidth={2} dot={false} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
          <div className="mt-2 flex items-center justify-center gap-4 text-xs text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: CONVERSATION_COLOR }} />
              대화
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: CARD_COLOR }} />
              카드
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full" style={{ background: MESSAGE_COLOR }} />
              메시지 (우측 축)
            </span>
          </div>
        </Card>
      </div>
    </div>
  )
}
