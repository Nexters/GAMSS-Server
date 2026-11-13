import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, CornerDownRight, FlaskConical, HelpCircle, Loader2, Reply, Sparkles } from 'lucide-react'
import { apiFetch } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { VerdictBadge } from '@/components/verdict-badge'
import {
  AWKWARD_TYPE_META,
  CHARACTER_META,
  orderThread,
  type PlaygroundInput,
  type ResponseInfo,
  type ThreadMessage,
  type ThreadView,
} from '@/lib/quality'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

const STATUS_LABEL: Record<ThreadView['status'], string> = { ACTIVE: '진행 중', ENDED: '종료', DELETED: '삭제됨' }

function speakerOf(message?: ThreadMessage) {
  return message?.emotionType ? CHARACTER_META[message.emotionType].label : '유저'
}

/** 근거 문장에 밑줄을 긋는다. 공백만 다른 경우까지 찾는다. */
function Highlighted({ text, evidence }: { text: string; evidence: string | null }) {
  const target = evidence?.trim()
  const match = target
    ? new RegExp(target.split(/\s+/).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s*')).exec(text)
    : null
  if (!match) {
    return <>{text}</>
  }
  return (
    <>
      {text.slice(0, match.index)}
      <mark className="rounded-sm bg-rose-100/70 px-0.5 text-inherit underline decoration-rose-500 decoration-2 underline-offset-[5px]">{match[0]}</mark>
      {text.slice(match.index + match[0].length)}
    </>
  )
}

function OpenInPlayground({ messageId }: { messageId: number }) {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const open = async () => {
    setLoading(true)
    try {
      const prefill = await apiFetch<PlaygroundInput>(`/api/admin/conversation-quality/responses/${messageId}/playground-input`)
      navigate('/playground', { state: { prefill } })
    } finally {
      setLoading(false)
    }
  }
  return (
    <button
      type="button"
      onClick={open}
      disabled={loading}
      className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium text-primary hover:bg-primary/10"
    >
      {loading ? <Loader2 className="size-3 animate-spin" /> : <FlaskConical className="size-3" />}
      실험실에서 열기
    </button>
  )
}

function AiMark({ response }: { response: ResponseInfo }) {
  const ai = response.aiVerdict
  if (!ai) {
    return response.aiHidden ? null : <span className="text-[11px] text-muted-foreground/70">AI 미채점</span>
  }
  if (ai.verdict === 'NATURAL') {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] text-emerald-600" title={`AI 자연스러움 · ${ai.judgeModel} · 채점 프롬프트 v${ai.judgePromptVersion}`}>
        <CheckCircle2 className="size-3.5" />
      </span>
    )
  }
  if (ai.verdict === 'UNSURE') {
    return (
      <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground" title={ai.reason ?? 'AI가 판단하지 못했습니다'}>
        <HelpCircle className="size-3.5" />
        애매함
      </span>
    )
  }
  return <VerdictBadge verdict="AWKWARD" prefix="AI" className="px-1.5 py-0 text-[11px]" />
}

function GenerationDivider({ response }: { response: ResponseInfo }) {
  const prompt = response.kind === 'COMMENT' ? '댓글' : '답글'
  return (
    <div className="flex items-center gap-2 pt-1 text-[11px] text-muted-foreground/80">
      <Sparkles className="size-3" />
      <span>
        {response.kind === 'COMMENT' ? '캐릭터 댓글 생성' : '답글 재응답 생성'}
        {response.model ? ` · ${response.model}` : ' · 생성 정보 없음'}
        {response.typePromptVersion !== null && ` · ${prompt} 프롬프트 v${response.typePromptVersion}`}
      </span>
      <span className="h-px flex-1 bg-border" />
    </div>
  )
}

export function ConversationThread({
  messages,
  focusMessageId,
  mode = 'review',
}: {
  messages: ThreadMessage[]
  focusMessageId?: number | null
  mode?: 'review' | 'label'
}) {
  const byId = new Map(messages.map((m) => [m.id, m]))
  const focusRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    focusRef.current?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [focusMessageId])

  let lastTrigger: number | null = null

  return (
    <div className="space-y-3">
      {orderThread(messages).map((message) => {
        const target = message.repliesToMessageId ? byId.get(message.repliesToMessageId) : undefined
        const focused = message.id === focusMessageId
        const dimmed = mode === 'label' && !focused

        if (message.senderType === 'USER') {
          lastTrigger = null
          return (
            <div key={message.id} className={cn('flex flex-col items-end gap-1 transition-opacity', dimmed && 'opacity-45')}>
              {target && (
                <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                  <Reply className="size-3" />
                  {speakerOf(target)}에게 답장
                </span>
              )}
              <div className="max-w-[80%] whitespace-pre-wrap rounded-2xl rounded-br-md bg-zinc-900 px-3.5 py-2 text-sm leading-relaxed text-white">
                {message.content}
              </div>
            </div>
          )
        }

        const meta = message.emotionType ? CHARACTER_META[message.emotionType] : null
        const response = message.response
        const ai = response?.aiVerdict
        const nested = target?.senderType === 'CHARACTER'
        const showDivider = mode === 'review' && response && response.triggerMessageId !== lastTrigger
        lastTrigger = response?.triggerMessageId ?? lastTrigger
        const awkward = ai?.verdict === 'AWKWARD'

        return (
          <div key={message.id} className="space-y-3">
            {showDivider && <GenerationDivider response={response} />}
            <div ref={focused ? focusRef : undefined} className={cn('group flex gap-2.5 transition-opacity', nested && 'pl-8', dimmed && 'opacity-45')}>
              <div
                className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full text-sm"
                style={{ background: `${meta?.color}1a` }}
                aria-hidden
              >
                {meta?.emoji}
              </div>
              <div className="min-w-0 flex-1 space-y-1">
                <div className="flex items-center gap-2 text-xs">
                  <span className="font-semibold" style={{ color: meta?.color }}>
                    {meta?.label}
                  </span>
                  {nested && (
                    <span className="flex items-center gap-0.5 text-muted-foreground">
                      <CornerDownRight className="size-3" />
                      {speakerOf(target)}에게
                    </span>
                  )}
                  {response && mode === 'review' && <AiMark response={response} />}
                  {response && mode === 'review' && response.myVerdict && (
                    <VerdictBadge verdict={response.myVerdict.verdict} prefix="나" className="px-1.5 py-0 text-[11px]" />
                  )}
                  {response && mode === 'review' && response.humanVerdictCount > (response.myVerdict ? 1 : 0) && (
                    <span className="text-[10px] text-muted-foreground">사람 {response.humanVerdictCount}명 판정</span>
                  )}
                  {response && mode === 'review' && (
                    <span className={cn('ml-auto opacity-0 transition-opacity group-hover:opacity-100', (focused || awkward) && 'opacity-100')}>
                      <OpenInPlayground messageId={message.id} />
                    </span>
                  )}
                </div>
                <div
                  className={cn(
                    'inline-block max-w-full whitespace-pre-wrap rounded-2xl rounded-tl-md border bg-background px-3.5 py-2 text-sm leading-relaxed',
                    awkward && 'border-rose-200',
                    focused && 'shadow-[0_0_0_3px_hsl(var(--primary)/0.15)] ring-1 ring-primary',
                  )}
                >
                  <Highlighted text={message.content} evidence={awkward ? ai.evidence : null} />
                </div>
                {awkward && (
                  <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                    {ai.awkwardTypes.map((t) => (
                      <span key={t} className="rounded-full bg-rose-50 px-2 py-0.5 text-[11px] font-medium text-rose-700" title={AWKWARD_TYPE_META[t].hint}>
                        {AWKWARD_TYPE_META[t].label}
                      </span>
                    ))}
                    {ai.reason && <span className="text-xs text-muted-foreground">{ai.reason}</span>}
                  </div>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

export function ThreadHeader({ thread }: { thread: ThreadView }) {
  const responses = thread.messages.filter((m) => m.response)
  const awkward = responses.filter((m) => m.response?.aiVerdict?.verdict === 'AWKWARD').length
  return (
    <div className="min-w-0 space-y-1.5">
      <div className="flex items-center gap-2">
        <p className="truncate text-base font-semibold">{thread.title ?? '제목 없는 대화'}</p>
        <Badge variant={thread.status === 'ENDED' ? 'success' : 'secondary'}>{STATUS_LABEL[thread.status]}</Badge>
      </div>
      <p className="text-xs text-muted-foreground">
        {formatDateTime(thread.createdAt)} · 캐릭터 응답 {responses.length}개
        {awkward > 0 && <span className="text-rose-600"> · AI 어색함 {awkward}개</span>}
      </p>
      {thread.summary && <p className="line-clamp-2 rounded-md bg-muted/60 px-2.5 py-1.5 text-xs text-muted-foreground">{thread.summary}</p>}
    </div>
  )
}
