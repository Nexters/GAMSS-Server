import { useState } from 'react'
import { useCustom } from '@refinedev/core'
import { ChevronLeft, ChevronRight, MessagesSquare, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { TypeChip, VerdictBadge } from '@/components/verdict-badge'
import { AWKWARD_TYPES, AWKWARD_TYPE_META, CHARACTER_META, EMOTIONS, KIND_LABEL, type Emotion, type ResponseRow } from '@/lib/quality'
import { cn } from '@/lib/utils'

interface PageResponse<T> {
  content: T[]
  totalElements: number
}

export interface ResponseFilters {
  from: string
  to: string
  emotionType: Emotion | ''
  kind: string
  aiVerdict: string
  awkwardType: string
  humanState: string
  promptVersion: string
}

const DEFAULT_FILTERS: Omit<ResponseFilters, 'from' | 'to'> = {
  emotionType: '',
  kind: '',
  aiVerdict: 'AWKWARD',
  awkwardType: '',
  humanState: 'ALL',
  promptVersion: '',
}

const PAGE_SIZE = 20
const COLUMN_COUNT = 6

const AI_TABS = [
  { value: 'AWKWARD', label: '어색함' },
  { value: 'UNSURE', label: '애매함' },
  { value: 'NATURAL', label: '자연스러움' },
  { value: 'NONE', label: '미채점' },
  { value: '', label: '전체' },
]

const HUMAN_STATES = [
  { value: 'ALL', label: '사람 판정 전체' },
  { value: 'MINE_PENDING', label: '내가 판정 안 함' },
  { value: 'MINE_DONE', label: '내가 판정함' },
  { value: 'ANY_DONE', label: '누군가 판정함' },
  { value: 'NONE', label: '아무도 판정 안 함' },
]

function shortDateTime(iso: string) {
  return new Date(iso).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export function ResponseList({
  filters,
  onChange,
  selectedMessageId,
  onSelect,
}: {
  filters: ResponseFilters
  onChange: (filters: ResponseFilters) => void
  selectedMessageId: number | null
  onSelect: (row: ResponseRow) => void
}) {
  const [page, setPage] = useState(0)
  const [lastEmotion, setLastEmotion] = useState(filters.emotionType)
  if (lastEmotion !== filters.emotionType) {
    setLastEmotion(filters.emotionType)
    setPage(0)
  }

  const params = new URLSearchParams({ from: filters.from, to: filters.to, page: String(page), size: String(PAGE_SIZE), humanState: filters.humanState })
  ;(['emotionType', 'kind', 'aiVerdict', 'awkwardType', 'promptVersion'] as const).forEach((key) => {
    if (filters[key]) {
      params.set(key, filters[key])
    }
  })
  const { data, isLoading } = useCustom<PageResponse<ResponseRow>>({ url: `/api/admin/conversation-quality/responses?${params}`, method: 'get' })
  const rows = data?.data.content ?? []
  const total = data?.data.totalElements ?? 0
  const lastPage = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const rangeStart = total === 0 ? 0 : page * PAGE_SIZE + 1
  const rangeEnd = Math.min((page + 1) * PAGE_SIZE, total)

  const set = (patch: Partial<ResponseFilters>) => {
    setPage(0)
    onChange({ ...filters, ...patch })
  }
  const isFiltered = (Object.keys(DEFAULT_FILTERS) as (keyof typeof DEFAULT_FILTERS)[]).some((key) => filters[key] !== DEFAULT_FILTERS[key])

  return (
    <Card className="overflow-hidden">
      <div className="space-y-3 border-b p-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="inline-flex items-center rounded-md border bg-muted/40 p-0.5">
            {AI_TABS.map((tab) => (
              <button
                key={tab.value}
                type="button"
                onClick={() => set({ aiVerdict: tab.value })}
                className={cn(
                  'rounded px-3 py-1 text-xs font-medium transition-colors',
                  filters.aiVerdict === tab.value ? 'bg-background text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="flex items-center gap-1.5">
            <Input type="date" className="h-8 w-[8.75rem] text-xs" value={filters.from} max={filters.to} onChange={(e) => set({ from: e.target.value })} aria-label="시작일" />
            <span className="text-xs text-muted-foreground">~</span>
            <Input type="date" className="h-8 w-[8.75rem] text-xs" value={filters.to} min={filters.from} onChange={(e) => set({ to: e.target.value })} aria-label="종료일" />
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Select className="h-8 w-auto text-xs" value={filters.emotionType} onChange={(e) => set({ emotionType: e.target.value as Emotion | '' })} aria-label="캐릭터">
            <option value="">캐릭터 전체</option>
            {EMOTIONS.map((e) => (
              <option key={e} value={e}>
                {CHARACTER_META[e].label}
              </option>
            ))}
          </Select>
          <Select className="h-8 w-auto text-xs" value={filters.kind} onChange={(e) => set({ kind: e.target.value })} aria-label="응답 종류">
            <option value="">댓글과 답글</option>
            <option value="COMMENT">댓글만</option>
            <option value="REPLY">답글만</option>
          </Select>
          <Select className="h-8 w-auto text-xs" value={filters.awkwardType} onChange={(e) => set({ awkwardType: e.target.value })} aria-label="어색함 유형">
            <option value="">유형 전체</option>
            {AWKWARD_TYPES.map((t) => (
              <option key={t} value={t}>
                {AWKWARD_TYPE_META[t].label}
              </option>
            ))}
          </Select>
          <Select className="h-8 w-auto text-xs" value={filters.humanState} onChange={(e) => set({ humanState: e.target.value })} aria-label="사람 판정">
            {HUMAN_STATES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
          <Input
            type="number"
            min={1}
            className="h-8 w-32 text-xs"
            placeholder="프롬프트 버전"
            value={filters.promptVersion}
            onChange={(e) => set({ promptVersion: e.target.value })}
            aria-label="생성 프롬프트 버전"
          />
          {isFiltered && (
            <Button variant="ghost" size="sm" className="h-8 text-xs text-muted-foreground" onClick={() => set(DEFAULT_FILTERS)}>
              <RotateCcw className="size-3.5" />
              초기화
            </Button>
          )}
        </div>
      </div>

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-28">캐릭터</TableHead>
              <TableHead className="min-w-[22rem]">응답</TableHead>
              <TableHead className="w-60">AI 판정</TableHead>
              <TableHead className="w-28">사람 판정</TableHead>
              <TableHead className="w-28">생성 프롬프트</TableHead>
              <TableHead className="w-32">생성 시각</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i} className="hover:bg-transparent">
                  {Array.from({ length: COLUMN_COUNT }).map((__, j) => (
                    <TableCell key={j}>
                      <Skeleton className={cn('h-4', j === 1 ? 'w-full' : 'w-16')} />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : rows.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={COLUMN_COUNT} className="py-16">
                  <div className="flex flex-col items-center gap-2 text-center">
                    <div className="flex size-10 items-center justify-center rounded-full bg-muted">
                      <MessagesSquare className="size-5 text-muted-foreground" />
                    </div>
                    <p className="text-sm font-medium">조건에 맞는 응답이 없습니다</p>
                    <p className="text-xs text-muted-foreground">기간을 넓히거나 AI 판정 조건을 바꿔 보세요.</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => {
                const meta = CHARACTER_META[row.emotionType]
                const ai = row.aiVerdict
                const disagree = Boolean(ai && row.myVerdict && ai.verdict !== row.myVerdict.verdict)
                return (
                  <TableRow
                    key={row.messageId}
                    onClick={() => onSelect(row)}
                    className={cn('cursor-pointer', selectedMessageId === row.messageId && 'bg-muted hover:bg-muted')}
                  >
                    <TableCell className="align-top">
                      <p className="flex items-center gap-1.5 whitespace-nowrap font-medium">
                        <span className="size-2 rounded-full" style={{ background: meta.color }} />
                        {meta.label}
                      </p>
                      <p className="mt-0.5 pl-3.5 text-xs text-muted-foreground">{KIND_LABEL[row.kind]}</p>
                    </TableCell>
                    <TableCell className="max-w-0 align-top">
                      <p className="truncate">{row.content}</p>
                      {ai?.reason && <p className="mt-0.5 truncate text-xs text-muted-foreground">{ai.reason}</p>}
                    </TableCell>
                    <TableCell className="align-top">
                      {ai ? (
                        <div className="flex flex-wrap items-center gap-1">
                          <VerdictBadge verdict={ai.verdict} />
                          {ai.awkwardTypes.map((t) => (
                            <TypeChip key={t} label={AWKWARD_TYPE_META[t].label} title={AWKWARD_TYPE_META[t].hint} />
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">미채점</span>
                      )}
                    </TableCell>
                    <TableCell className="align-top">
                      {row.myVerdict ? (
                        <div className="space-y-0.5">
                          <VerdictBadge verdict={row.myVerdict.verdict} prefix="나" />
                          {disagree && <p className="text-[11px] text-amber-700">AI와 다름</p>}
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">{row.humanVerdictCount > 0 ? `${row.humanVerdictCount}명 판정` : '-'}</span>
                      )}
                    </TableCell>
                    <TableCell className="align-top text-sm text-muted-foreground">
                      {row.typePromptVersion !== null ? (
                        <span className="tabular-nums">
                          {KIND_LABEL[row.kind]} v{row.typePromptVersion}
                        </span>
                      ) : (
                        '기록 없음'
                      )}
                    </TableCell>
                    <TableCell className="whitespace-nowrap align-top text-sm tabular-nums text-muted-foreground">{shortDateTime(row.createdAt)}</TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between border-t px-4 py-3">
        <p className="text-xs text-muted-foreground">{total === 0 ? '0개' : `${rangeStart}–${rangeEnd} / ${total.toLocaleString()}`}</p>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 0 || isLoading} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft className="size-4" />
            이전
          </Button>
          <span className="min-w-16 text-center text-xs tabular-nums text-muted-foreground">
            {page + 1} / {lastPage}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= lastPage || isLoading} onClick={() => setPage((p) => p + 1)}>
            다음
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>
    </Card>
  )
}
