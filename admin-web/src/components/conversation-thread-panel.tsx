import { useEffect } from 'react'
import { useCustom } from '@refinedev/core'
import { RotateCcw, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ConversationThread, ThreadHeader } from '@/components/conversation-thread'
import type { ThreadView } from '@/lib/quality'

/** 오른쪽에서 열리는 대화 원문 패널. 토큰 사용량 표와 대화 품질 탭이 같이 쓴다. */
export function ConversationThreadPanel({
  conversationId,
  focusMessageId,
  onClose,
}: {
  conversationId: number
  focusMessageId?: number | null
  onClose: () => void
}) {
  const { data, isLoading, isError, refetch } = useCustom<ThreadView>({
    url: `/api/admin/conversations/${conversationId}/thread`,
    method: 'get',
  })
  const thread = data?.data

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <aside className="fixed inset-y-0 right-0 z-40 flex w-full max-w-xl flex-col border-l bg-background shadow-2xl">
      <div className="flex items-start justify-between gap-3 border-b px-5 py-4">
        {thread ? <ThreadHeader title={thread.title} createdAt={thread.createdAt} summary={thread.summary} /> : <Skeleton className="h-10 w-48" />}
        <Button variant="ghost" size="sm" onClick={onClose} aria-label="닫기">
          <X className="size-4" />
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto px-5 py-4">
        {isError && !thread ? (
          <div className="flex items-center gap-3">
            <p className="text-sm text-muted-foreground">대화를 불러오지 못했습니다.</p>
            <Button variant="outline" size="sm" onClick={() => refetch()}>
              <RotateCcw className="size-4" />
              다시 시도
            </Button>
          </div>
        ) : isLoading || !thread ? (
          <div className="space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-3/4" />
            ))}
          </div>
        ) : (
          <ConversationThread messages={thread.messages} focusMessageId={focusMessageId} />
        )}
      </div>
      <p className="border-t px-5 py-2 text-[11px] text-muted-foreground">대화 원문을 열면 열람 기록이 남습니다. 회원 #{thread?.memberId ?? '-'} · 대화 #{conversationId}</p>
    </aside>
  )
}
