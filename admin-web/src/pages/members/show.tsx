import { useCustomMutation, useShow } from '@refinedev/core'
import { Link } from 'react-router-dom'
import { ArrowLeft, UserX } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Member } from '@/types/member'
import { Avatar } from '@/components/ui/avatar'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
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
import { StatusBadge } from '@/components/status-badge'
import { cn } from '@/lib/utils'
import { formatDateTime } from '@/lib/format'

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-3 gap-4 px-6 py-3.5">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="col-span-2 text-sm">{children}</dd>
    </div>
  )
}

export function MemberShow() {
  const { query } = useShow<Member>({ resource: 'members' })
  const member = query.data?.data
  const displayName = member?.nickname ?? member?.name ?? member?.email ?? `회원 #${member?.id ?? ''}`

  const { mutate: withdraw } = useCustomMutation()
  const onWithdraw = () => {
    if (!member) {
      return
    }
    withdraw(
      { url: `/api/admin/members/${member.id}/withdraw`, method: 'post', values: {} },
      { onSuccess: () => query.refetch() },
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="회원 상세"
        actions={
          <>
            {member?.status === 'ACTIVE' && (
              <AlertDialog>
                <AlertDialogTrigger asChild>
                  <Button variant="outline" size="sm" className="text-destructive hover:text-destructive">
                    <UserX className="size-4" />
                    탈퇴 처리
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>회원을 탈퇴 처리할까요?</AlertDialogTitle>
                    <AlertDialogDescription>
                      <span className="font-medium text-foreground">{displayName}</span> 회원을 탈퇴 상태로
                      전환합니다. 이 작업은 되돌릴 수 없습니다.
                    </AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>취소</AlertDialogCancel>
                    <AlertDialogAction
                      className={cn(buttonVariants({ variant: 'destructive' }))}
                      onClick={onWithdraw}
                    >
                      탈퇴 처리
                    </AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            )}
            <Button variant="outline" size="sm" asChild>
              <Link to="/members">
                <ArrowLeft className="size-4" />
                목록으로
              </Link>
            </Button>
          </>
        }
      />

      <Card className="max-w-2xl overflow-hidden">
        <div className="flex items-center gap-4 border-b bg-muted/20 p-6">
          {query.isLoading ? (
            <>
              <Skeleton className="size-12 rounded-full" />
              <div className="space-y-2">
                <Skeleton className="h-5 w-32" />
                <Skeleton className="h-4 w-20" />
              </div>
            </>
          ) : (
            <>
              <Avatar name={displayName} className="size-12 text-lg" />
              <div className="min-w-0">
                <p className="truncate text-base font-semibold tracking-tight">{displayName}</p>
                {member && (
                  <div className="mt-1">
                    <StatusBadge status={member.status} />
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        <CardContent className="p-0">
          {member && (
            <dl className="divide-y">
              <Field label="ID">
                <span className="font-mono text-xs text-muted-foreground">{member.id}</span>
              </Field>
              <Field label="이메일">
                {member.email ?? <span className="text-muted-foreground">미제공</span>}
              </Field>
              <Field label="이름">
                {member.name ?? <span className="text-muted-foreground">미제공</span>}
              </Field>
              <Field label="닉네임">
                {member.nickname ?? <span className="text-muted-foreground">미설정</span>}
              </Field>
              <Field label="상태">
                <StatusBadge status={member.status} />
              </Field>
              <Field label="가입일">{formatDateTime(member.createdAt)}</Field>
            </dl>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
