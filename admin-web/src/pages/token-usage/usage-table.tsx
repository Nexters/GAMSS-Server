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
  reminderNotification: NotificationOutcome | null
  cardNotification: NotificationOutcome | null
}

type NotificationOutcome = 'SENT' | 'NO_DEVICE' | 'FAILED' | 'SKIPPED' | 'ALREADY_HANDLED'

const PAGE_SIZE = 20
const COLUMN_COUNT = 13

const STATUS_META: Record<ConversationUsage['status'], { label: string; variant: 'secondary' | 'success' | 'muted' }> = {
  ACTIVE: { label: '진행중', variant: 'secondary' },
  ENDED: { label: '종료', variant: 'success' },
  DELETED: { label: '삭제됨', variant: 'muted' },
}

/**
 * 알림 결과를 다르게 보여준다.
 * 기기 없음(알림 끔)과 건너뜀은 실패가 아니므로 실패와 같은 색으로 그리면 대응할 것이 묻힌다.
 * ALREADY_HANDLED 는 배치가 이 방을 대상으로 잡아 처리하려 했지만, 그사이 사용자가 직접
 * 종료+카드 생성을 먼저 끝내 놓은 경우다 - 배치가 실제로 봤다는 사실이 로그에 남은 값이라, 프론트가
 * status/cardCreated 로 추측하는 값(NOT_TARGET_META, notTargetCardLabel)보다 신뢰도가 높다. 라벨
 * 텍스트도 "직접 생성"(확인된 사실)과 "직접 생성 추정"(추측)으로 갈라, 툴팁을 열지 않아도
 * 신뢰도 차이가 보이게 한다.
 * 기록이 아예 없으면(null) 그 회차에 배치가 이 방을 보지도 않았다는 뜻인데, 이유가 갈린다 - 사용자가
 * 이미 직접 처리해서 대상이 아니게 된 경우(직접 종료·직접 생성)와, 순수하게 시간대 밖이라 처음부터
 * 대상이 아니었던 경우(대상 아님)를 구분해서 보여준다. 빈칸(-) 하나로 두면 기기 없음과도,
 * 서로와도 시각적으로 구분이 안 돼 헷갈린다.
 */
const NOTIFICATION_META: Record<NotificationOutcome, { label: string; variant: 'success' | 'muted' | 'destructive'; title: string }> = {
  SENT: { label: '발송', variant: 'success', title: 'FCM 이 성공을 돌려줬습니다' },
  NO_DEVICE: { label: '기기 없음', variant: 'muted', title: '알림을 껐거나 앱을 지운 회원이라 보낼 기기가 없었습니다' },
  FAILED: { label: '실패', variant: 'destructive', title: 'FCM 이 실패를 돌려줬습니다' },
  SKIPPED: { label: '건너뜀', variant: 'muted', title: '같은 회원의 다른 방으로 이미 같은 알림이 나갔습니다' },
  ALREADY_HANDLED: {
    label: '직접 생성',
    variant: 'muted',
    title: '배치가 카드를 만들려 했지만, 그사이 사용자가 앱에서 직접 종료하고 카드를 먼저 생성했습니다',
  },
}

const NOT_TARGET_META = { label: '대상 아님', title: '배치가 이 방을 이 회차의 대상으로 보지 않았습니다(시간대 밖 생성 등)' }
const DELETED_META = { label: '삭제됨', title: '삭제된 방이라 알림 결과가 더 이상 의미가 없습니다' }

const SEOUL_HOUR_MINUTE = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/**
 * 리마인더(04:30)는 그 시점에 이미 존재하는 방만 조회한다. 04:30~05:00 사이에 생성된 방은 조회
 * 시점에 아직 없어 리마인더 대상일 수 없었는데도, 05:00 배치가 곧바로 자동 종료시킨다
 * (AutoCardWindow.DAY_BOUNDARY_HOUR). 이 방은 reminderNotification 이 null 이면서 상태만 ENDED가
 * 되므로, 생성 시각이 이 틈에 걸리면 "사용자가 직접 종료했다"고 단정할 수 없다.
 */
function isCreatedInReminderGap(createdAt: string): boolean {
  const parts = SEOUL_HOUR_MINUTE.formatToParts(new Date(createdAt))
  const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? 0) % 24
  const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? 0)
  const minutesSinceMidnight = hour * 60 + minute
  return minutesSinceMidnight >= 4 * 60 + 30 && minutesSinceMidnight < 5 * 60
}

/** 리마인더가 뜨기 전에 사용자가 이미 대화를 직접 종료해서 알릴 필요가 없었던 경우다. */
function notTargetReminder(
  status: ConversationUsage['status'],
  createdAt: string,
): { label?: string; title?: string } {
  if (status !== 'ENDED' || isCreatedInReminderGap(createdAt)) {
    return {}
  }
  return {
    label: '직접 종료',
    title: '리마인더가 뜨기 전에 사용자가 이미 대화를 직접 종료해서 알릴 필요가 없었습니다',
  }
}

/**
 * 카드가 있는데 배치 알림 기록이 없다면, 배치가 이 방을 아예 보지 못한 채로(시간대 밖 생성 등)
 * 사용자가 직접 만든 것이다. 배치가 실제로 이 방을 봤는데 사용자가 먼저 끝낸 경우는 이제
 * ALREADY_HANDLED 로 기록이 남으므로(NOTIFICATION_META), 이 추측은 기록이 아예 없는 나머지
 * 경우에만 쓰인다. ALREADY_HANDLED 와 텍스트를 다르게 둬서, 배치가 실제로 확인한 값과 프론트가
 * 추측한 값을 라벨만 보고도 구분할 수 있게 한다.
 */
function notTargetCardLabel(cardCreated: boolean): string | undefined {
  return cardCreated ? '직접 생성 추정' : undefined
}

function notTargetCardTitle(cardCreated: boolean): string | undefined {
  return cardCreated
    ? '배치 기록은 없지만 카드가 있어, 배치가 보기 전에 사용자가 직접 종료하고 카드를 만들었을 것으로 추정합니다(확인된 사실 아님)'
    : undefined
}

/**
 * 삭제는 종료 여부와 무관하게 일어날 수 있고(진행 중인 방도 바로 삭제 가능), 삭제 전에 실제
 * 발송 이력(SENT·FAILED 등)이 있었을 수도 있다. 그 이력이 있든 없든 삭제된 방은 더 이상 조치할
 * 게 없으므로, outcome 값을 따지기 전에 삭제 여부부터 확인해 항상 "삭제됨"으로 보여준다.
 */
function NotificationCell({
  outcome,
  deleted,
  notTargetLabel,
  notTargetTitle,
}: {
  outcome: NotificationOutcome | null
  deleted: boolean
  notTargetLabel?: string
  notTargetTitle?: string
}) {
  if (deleted) {
    return (
      <Badge variant="secondary" title={DELETED_META.title}>
        {DELETED_META.label}
      </Badge>
    )
  }
  if (outcome === null) {
    return (
      <Badge variant="secondary" title={notTargetTitle ?? NOT_TARGET_META.title}>
        {notTargetLabel ?? NOT_TARGET_META.label}
      </Badge>
    )
  }
  const meta = NOTIFICATION_META[outcome]
  return (
    <Badge variant={meta.variant} title={meta.title}>
      {meta.label}
    </Badge>
  )
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
          모든 대화방(prod, dev)의 메시지 수, 카드 생성 여부, 소비 토큰, 예상 비용, 새벽 알림 발송 결과를 최신순으로 봅니다. 비용은 USD 기준이며 환율 {USD_TO_KRW_LABEL}원으로 원화를 함께 표기합니다. 대화방 귀속 정보가 없던 이전 기록의 토큰은 집계되지 않고, 알림 이력을 남기기 이전 회차는 알림 칸이 비어 있습니다.
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
              <TableHead className="w-24 text-right">캐릭터</TableHead>
              <TableHead className="w-20 text-center">카드</TableHead>
              <TableHead className="w-28 text-center">04:30 알림</TableHead>
              <TableHead className="w-28 text-center">05:00 알림</TableHead>
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
                const reminderNotTarget = notTargetReminder(row.status, row.createdAt)
                return (
                  <TableRow key={row.conversationId} className="hover:bg-transparent">
                    <TableCell className="font-mono text-xs text-muted-foreground">{row.conversationId}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">{row.memberId}</TableCell>
                    <TableCell className="max-w-64 truncate">
                      {row.title ?? <Badge variant="muted">제목 없음</Badge>}
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
                        <Badge variant="muted">미생성</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-center">
                      <NotificationCell
                        outcome={row.reminderNotification}
                        deleted={row.status === 'DELETED'}
                        notTargetLabel={reminderNotTarget.label}
                        notTargetTitle={reminderNotTarget.title}
                      />
                    </TableCell>
                    <TableCell className="text-center">
                      <NotificationCell
                        outcome={row.cardNotification}
                        deleted={row.status === 'DELETED'}
                        notTargetLabel={notTargetCardLabel(row.cardCreated)}
                        notTargetTitle={notTargetCardTitle(row.cardCreated)}
                      />
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
                        <Badge variant="muted">비용 없음</Badge>
                      )}
                    </TableCell>
                    <TableCell className="whitespace-nowrap text-sm text-muted-foreground">{formatDateTime(row.createdAt)}</TableCell>
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
