import { useEffect, useState } from 'react'
import { useCustom } from '@refinedev/core'
import { ChevronLeft, ChevronRight, MessagesSquare } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime } from '@/lib/format'
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

const PAGE_SIZE = 20

const HUMAN_STATES = [
  { value: 'ALL', label: '사람 판정 전체' },
  { value: 'MINE_PENDING', label: '내가 판정 안 함' },
  { value: 'MINE_DONE', label: '내가 판정함' },
  { value: 'ANY_DONE', label: '누군가 판정함' },
  { value: 'NONE', label: '아무도 판정 안 함' },
]

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

  return (
    <Card className="overflow-hidden">
      <div className="grid gap-2 border-b p-3 sm:grid-cols-4 lg:grid-cols-8">
        <Input type="date" value={filters.from} onChange={(e) => set({ from: e.target.value })} aria-label="시작일" />
        <Input type="date" value={filters.to} onChange={(e) => set({ to: e.target.value })} aria-label="종료일" />
        <Select value={filters.emotionType} onChange={(e) => set({ emotionType: e.target.value as Emotion | '' })}>
          <option value="">캐릭터 전체</option>
          {EMOTIONS.map((e) => (
            <option key={e} value={e}>
              {CHARACTER_META[e].label}
            </option>
          ))}
        </Select>
        <Select value={filters.kind} onChange={(e) => set({ kind: e.target.value })}>
          <option value="">댓글·답글</option>
          <option value="COMMENT">댓글</option>
          <option value="REPLY">답글</option>
        </Select>
        <Select value={filters.aiVerdict} onChange={(e) => set({ aiVerdict: e.target.value })}>
          <option value="">AI 판정 전체</option>
          <option value="AWKWARD">AI 어색함</option>
          <option value="NATURAL">AI 자연스러움</option>
          <option value="UNSURE">AI 애매함</option>
          <option value="NONE">AI 미채점</option>
        </Select>
        <Select value={filters.awkwardType} onChange={(e) => set({ awkwardType: e.target.value })}>
          <option value="">유형 전체</option>
          {AWKWARD_TYPES.map((t) => (
            <option key={t} value={t}>
              {AWKWARD_TYPE_META[t].label}
            </option>
          ))}
        </Select>
        <Select value={filters.humanState} onChange={(e) => set({ humanState: e.target.value })}>
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
        />
      </div>

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-24">캐릭터</TableHead>
              <TableHead className="w-16">종류</TableHead>
              <TableHead className="min-w-72">응답</TableHead>
              <TableHead className="w-28">AI 판정</TableHead>
              <TableHead className="min-w-48">유형 · 사유</TableHead>
              <TableHead className="w-28">사람 판정</TableHead>
              <TableHead className="w-24">버전</TableHead>
              <TableHead className="w-40">생성일</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => (
                <TableRow key={i} className="hover:bg-transparent">
                  <TableCell colSpan={8}>
                    <Skeleton className="h-4 w-full" />
                  </TableCell>
                </TableRow>
              ))
            ) : rows.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={8} className="py-16">
                  <div className="flex flex-col items-center gap-2 text-center">
                    <MessagesSquare className="size-5 text-muted-foreground" />
                    <p className="text-sm font-medium">조건에 맞는 응답이 없습니다</p>
                    <p className="text-xs text-muted-foreground">기간이나 AI 판정 조건을 넓혀 보세요.</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => {
                const meta = CHARACTER_META[row.emotionType]
                return (
                  <TableRow
                    key={row.messageId}
                    onClick={() => onSelect(row)}
                    className={cn('cursor-pointer', selectedMessageId === row.messageId && 'bg-muted')}
                  >
                    <TableCell className="whitespace-nowrap text-sm" style={{ color: meta.color }}>
                      {meta.emoji} {meta.label}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{KIND_LABEL[row.kind]}</TableCell>
                    <TableCell className="max-w-96 truncate text-sm">{row.content}</TableCell>
                    <TableCell>
                      {row.aiVerdict ? (
                        <Badge variant={VERDICT_META[row.aiVerdict.verdict].variant}>{VERDICT_META[row.aiVerdict.verdict].label}</Badge>
                      ) : (
                        <Badge variant="muted">미채점</Badge>
                      )}
                    </TableCell>
                    <TableCell className="max-w-72 truncate text-xs text-muted-foreground">
                      {row.aiVerdict?.verdict === 'AWKWARD' &&
                        `${row.aiVerdict.awkwardTypes.map((t) => AWKWARD_TYPE_META[t].label).join(', ')}${row.aiVerdict.reason ? ` · ${row.aiVerdict.reason}` : ''}`}
                    </TableCell>
                    <TableCell className="text-xs">
                      {row.myVerdict ? (
                        <Badge variant="outline">나: {VERDICT_META[row.myVerdict.verdict].label}</Badge>
                      ) : row.humanVerdictCount > 0 ? (
                        <span className="text-muted-foreground">{row.humanVerdictCount}명 판정</span>
                      ) : (
                        <span className="text-muted-foreground">-</span>
                      )}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{row.typePromptVersion !== null ? `v${row.typePromptVersion}` : '-'}</TableCell>
                    <TableCell className="whitespace-nowrap text-xs text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between border-t px-4 py-3">
        <p className="text-xs text-muted-foreground">{total.toLocaleString()}건</p>
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
