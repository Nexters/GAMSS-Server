import { Fragment, useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { ChevronLeft, ChevronRight, Eye, History, PencilLine, RotateCcw, X } from 'lucide-react'
import type { PromptRevision, PromptRevisionDetail, PromptRevisionPage } from '@/types/promptRevision'
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
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { formatDateTime } from '@/lib/format'

const PAGE_SIZE = 5

interface Props {
  type: string
  /** 현재 적용(저장)된 프롬프트. 같은 내용의 리비전은 복원 대상이 아니고, 값이 바뀌면 목록을 다시 불러온다. */
  currentPrompt: string
  /** 리비전 전문을 에디터에 올려 저장 전 검토·수정할 수 있게 한다. */
  onLoadToEditor: (content: string) => void
  /** 복원 성공 시 서버가 돌려준 새 현재 프롬프트. */
  onRestored: (content: string) => void
}

/** 리비전 한 건의 전문 조회 + 검토·복원 액션. 펼쳤을 때만 마운트되어 전문을 불러온다. */
function RevisionDetailPanel({
  revision,
  currentPrompt,
  onLoadToEditor,
  onRestore,
  restoring,
  restoreFailed,
  onClose,
}: {
  revision: PromptRevision
  currentPrompt: string
  onLoadToEditor: (content: string) => void
  onRestore: (revisionId: number) => void
  restoring: boolean
  restoreFailed: boolean
  onClose: () => void
}) {
  const { data, isLoading, isError, refetch } = useCustom<PromptRevisionDetail>({
    url: `/api/admin/llm-settings/prompt/revisions/${revision.id}`,
    method: 'get',
  })
  const detail = data?.data
  const isCurrentContent = detail?.systemPrompt === currentPrompt

  if (isError && !detail) {
    return (
      <div className="flex items-center gap-3 border-t bg-muted/20 p-4">
        <p className="text-sm text-muted-foreground">리비전 내용을 불러오지 못했습니다.</p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RotateCcw className="size-4" />
          다시 시도
        </Button>
      </div>
    )
  }

  return (
    <div className="space-y-3 border-t bg-muted/20 p-4">
      {isLoading || !detail ? (
        <Skeleton className="h-40 w-full" />
      ) : (
        <>
          <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-md border bg-background p-4 font-mono text-[13px] leading-relaxed">
            {detail.systemPrompt}
          </pre>
          <div className="flex flex-wrap items-center gap-2">
            <p className="mr-auto text-xs text-muted-foreground">
              {isCurrentContent
                ? '현재 적용 중인 내용과 같아 복원할 것이 없습니다.'
                : '에디터로 불러오면 저장 전에 검토·수정할 수 있고, 복원은 즉시 현재 프롬프트로 반영됩니다.'}
            </p>
            {restoreFailed && <span className="text-sm text-destructive">복원에 실패했습니다</span>}
            <Button variant="outline" size="sm" onClick={onClose}>
              <X className="size-4" />
              닫기
            </Button>
            <Button variant="outline" size="sm" onClick={() => onLoadToEditor(detail.systemPrompt)}>
              <PencilLine className="size-4" />
              에디터로 불러오기
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" disabled={restoring || isCurrentContent}>
                  <RotateCcw className="size-4" />이 버전으로 복원
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>v{revision.version}으로 복원할까요?</AlertDialogTitle>
                  <AlertDialogDescription className="break-keep">
                    현재 프롬프트가 이 버전의 내용으로 바뀌고{' '}
                    <span className="font-medium text-foreground">다음 생성부터 즉시 적용됩니다.</span> 복원도 새
                    리비전으로 기록되므로 언제든 다시 되돌릴 수 있습니다.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>취소</AlertDialogCancel>
                  <AlertDialogAction onClick={() => onRestore(revision.id)}>복원</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </>
      )}
    </div>
  )
}

export function PromptRevisionHistory({ type, currentPrompt, onLoadToEditor, onRestored }: Props) {
  const [page, setPage] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const { data, isLoading, isError, refetch } = useCustom<PromptRevisionPage>({
    url: `/api/admin/llm-settings/prompt/revisions?promptType=${type}&page=${page}&size=${PAGE_SIZE}`,
    method: 'get',
    queryOptions: { queryKey: ['prompt-revisions', type, page, currentPrompt] },
  })
  const { mutate: restore, isLoading: restoring } = useCustomMutation()
  const [restoreFailedId, setRestoreFailedId] = useState<number | null>(null)

  const revisions = data?.data?.content ?? []
  const totalPages = data?.data?.totalPages ?? 0
  const total = data?.data?.totalElements ?? 0

  const onRestore = (revisionId: number) => {
    setRestoreFailedId(null)
    restore(
      { url: `/api/admin/llm-settings/prompt/revisions/${revisionId}/restore`, method: 'post', values: {} },
      {
        onSuccess: (result) => {
          const updated = (result.data as { systemPrompt?: string } | undefined)?.systemPrompt
          if (updated !== undefined) {
            // currentPrompt prop이 바뀌면 쿼리 키가 갱신돼 목록도 다시 불러온다(별도 refetch 불필요).
            onRestored(updated)
          }
          setExpandedId(null)
          setPage(0)
        },
        onError: () => setRestoreFailedId(revisionId),
      },
    )
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center gap-2 border-b p-4">
        <History className="size-4 text-muted-foreground" />
        <span className="text-sm font-medium">버전 이력</span>
        {total > 0 && (
          <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] font-medium tabular-nums text-muted-foreground">
            {total.toLocaleString()}개
          </span>
        )}
        <span className="ml-auto text-xs text-muted-foreground">저장할 때마다 자동으로 기록됩니다</span>
      </div>

      {isError && !data ? (
        <div className="flex items-center gap-3 p-6">
          <p className="text-sm text-muted-foreground">이력을 불러오지 못했습니다.</p>
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
      ) : revisions.length === 0 ? (
        <p className="p-6 text-center text-sm text-muted-foreground">
          아직 이력이 없습니다. 프롬프트를 저장하면 첫 버전이 기록됩니다.
        </p>
      ) : (
        <>
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead className="whitespace-nowrap">버전</TableHead>
                <TableHead className="w-56">저장자</TableHead>
                <TableHead className="w-44">저장 시각</TableHead>
                <TableHead>내용</TableHead>
                <TableHead className="w-20 text-right">길이</TableHead>
                <TableHead className="w-16" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {revisions.map((revision) => (
                <Fragment key={revision.id}>
                  <TableRow
                    className="cursor-pointer"
                    data-state={expandedId === revision.id ? 'selected' : undefined}
                    onClick={() => setExpandedId(expandedId === revision.id ? null : revision.id)}
                  >
                    <TableCell>
                      <div className="flex flex-nowrap items-center gap-1.5">
                        <Badge variant="outline" className="whitespace-nowrap tabular-nums">
                          v{revision.version}
                        </Badge>
                        {revision.restoredFromVersion != null && (
                          <Badge variant="secondary" className="gap-1 whitespace-nowrap text-[10px] font-normal text-muted-foreground">
                            <RotateCcw className="size-3" />
                            v{revision.restoredFromVersion}에서 복원
                          </Badge>
                        )}
                        {revision.version === total && (
                          <Badge className="whitespace-nowrap text-[10px]">현재 적용 중</Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-sm">
                      {revision.savedBy ?? <span className="text-muted-foreground">시스템</span>}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDateTime(revision.createdAt)}</TableCell>
                    <TableCell className="max-w-0">
                      <p className="truncate text-xs text-muted-foreground">{revision.preview}</p>
                    </TableCell>
                    <TableCell className="text-right text-xs tabular-nums text-muted-foreground">
                      {revision.length.toLocaleString()}자
                    </TableCell>
                    <TableCell className="text-right">
                      <Eye className="ml-auto size-4 text-muted-foreground" />
                    </TableCell>
                  </TableRow>
                  {expandedId === revision.id && (
                    <TableRow className="hover:bg-transparent">
                      <TableCell colSpan={6} className="p-0">
                        <RevisionDetailPanel
                          revision={revision}
                          currentPrompt={currentPrompt}
                          onLoadToEditor={onLoadToEditor}
                          onRestore={onRestore}
                          restoring={restoring}
                          restoreFailed={restoreFailedId === revision.id}
                          onClose={() => setExpandedId(null)}
                        />
                      </TableCell>
                    </TableRow>
                  )}
                </Fragment>
              ))}
            </TableBody>
          </Table>

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
    </Card>
  )
}
