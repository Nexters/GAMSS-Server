import { useEffect } from 'react'
import { useCustom } from '@refinedev/core'
import { RotateCcw, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ConversationThread, ThreadHeader } from '@/components/conversation-thread'
import { ApiError } from '@/lib/api'
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
  const { data, isLoading, isError, error, refetch } = useCustom<ThreadView>({
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
    <aside className="fixed inset-y-0 right-0 z-40 flex w-full max-w-[600px] flex-col border-l bg-background shadow-[-24px_0_48px_-24px_rgba(0,0,0,0.25)] animate-in slide-in-from-right duration-200">
      <div className="flex items-start justify-between gap-3 border-b px-5 py-4">
        {thread ? <ThreadHeader thread={thread} /> : <Skeleton className="h-10 w-48" />}
        <Button variant="ghost" size="sm" onClick={onClose} aria-label="닫기">
          <X className="size-4" />
        </Button>
      </div>
      <div className="flex-1 overflow-y-auto bg-muted/20 px-5 py-5">
        {isError && !thread ? (
          <div className="flex items-center gap-3">
            <p className="text-sm text-muted-foreground">{error instanceof ApiError && error.detail ? error.detail : '대화를 불러오지 못했습니다.'}</p>
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
      <p className="flex justify-between border-t px-5 py-2.5 text-[11px] text-muted-foreground">
        <span>원문을 열면 열람 기록이 남습니다</span>
        <span className="tabular-nums">회원 #{thread?.memberId ?? '-'} · 대화 #{conversationId} · Esc 로 닫기</span>
      </p>
    </aside>
  )
}
