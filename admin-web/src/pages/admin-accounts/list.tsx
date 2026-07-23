import { useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Plus, RotateCcw, ShieldCheck, Trash2 } from 'lucide-react'
import type { AdminAccount } from '@/types/adminAccount'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { PageHeader } from '@/components/page-header'
import { formatDateTime } from '@/lib/format'

function mapError(code?: string): string {
  switch (code) {
    case 'ADMIN_ACCOUNT_ALREADY_EXISTS':
      return '이미 등록된 관리자입니다.'
    case 'INVALID_INPUT':
      return '이메일 형식이 올바르지 않습니다.'
    case 'CANNOT_REMOVE_SELF':
      return '자기 자신은 삭제할 수 없습니다.'
    case 'ADMIN_ACCOUNT_NOT_FOUND':
      return '이미 삭제된 관리자입니다.'
    default:
      return '요청에 실패했습니다.'
  }
}

export function AdminAccountList() {
  const { data, isLoading, isError, refetch } = useCustom<AdminAccount[]>({
    url: '/api/admin/accounts',
    method: 'get',
  })
  const { mutate: addMutate, isLoading: adding } = useCustomMutation()
  const { mutate: removeMutate } = useCustomMutation()

  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)

  const accounts = data?.data ?? []

  const submit = () => {
    const value = email.trim()
    if (!value || adding) {
      return
    }
    setError(null)
    addMutate(
      { url: '/api/admin/accounts', method: 'post', values: { email: value } },
      {
        onSuccess: () => {
          setEmail('')
          refetch()
        },
        onError: (e) => setError(mapError(e?.message)),
      },
    )
  }

  const remove = (id: number) => {
    setError(null)
    removeMutate(
      { url: `/api/admin/accounts/${id}`, method: 'delete', values: {} },
      {
        onSuccess: () => refetch(),
        onError: (e) => setError(mapError(e?.message)),
      },
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="관리자 관리"
        description="백오피스에 로그인할 수 있는 관리자를 관리합니다. 추가·삭제는 재배포 없이 즉시 반영됩니다."
      />

      <Card className="space-y-3 p-4">
        <label htmlFor="admin-email" className="text-sm font-medium">
          관리자 추가
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <Input
            id="admin-email"
            type="email"
            className="w-full max-w-sm"
            placeholder="구글 계정 이메일 (예: teammate@gmail.com)"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                submit()
              }
            }}
          />
          <Button size="sm" onClick={submit} disabled={!email.trim() || adding}>
            <Plus className="size-4" />
            추가
          </Button>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <p className="text-xs text-muted-foreground">
          추가한 계정은 다음 구글 로그인부터 백오피스에 접근할 수 있습니다. 구글 로그인만 허용됩니다.
        </p>
      </Card>

      {isError && !data ? (
        <Card className="flex flex-col items-start gap-3 p-6">
          <p className="text-sm text-muted-foreground">관리자 목록을 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>이메일</TableHead>
                <TableHead className="w-28">출처</TableHead>
                <TableHead>추가한 관리자</TableHead>
                <TableHead className="w-48">추가일</TableHead>
                <TableHead className="w-16" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                Array.from({ length: 3 }).map((_, i) => (
                  <TableRow key={i} className="hover:bg-transparent">
                    <TableCell>
                      <Skeleton className="h-4 w-48" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-5 w-16 rounded-full" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell>
                      <Skeleton className="h-4 w-32" />
                    </TableCell>
                    <TableCell />
                  </TableRow>
                ))
              ) : (
                accounts.map((account) => (
                  <TableRow key={account.source === 'ENV' ? `env-${account.email}` : account.id} className="hover:bg-transparent">
                    <TableCell className="font-medium">{account.email}</TableCell>
                    <TableCell>
                      {account.source === 'ENV' ? (
                        <Badge variant="muted">환경 설정</Badge>
                      ) : (
                        <Badge variant="secondary">관리</Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {account.addedByEmail ?? <span className="text-muted-foreground/60">—</span>}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {account.createdAt ? formatDateTime(account.createdAt) : <span className="text-muted-foreground/60">—</span>}
                    </TableCell>
                    <TableCell className="text-right">
                      {account.removable && account.id !== null && (
                        <AlertDialog>
                          <AlertDialogTrigger asChild>
                            <Button variant="ghost" size="sm" className="text-muted-foreground hover:text-destructive">
                              <Trash2 className="size-4" />
                            </Button>
                          </AlertDialogTrigger>
                          <AlertDialogContent>
                            <AlertDialogHeader>
                              <AlertDialogTitle>관리자를 삭제할까요?</AlertDialogTitle>
                              <AlertDialogDescription>
                                <span className="font-medium text-foreground">{account.email}</span> 계정의 백오피스
                                접근이 즉시 차단됩니다.
                              </AlertDialogDescription>
                            </AlertDialogHeader>
                            <AlertDialogFooter>
                              <AlertDialogCancel>취소</AlertDialogCancel>
                              <AlertDialogAction onClick={() => remove(account.id as number)}>삭제</AlertDialogAction>
                            </AlertDialogFooter>
                          </AlertDialogContent>
                        </AlertDialog>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          <div className="flex items-center gap-2 border-t px-4 py-2.5 text-xs text-muted-foreground">
            <ShieldCheck className="size-3.5" />
            <span>환경 설정(부트스트랩) 관리자는 안전장치로 UI에서 삭제할 수 없습니다.</span>
          </div>
        </Card>
      )}
    </div>
  )
}
