import { useState } from 'react'
import { useList } from '@refinedev/core'
import type { CrudFilters } from '@refinedev/core'
import { useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Search, Users } from 'lucide-react'
import type { Member, MemberStatus } from '@/types/member'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PageHeader } from '@/components/page-header'
import { StatusBadge } from '@/components/status-badge'
import { cn } from '@/lib/utils'
import { formatDateTime } from '@/lib/format'

const PAGE_SIZE = 10

type StatusTab = 'ALL' | MemberStatus

const STATUS_TABS: { value: StatusTab; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'ACTIVE', label: '활성' },
  { value: 'WITHDRAWN', label: '탈퇴' },
]

export function MemberList() {
  const navigate = useNavigate()
  const [current, setCurrent] = useState(1)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<StatusTab>('ALL')

  const filters: CrudFilters = []
  if (search.trim()) {
    filters.push({ field: 'q', operator: 'contains', value: search.trim() })
  }
  if (status !== 'ALL') {
    filters.push({ field: 'status', operator: 'eq', value: status })
  }

  const { data, isLoading } = useList<Member>({
    resource: 'members',
    pagination: { current, pageSize: PAGE_SIZE },
    filters,
  })

  const members = data?.data ?? []
  const total = data?.total ?? 0
  const lastPage = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const rangeStart = total === 0 ? 0 : (current - 1) * PAGE_SIZE + 1
  const rangeEnd = Math.min(current * PAGE_SIZE, total)

  return (
    <div className="space-y-6">
      <PageHeader
        title="회원 관리"
        description={
          <>
            가입한 회원을 조회합니다. 총 <span className="font-medium text-foreground">{total.toLocaleString()}</span>명
          </>
        }
      />

      <Card className="overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b p-3">
          <div className="relative w-full max-w-xs">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
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
          <div className="inline-flex items-center rounded-md border bg-muted/40 p-0.5">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.value}
                type="button"
                onClick={() => {
                  setStatus(tab.value)
                  setCurrent(1)
                }}
                className={cn(
                  'rounded px-3 py-1 text-xs font-medium transition-colors',
                  status === tab.value
                    ? 'bg-background text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-16">ID</TableHead>
              <TableHead>이메일</TableHead>
              <TableHead>닉네임</TableHead>
              <TableHead className="w-28">상태</TableHead>
              <TableHead className="w-48">가입일</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: PAGE_SIZE }).map((_, i) => (
                <TableRow key={i} className="hover:bg-transparent">
                  <TableCell>
                    <Skeleton className="h-4 w-6" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-40" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-24" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-5 w-12 rounded-full" />
                  </TableCell>
                  <TableCell>
                    <Skeleton className="h-4 w-32" />
                  </TableCell>
                </TableRow>
              ))
            ) : members.length === 0 ? (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={5} className="py-16">
                  <div className="flex flex-col items-center gap-2 text-center">
                    <div className="flex size-10 items-center justify-center rounded-full bg-muted">
                      <Users className="size-5 text-muted-foreground" />
                    </div>
                    <p className="text-sm font-medium">회원이 없습니다</p>
                    <p className="text-xs text-muted-foreground">
                      {search.trim() ? '검색 조건에 맞는 회원이 없습니다.' : '아직 가입한 회원이 없습니다.'}
                    </p>
                  </div>
                </TableCell>
              </TableRow>
            ) : (
              members.map((member) => (
                <TableRow
                  key={member.id}
                  className="cursor-pointer"
                  onClick={() => navigate(`/members/${member.id}`)}
                >
                  <TableCell className="font-mono text-xs text-muted-foreground">{member.id}</TableCell>
                  <TableCell className="font-medium">
                    {member.email ?? <span className="font-normal text-muted-foreground">—</span>}
                  </TableCell>
                  <TableCell>
                    {member.nickname ?? <span className="text-muted-foreground">미설정</span>}
                  </TableCell>
                  <TableCell>
                    <StatusBadge status={member.status} />
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">{formatDateTime(member.createdAt)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>

        <div className="flex items-center justify-between border-t px-4 py-3">
          <p className="text-xs text-muted-foreground">
            {total === 0 ? '0개' : `${rangeStart}–${rangeEnd} / ${total.toLocaleString()}`}
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={current <= 1 || isLoading}
              onClick={() => setCurrent((c) => Math.max(1, c - 1))}
            >
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
    </div>
  )
}
