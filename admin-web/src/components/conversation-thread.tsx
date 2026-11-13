import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { CornerDownRight, EyeOff, FlaskConical, Reply } from 'lucide-react'
import { apiFetch } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  AWKWARD_TYPE_META,
  CHARACTER_META,
  KIND_LABEL,
  VERDICT_META,
  orderThread,
  type PlaygroundInput,
  type ThreadMessage,
} from '@/lib/quality'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

function characterLabel(message?: ThreadMessage) {
  return message?.emotionType ? CHARACTER_META[message.emotionType].label : '유저'
}

/** 근거 문장에 밑줄을 긋는다. 공백만 다른 경우까지 찾는다. */
function Highlighted({ text, evidence }: { text: string; evidence: string | null }) {
  const target = evidence?.trim()
  if (!target) {
    return <>{text}</>
  }
  const pattern = new RegExp(target.split(/\s+/).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s*'))
  const match = pattern.exec(text)
  if (!match) {
    return <>{text}</>
  }
  return (
    <>
      {text.slice(0, match.index)}
      <span className="underline decoration-red-500 decoration-2 underline-offset-4">{match[0]}</span>
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
    <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={open} disabled={loading}>
      <FlaskConical className="size-3.5" />
      실험실에서 열기
    </Button>
  )
}

export function ConversationThread({
  messages,
  focusMessageId,
  renderResponseExtra,
}: {
  messages: ThreadMessage[]
  focusMessageId?: number | null
  renderResponseExtra?: (message: ThreadMessage) => ReactNode
}) {
  const byId = new Map(messages.map((m) => [m.id, m]))
  const focusRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    focusRef.current?.scrollIntoView({ block: 'center' })
  }, [focusMessageId, messages])

  return (
    <div className="space-y-3">
      {orderThread(messages).map((message) => {
        const target = message.repliesToMessageId ? byId.get(message.repliesToMessageId) : undefined
        const focused = message.id === focusMessageId
        if (message.senderType === 'USER') {
          return (
            <div key={message.id} className="flex flex-col items-end gap-1">
              {target && (
                <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                  <Reply className="size-3" />
                  {characterLabel(target)}에게 답장
                </span>
              )}
              <div className="max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-tr-sm bg-foreground px-3.5 py-2 text-sm text-background">
                {message.content}
              </div>
            </div>
          )
        }
        const meta = message.emotionType ? CHARACTER_META[message.emotionType] : null
        const response = message.response
        const ai = response?.aiVerdict
        const nested = target?.senderType === 'CHARACTER'
        return (
          <div key={message.id} ref={focused ? focusRef : undefined} className={cn('flex flex-col gap-1', nested && 'pl-6')}>
            <span className="flex items-center gap-1.5 text-xs font-medium" style={{ color: meta?.color }}>
              {nested && <CornerDownRight className="size-3 text-muted-foreground" />}
              {meta?.emoji} {meta?.label}
              {nested && <span className="font-normal text-muted-foreground">→ {characterLabel(target)}</span>}
            </span>
            <div
              className={cn(
                'max-w-[90%] whitespace-pre-wrap rounded-2xl rounded-tl-sm border bg-background px-3.5 py-2 text-sm',
                ai?.verdict === 'AWKWARD' && 'border-red-200 bg-red-50/40',
                focused && 'ring-2 ring-primary ring-offset-2',
              )}
            >
              <Highlighted text={message.content} evidence={ai?.verdict === 'AWKWARD' ? ai.evidence : null} />
            </div>
            {response && (
              <div className="max-w-[90%] space-y-1 pl-1">
                <div className="flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
                  {ai ? (
                    <Badge variant={VERDICT_META[ai.verdict].variant} title={`채점 ${ai.judgeModel} · 프롬프트 v${ai.judgePromptVersion}`}>
                      AI {VERDICT_META[ai.verdict].label}
                    </Badge>
                  ) : response.aiHidden ? (
                    <Badge variant="muted">
                      <EyeOff className="mr-1 size-3" />
                      AI 판정 가려짐
                    </Badge>
                  ) : (
                    <Badge variant="muted">AI 미채점</Badge>
                  )}
                  {response.myVerdict && <Badge variant="outline">내 판정 {VERDICT_META[response.myVerdict.verdict].label}</Badge>}
                  {response.humanVerdictCount > 0 && (
                    <span>
                      사람 {response.humanVerdictCount}명 중 어색함 {response.humanAwkwardCount}
                    </span>
                  )}
                  <span>
                    {KIND_LABEL[response.kind]}
                    {response.model ? ` · ${response.model}` : ' · 생성 정보 없음'}
                    {response.typePromptVersion !== null && ` · v${response.typePromptVersion}`}
                  </span>
                  <OpenInPlayground messageId={message.id} />
                </div>
                {ai?.verdict === 'AWKWARD' && (
                  <p className="text-xs text-red-700">
                    {ai.awkwardTypes.map((t) => AWKWARD_TYPE_META[t].label).join(', ')}
                    {ai.reason && ` · ${ai.reason}`}
                  </p>
                )}
                {renderResponseExtra?.(message)}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

export function ThreadHeader({ title, createdAt, summary }: { title: string | null; createdAt: string; summary: string | null }) {
  return (
    <div className="space-y-1">
      <p className="text-sm font-medium">{title ?? '제목 없음'}</p>
      <p className="text-xs text-muted-foreground">{formatDateTime(createdAt)}</p>
      {summary && <p className="rounded-md bg-muted/50 px-2.5 py-1.5 text-xs text-muted-foreground">요약: {summary}</p>}
    </div>
  )
}
