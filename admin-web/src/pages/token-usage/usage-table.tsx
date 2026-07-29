import { useState } from 'react'
import { useList } from '@refinedev/core'
import { ChevronLeft, ChevronRight, MessagesSquare } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatKrw, formatUsd, USD_TO_KRW_LABEL } from '@/lib/currency'
import { formatDateTime } from '@/lib/format'

interface ConversationUsage {
  conversationId: number
  memberId: number
  title: string | null
  status: 'ACTIVE' | 'ENDED' | 'DELETED'
  createdAt: string
  userMessageCount: number
  characterMessageCount: number
  cardCreated: boolean
  totalTokens: number
  cachedTokens: number
  estimatedCostUsd: number
}

const PAGE_SIZE = 20
const COLUMN_COUNT = 11

const STATUS_META: Record<ConversationUsage['status'], { label: string; variant: 'secondary' | 'success' | 'muted' }> = {
  ACTIVE: { label: '진행중', variant: 'secondary' },
  ENDED: { label: '종료', variant: 'success' },
  DELETED: { label: '삭제됨', variant: 'muted' },
}

export function UsageTable() {
  const [current, setCurrent] = useState(1)

  const { data, isLoading } = useList<ConversationUsage>({
    resource: 'conversation-usage',
    pagination: { current, pageSize: PAGE_SIZE },
  })

  const rows = data?.data ?? []
  const total = data?.total ?? 0
  const lastPage = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const rangeStart = total === 0 ? 0 : (current - 1) * PAGE_SIZE + 1
  const rangeEnd = Math.min(current * PAGE_SIZE, total)

  return (
    <Card className="overflow-hidden">
      <div className="border-b px-4 py-3">
        <p className="text-sm font-medium">대화방별 사용량</p>
        <p className="text-xs text-muted-foreground">
          모든 대화방(prod·dev)의 메시지 수·카드 생성 여부·소비 토큰·예상 비용을 최신순으로 봅니다. 비용은 USD 기준이며 환율 {USD_TO_KRW_LABEL}원으로 원화를 함께 표기합니다. 대화방 귀속 정보가 없던 이전 기록의 토큰은 집계되지 않습니다.
        </p>
      </div>

      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-20">방 ID</TableHead>
              <TableHead className="w-20">회원</TableHead>
              <TableHead className="min-w-40">제목</TableHead>
              <TableHead className="w-24">상태</TableHead>
              <TableHead className="w-20 text-right">유저</TableHead>
              <TableHead className="w-20 text-right">캐릭터</TableHead>
              <TableHead className="w-20 text-center">카드</TableHead>
              <TableHead className="w-28 text-right">총 토큰</TableHead>
              <TableHead className="w-28 text-right">캐시 토큰</TableHead>
              <TableHead className="w-32 text-right">비용</TableHead>
              <TableHead className="w-44">생성일</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: PAGE_SIZE }).map((_, i) => (
                <TableRow key={i} className="hover:bg-transparent">
                  {Array.from({ length: COLUMN_COUNT }).map((__, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-4 w-full" />
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
                    <p className="text-sm font-medium">대화방이 없습니다</p>
                    <p className="text-xs text-muted-foreground">아직 생성된 대화방이 없습니다.</p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => {
                const status = STATUS_META[row.status]
                return (
                  <TableRow key={row.conversationId} className="hover:bg-transparent">
                    <TableCell className="font-mono text-xs text-muted-foreground">{row.conversationId}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{row.memberId}</TableCell>
                    <TableCell className="max-w-64 truncate">
                      {row.title ?? <span className="text-muted-foreground">—</span>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={status.variant}>{status.label}</Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">{row.userMessageCount.toLocaleString()}</TableCell>
                    <TableCell className="text-right tabular-nums">{row.characterMessageCount.toLocaleString()}</TableCell>
                    <TableCell className="text-center">
                      {row.cardCreated ? (
                        <Badge variant="success">생성</Badge>
                      ) : (
                        <span className="text-xs text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right font-medium tabular-nums">{row.totalTokens.toLocaleString()}</TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">
                      {row.cachedTokens.toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {row.estimatedCostUsd > 0 ? (
                        <div className="flex flex-col items-end leading-tight">
                          <span className="font-medium">{formatUsd(row.estimatedCostUsd)}</span>
                          <span className="text-xs text-muted-foreground">{formatKrw(row.estimatedCostUsd)}</span>
                        </div>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
                  </TableRow>
                )
              })
            )}
          </TableBody>
        </Table>
      </div>

      <div className="flex items-center justify-between border-t px-4 py-3">
        <p className="text-xs text-muted-foreground">
          {total === 0 ? '0개' : `${rangeStart}–${rangeEnd} / ${total.toLocaleString()}`}
        </p>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={current <= 1 || isLoading} onClick={() => setCurrent((c) => Math.max(1, c - 1))}>
            <ChevronLeft className="size-4" />
            이전
          </Button>
          <span className="min-w-16 text-center text-xs tabular-nums text-muted-foreground">
            {current} / {lastPage}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={current >= lastPage || isLoading}
            onClick={() => setCurrent((c) => Math.min(lastPage, c + 1))}
          >
            다음
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>
    </Card>
  )
}
