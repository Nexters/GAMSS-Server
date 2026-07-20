import { useState } from 'react'
import { useList } from '@refinedev/core'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Search } from 'lucide-react'
import type { Member } from '@/types/member'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime } from '@/lib/format'

const PAGE_SIZE = 10

export function MemberList() {
  const navigate = useNavigate()
  const [current, setCurrent] = useState(1)
  const [search, setSearch] = useState('')

  const { data, isLoading } = useList<Member>({
    resource: 'members',
    pagination: { current, pageSize: PAGE_SIZE },
    filters: search.trim() ? [{ field: 'q', operator: 'contains', value: search.trim() }] : [],
  })

  const members = data?.data ?? []
  const total = data?.total ?? 0
  const lastPage = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">회원 관리</h1>
        <p className="mt-1 text-sm text-muted-foreground">가입한 회원을 조회합니다. 총 {total}명.</p>
      </div>

      <Card>
        <div className="flex items-center justify-between gap-4 border-b p-4">
          <div className="relative w-full max-w-xs">
            <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-8"
              placeholder="이메일·닉네임 검색"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setCurrent(1)
              }}
            />
          </div>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-16">ID</TableHead>
              <TableHead>이메일</TableHead>
              <TableHead>닉네임</TableHead>
              <TableHead className="w-28">상태</TableHead>
              <TableHead className="w-44">가입일</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                  불러오는 중…
                </TableCell>
              </TableRow>
            ) : members.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                  회원이 없습니다.
                </TableCell>
              </TableRow>
            ) : (
              members.map((member) => (
                <TableRow
                  key={member.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/members/${member.id}`)}
                >
                  <TableCell className="font-mono text-muted-foreground">{member.id}</TableCell>
                  <TableCell>{member.email ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  <TableCell>
                    {member.nickname ?? <span className="text-muted-foreground">미설정</span>}
                  </TableCell>
                  <TableCell>
                    {member.status === 'ACTIVE' ? (
                      <Badge variant="success">활성</Badge>
                    ) : (
                      <Badge variant="muted">탈퇴</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatDateTime(member.createdAt)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        <div className="flex items-center justify-between border-t px-4 py-3">
          <p className="text-sm text-muted-foreground">
            {total === 0 ? 0 : (current - 1) * PAGE_SIZE + 1}–{Math.min(current * PAGE_SIZE, total)} / {total}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={current <= 1}
              onClick={() => setCurrent((c) => Math.max(1, c - 1))}
            >
              <ChevronLeft className="size-4" />
              이전
            </Button>
            <span className="text-sm text-muted-foreground">
              {current} / {lastPage}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={current >= lastPage}
              onClick={() => setCurrent((c) => Math.min(lastPage, c + 1))}
            >
              다음
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      </Card>
    </div>
  )
}
