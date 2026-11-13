import { useEffect, useState } from 'react'
import { useCustom } from '@refinedev/core'
import { ChevronLeft, ChevronRight, MessagesSquare, RotateCcw } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  AWKWARD_TYPES,
  AWKWARD_TYPE_META,
  CHARACTER_META,
  EMOTIONS,
  KIND_LABEL,
  VERDICT_META,
  type Emotion,
  type ResponseRow,
} from '@/lib/quality'
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
    month: 'numeric',
    day: 'numeric',
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
  useEffect(() => setPage(0), [filters.emotionType])

  const params = new URLSearchParams({ from: filters.from, to: filters.to, page: String(page), size: String(PAGE_SIZE), humanState: filters.humanState })
  ;(['emotionType', 'kind', 'aiVerdict', 'awkwardType', 'promptVersion'] as const).forEach((key) => {
    if (filters[key]) {
      params.set(key, filters[key])
    }
  })
  const { data, isLoading } = useCustom<PageResponse<ResponseRow>>({ url: `/api/admin/conversation-quality/responses?${params}`, method: 'get' })
  const rows = data?.data.content ?? []
  const total = data?.data.totalElements ?? 0
  const lastPage = Math.max(0, Math.ceil(total / PAGE_SIZE) - 1)

  const set = (patch: Partial<ResponseFilters>) => {
    setPage(0)
    onChange({ ...filters, ...patch })
  }
  const isFiltered = (Object.keys(DEFAULT_FILTERS) as (keyof typeof DEFAULT_FILTERS)[]).some((key) => filters[key] !== DEFAULT_FILTERS[key])

  return (
    <Card className="overflow-hidden">
      <div className="flex flex-wrap items-end justify-between gap-2 px-5 pt-5">
        <div>
          <p className="text-sm font-medium">응답</p>
          <p className="text-xs text-muted-foreground">행을 누르면 대화 흐름 안에서 그 응답을 봅니다</p>
        </div>
        {isFiltered && (
          <Button variant="ghost" size="sm" onClick={() => set(DEFAULT_FILTERS)}>
            <RotateCcw className="size-3.5" />
            필터 초기화
          </Button>
        )}
      </div>

      <div className="grid gap-2 border-b px-5 pb-4 pt-3 sm:grid-cols-4 lg:grid-cols-8">
        <Input type="date" value={filters.from} onChange={(e) => set({ from: e.target.value })} aria-label="시작일" />
        <Input type="date" value={filters.to} onChange={(e) => set({ to: e.target.value })} aria-label="종료일" />
        <Select value={filters.emotionType} onChange={(e) => set({ emotionType: e.target.value as Emotion | '' })} aria-label="캐릭터">
          <option value="">캐릭터 전체</option>
          {EMOTIONS.map((e) => (
            <option key={e} value={e}>
              {CHARACTER_META[e].label}
            </option>
          ))}
        </Select>
        <Select value={filters.kind} onChange={(e) => set({ kind: e.target.value })} aria-label="응답 종류">
          <option value="">댓글과 답글</option>
          <option value="COMMENT">댓글만</option>
          <option value="REPLY">답글만</option>
        </Select>
        <Select value={filters.aiVerdict} onChange={(e) => set({ aiVerdict: e.target.value })} aria-label="AI 판정">
          <option value="">AI 판정 전체</option>
          <option value="AWKWARD">AI 어색함</option>
          <option value="NATURAL">AI 자연스러움</option>
          <option value="UNSURE">AI 애매함</option>
          <option value="NONE">AI 미채점</option>
        </Select>
        <Select value={filters.awkwardType} onChange={(e) => set({ awkwardType: e.target.value })} aria-label="어색함 유형">
          <option value="">유형 전체</option>
          {AWKWARD_TYPES.map((t) => (
            <option key={t} value={t}>
              {AWKWARD_TYPE_META[t].label}
            </option>
          ))}
        </Select>
        <Select value={filters.humanState} onChange={(e) => set({ humanState: e.target.value })} aria-label="사람 판정">
          {HUMAN_STATES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </Select>
        <Input
          type="number"
          min={1}
          placeholder="프롬프트 버전"
          value={filters.promptVersion}
          onChange={(e) => set({ promptVersion: e.target.value })}
          aria-label="프롬프트 버전"
        />
      </div>

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-28 pl-5">캐릭터</TableHead>
              <TableHead className="min-w-[24rem]">응답 · AI 사유</TableHead>
              <TableHead className="w-52">AI 판정</TableHead>
              <TableHead className="w-28">사람 판정</TableHead>
              <TableHead className="w-36">생성 프롬프트</TableHead>
              <TableHead className="w-28 pr-5 text-right">시각</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i} className="hover:bg-transparent">
                  <TableCell colSpan={6} className="px-5">
                    <Skeleton className="h-9 w-full" />
                  </TableCell>
                </TableRow>
              ))
            ) : rows.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={6} className="py-16">
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
                    className={cn('cursor-pointer', selectedMessageId === row.messageId && 'bg-primary/5 hover:bg-primary/5')}
                  >
                    <TableCell className="py-3 pl-5">
                      <p className="whitespace-nowrap text-sm font-medium" style={{ color: meta.color }}>
                        {meta.emoji} {meta.label}
                      </p>
                      <p className="text-[11px] text-muted-foreground">{KIND_LABEL[row.kind]}</p>
                    </TableCell>
                    <TableCell className="max-w-0 py-3">
                      <p className="truncate text-sm">{row.content}</p>
                      {ai?.reason && <p className="mt-0.5 truncate text-xs text-muted-foreground">{ai.reason}</p>}
                    </TableCell>
                    <TableCell className="py-3">
                      {ai ? (
                        <div className="flex flex-wrap items-center gap-1">
                          <Badge variant={VERDICT_META[ai.verdict].variant} className="px-1.5 py-0 text-[11px]">
                            {VERDICT_META[ai.verdict].label}
                          </Badge>
                          {ai.awkwardTypes.map((t) => (
                            <span key={t} className="rounded-full bg-rose-50 px-1.5 py-0.5 text-[10px] font-medium text-rose-700">
                              {AWKWARD_TYPE_META[t].label}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-muted-foreground">미채점</span>
                      )}
                    </TableCell>
                    <TableCell className="py-3 text-xs">
                      {row.myVerdict ? (
                        <span className={cn('font-medium', disagree && 'text-amber-700')} title={disagree ? 'AI 판정과 다릅니다' : undefined}>
                          나: {VERDICT_META[row.myVerdict.verdict].label}
                        </span>
                      ) : (
                        <span className="text-muted-foreground">{row.humanVerdictCount > 0 ? `${row.humanVerdictCount}명 판정` : '-'}</span>
                      )}
                    </TableCell>
                    <TableCell className="py-3 text-xs text-muted-foreground">
                      {row.typePromptVersion !== null ? `${KIND_LABEL[row.kind]} v${row.typePromptVersion}` : '기록 없음'}
                    </TableCell>
                    <TableCell className="whitespace-nowrap py-3 pr-5 text-right text-xs tabular-nums text-muted-foreground">{shortDateTime(row.createdAt)}</TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between border-t px-5 py-3">
        <p className="text-xs text-muted-foreground">{total.toLocaleString()}개 응답</p>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page <= 0 || isLoading} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft className="size-4" />
            이전
          </Button>
          <span className="min-w-16 text-center text-xs tabular-nums text-muted-foreground">
            {page + 1} / {lastPage + 1}
          </span>
          <Button variant="outline" size="sm" disabled={page >= lastPage || isLoading} onClick={() => setPage((p) => p + 1)}>
            다음
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>
    </Card>
  )
}
