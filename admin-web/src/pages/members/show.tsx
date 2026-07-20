import { useShow } from '@refinedev/core'
import { Link } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import type { Member } from '@/types/member'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime } from '@/lib/format'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-3 gap-4 border-b py-3 last:border-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="col-span-2 text-sm">{children}</dd>
    </div>
  )
}

export function MemberShow() {
  const { query } = useShow<Member>({ resource: 'members' })
  const member = query.data?.data

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Button variant="outline" size="icon" asChild>
          <Link to="/members">
            <ArrowLeft className="size-4" />
          </Link>
        </Button>
        <h1 className="text-2xl font-semibold tracking-tight">회원 상세</h1>
      </div>

      <Card className="max-w-2xl">
        <CardHeader>
          <CardTitle className="text-base">
            {query.isLoading ? '불러오는 중…' : (member?.nickname ?? `회원 #${member?.id}`)}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {member && (
            <dl>
              <Field label="ID">
                <span className="font-mono">{member.id}</span>
              </Field>
              <Field label="이메일">{member.email ?? <span className="text-muted-foreground">미제공</span>}</Field>
              <Field label="닉네임">
                {member.nickname ?? <span className="text-muted-foreground">미설정</span>}
              </Field>
              <Field label="상태">
                {member.status === 'ACTIVE' ? (
                  <Badge variant="success">활성</Badge>
                ) : (
                  <Badge variant="muted">탈퇴</Badge>
                )}
              </Field>
              <Field label="가입일">{formatDateTime(member.createdAt)}</Field>
            </dl>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
