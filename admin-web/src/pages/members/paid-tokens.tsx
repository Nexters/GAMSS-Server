import { useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { ChevronLeft, ChevronRight, Coins, RotateCcw } from 'lucide-react'
import type { PaidTokens, TokenLedgerType } from '@/types/paidToken'
import { ApiError } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Badge } from '@/components/ui/badge'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Textarea } from '@/components/ui/textarea'

const PAGE_SIZE = 10

const TYPE_LABELS: Record<TokenLedgerType, string> = {
  PURCHASE: '구매',
  CONSUME: '사용',
  REFUND: '환불 회수',
  REFUND_REVERSED: '환불 취소',
  ADMIN_GRANT: '수동 지급',
  ADMIN_REVOKE: '수동 회수',
}

type Action = 'grant' | 'revoke'

const ACTION_LABELS: Record<Action, string> = { grant: '지급', revoke: '회수' }

function formatSigned(amount: number): string {
  return `${amount > 0 ? '+' : ''}${amount.toLocaleString()}`
}

function AdjustForm({ memberId, balance, onDone }: { memberId: number; balance: number; onDone: () => void }) {
  const [action, setAction] = useState<Action>('grant')
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // 입력이 바뀌면 새 요청이므로 키도 새로 만든다. 같은 입력의 재시도는 같은 키라 두 번 반영되지 않는다.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const { mutate, isLoading } = useCustomMutation()

  const parsedAmount = Number(amount)
  const valid = Number.isSafeInteger(parsedAmount) && parsedAmount > 0 && reason.trim().length > 0
  const expectedBalance = balance + (action === 'grant' ? parsedAmount : -parsedAmount)

  const edit = (apply: () => void) => {
    apply()
    setError(null)
    setIdempotencyKey(crypto.randomUUID())
  }

  const submit = () => {
    mutate(
      {
        url: `/api/admin/members/${memberId}/paid-tokens/${action}`,
        method: 'post',
        values: { idempotencyKey, amount: parsedAmount, reason: reason.trim() },
      },
      {
        onSuccess: () => {
          setAmount('')
          setReason('')
          setIdempotencyKey(crypto.randomUUID())
          onDone()
        },
        onError: (e) => setError(e instanceof ApiError && e.detail ? e.detail : `${ACTION_LABELS[action]}에 실패했습니다`),
      },
    )
  }

  return (
    <div className="space-y-3 border-t p-4">
      <div className="flex gap-1.5">
        {(['grant', 'revoke'] as const).map((value) => (
          <Button
            key={value}
            type="button"
            size="sm"
            variant={action === value ? 'default' : 'outline'}
            onClick={() => edit(() => setAction(value))}
          >
            {ACTION_LABELS[value]}
          </Button>
        ))}
      </div>
      <div className="grid gap-3 sm:grid-cols-[12rem_1fr]">
        <Input
          type="number"
          min={1}
          step={1}
          inputMode="numeric"
          placeholder="토큰 양"
          value={amount}
          onChange={(e) => edit(() => setAmount(e.target.value))}
        />
        <Textarea
          className="min-h-9"
          rows={1}
          maxLength={500}
          placeholder="사유 (예: 결제 후 미지급 보상)"
          value={reason}
          onChange={(e) => edit(() => setReason(e.target.value))}
        />
      </div>
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant={action === 'revoke' ? 'destructive' : 'default'}
          disabled={!valid || isLoading}
          onClick={() => setConfirming(true)}
        >
          {ACTION_LABELS[action]}
        </Button>
        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              유료 토큰 {parsedAmount.toLocaleString()}개를 {ACTION_LABELS[action]}할까요?
            </AlertDialogTitle>
            <AlertDialogDescription className="break-keep">
              잔액이 <span className="tabular-nums">{balance.toLocaleString()}</span>에서{' '}
              <span className={cn('font-medium tabular-nums', expectedBalance < 0 ? 'text-destructive' : 'text-foreground')}>
                {expectedBalance.toLocaleString()}
              </span>
              으로 바뀝니다. 원장에 처리자와 사유가 남습니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>취소</AlertDialogCancel>
            <AlertDialogAction
              className={cn(action === 'revoke' && buttonVariants({ variant: 'destructive' }))}
              onClick={submit}
            >
              {ACTION_LABELS[action]}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

// 탈퇴 회원은 토큰 주체 매핑이 지워져 지급·회수할 수 없으므로 폼을 숨기고 이력만 보여준다.
export function MemberPaidTokens({ memberId, canAdjust }: { memberId: number; canAdjust: boolean }) {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError, refetch } = useCustom<PaidTokens>({
    url: `/api/admin/members/${memberId}/paid-tokens?page=${page}&size=${PAGE_SIZE}`,
    method: 'get',
    queryOptions: { queryKey: ['member-paid-tokens', memberId, page] },
  })
  const paidTokens = data?.data
  const entries = paidTokens?.ledger.content ?? []
  const totalPages = paidTokens?.ledger.totalPages ?? 0

  const onAdjusted = () => {
    if (page === 0) {
      refetch()
    } else {
      setPage(0)
    }
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-2 border-b p-4">
        <Coins className="size-4 text-muted-foreground" />
        <span className="text-sm font-medium">유료 토큰</span>
        {paidTokens && (
          <span
            className={cn(
              'ml-auto text-lg font-semibold tabular-nums tracking-tight',
              paidTokens.balance < 0 && 'text-destructive',
            )}
          >
            {paidTokens.balance.toLocaleString()}
          </span>
        )}
      </div>

      {isError && !paidTokens ? (
        <div className="flex items-center gap-3 p-6">
          <p className="text-sm text-muted-foreground">유료 토큰을 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </div>
      ) : isLoading ? (
        <div className="space-y-2 p-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-9 w-full" />
          ))}
        </div>
      ) : entries.length === 0 ? (
        <p className="p-6 text-center text-sm text-muted-foreground">변동 이력이 없습니다.</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead className="w-40">시각</TableHead>
                  <TableHead className="w-24">타입</TableHead>
                  <TableHead className="w-28 text-right">증감</TableHead>
                  <TableHead className="w-28 text-right">잔액</TableHead>
                  <TableHead className="w-48">처리자</TableHead>
                  <TableHead>사유</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                      {formatDateTime(entry.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={entry.adminEmail ? 'outline' : 'muted'}>{TYPE_LABELS[entry.type] ?? entry.type}</Badge>
                    </TableCell>
                    <TableCell
                      className={cn(
                        'text-right text-sm tabular-nums',
                        entry.amount > 0 ? 'text-emerald-600' : 'text-destructive',
                      )}
                    >
                      {formatSigned(entry.amount)}
                    </TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{entry.balanceAfter.toLocaleString()}</TableCell>
                    <TableCell className="text-sm">
                      {entry.adminEmail ?? <span className="text-muted-foreground">-</span>}
                    </TableCell>
                    <TableCell className="max-w-0">
                      <p className="truncate text-sm" title={entry.reason ?? undefined}>
                        {entry.reason ?? <span className="text-muted-foreground">-</span>}
                      </p>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-end gap-2 border-t px-4 py-2.5">
              <span className="text-xs tabular-nums text-muted-foreground">
                {page + 1} / {totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                <ChevronLeft className="size-4" />
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
              >
                <ChevronRight className="size-4" />
              </Button>
            </div>
          )}
        </>
      )}

      {paidTokens && canAdjust && <AdjustForm memberId={memberId} balance={paidTokens.balance} onDone={onAdjusted} />}
    </Card>
  )
}
