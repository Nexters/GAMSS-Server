import { useShow } from '@refinedev/core'
import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Member } from '@/types/member'
import { Avatar } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
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
  const displayName = member?.nickname ?? member?.email ?? `회원 #${member?.id ?? ''}`

  return (
    <div className="space-y-6">
      <PageHeader
        title="회원 상세"
        actions={
          <Button variant="outline" size="sm" asChild>
            <Link to="/members">
              <ArrowLeft className="size-4" />
              목록으로
            </Link>
          </Button>
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
