import { useCallback, useEffect, useRef, useState } from 'react'
import { CheckCircle2, Keyboard, Loader2 } from 'lucide-react'
import { apiFetch, ApiError } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { ConversationThread, ThreadHeader } from '@/components/conversation-thread'
import {
  AWKWARD_TYPES,
  AWKWARD_TYPE_META,
  CHARACTER_META,
  VERDICT_META,
  type AwkwardType,
  type HumanVerdictResult,
  type LabelingNext,
  type ThreadView,
  type Verdict,
} from '@/lib/quality'
import { cn } from '@/lib/utils'

interface Target {
  messageId: number
  conversationId: number
}

const VERDICTS: Verdict[] = ['AWKWARD', 'NATURAL', 'UNSURE']

export function LabelingMode({ onProgress }: { onProgress: (mine: number, target: number) => void }) {
  const [history, setHistory] = useState<Target[]>([])
  const [index, setIndex] = useState(-1)
  const [thread, setThread] = useState<ThreadView | null>(null)
  const [verdict, setVerdict] = useState<Verdict | null>(null)
  const [types, setTypes] = useState<AwkwardType[]>([])
  const [memo, setMemo] = useState('')
  const [result, setResult] = useState<HumanVerdictResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const started = useRef(false)
  const target = useRef(100)

  const current = index >= 0 ? history[index] : undefined
  const message = thread?.messages.find((m) => m.id === current?.messageId)

  const loadThread = useCallback(async (target: Target) => {
    setLoading(true)
    setError(null)
    try {
      const view = await apiFetch<ThreadView>(`/api/admin/conversations/${target.conversationId}/thread?blind=true`)
      setThread(view)
      const mine = view.messages.find((m) => m.id === target.messageId)?.response?.myVerdict
      setVerdict(mine?.verdict ?? null)
      setTypes(mine?.awkwardTypes ?? [])
      setMemo(mine?.memo ?? '')
      setResult(null)
    } catch {
      setError('대화를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  const next = useCallback(async () => {
    if (index < history.length - 1) {
      setIndex(index + 1)
      await loadThread(history[index + 1])
      return
    }
    setLoading(true)
    try {
      const exclude = history.map((h) => `exclude=${h.messageId}`).join('&')
      const found = await apiFetch<LabelingNext>(`/api/admin/conversation-quality/labeling/next?${exclude}`)
      target.current = found.target
      onProgress(found.mineCount, found.target)
      if (found.messageId === null || found.conversationId === null) {
        setDone(true)
        setLoading(false)
        return
      }
      const item = { messageId: found.messageId, conversationId: found.conversationId }
      setHistory((h) => [...h, item])
      setIndex(history.length)
      await loadThread(item)
    } catch {
      setError('다음 응답을 불러오지 못했습니다.')
      setLoading(false)
    }
  }, [history, index, loadThread, onProgress])

  const previous = useCallback(async () => {
    if (index <= 0) {
      return
    }
    setIndex(index - 1)
    await loadThread(history[index - 1])
  }, [history, index, loadThread])

  const submit = useCallback(async () => {
    if (!current || !verdict || (verdict === 'AWKWARD' && types.length === 0)) {
      return
    }
    setError(null)
    try {
      const saved = await apiFetch<HumanVerdictResult>(`/api/admin/conversation-quality/responses/${current.messageId}/human-verdict`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ verdict, awkwardTypes: verdict === 'AWKWARD' ? types : [], memo: memo.trim() || null }),
      })
      onProgress(saved.mineCount, target.current)
      const view = await apiFetch<ThreadView>(`/api/admin/conversations/${current.conversationId}/thread?blind=true`)
      setThread(view)
      setResult(saved)
    } catch (e) {
      setError(e instanceof ApiError && e.detail ? e.detail : '판정을 저장하지 못했습니다.')
    }
  }, [current, memo, onProgress, types, verdict])

  useEffect(() => {
    if (!started.current) {
      started.current = true
      void next()
    }
  }, [next])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName
      if (tag === 'TEXTAREA' || tag === 'INPUT' || tag === 'SELECT' || e.metaKey || e.ctrlKey || loading) {
        return
      }
      const key = e.key.toLowerCase()
      if (key === 'j') {
        void next()
      } else if (key === 'k') {
        void previous()
      } else if (key === 'enter') {
        void submit()
      } else if (key === 'escape' || key === 'backspace') {
        setVerdict(null)
        setTypes([])
      } else if (/^[1-7]$/.test(key)) {
        const n = Number(key)
        if (verdict === 'AWKWARD' && !result) {
          const type = AWKWARD_TYPES[n - 1]
          setTypes((prev) => (prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]))
        } else if (n <= 3) {
          setVerdict(VERDICTS[n - 1])
          setTypes([])
          setResult(null)
        }
      } else {
        return
      }
      e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [loading, next, previous, result, submit, verdict])

  if (done) {
    return (
      <Card className="flex flex-col items-center gap-2 p-10 text-center">
        <CheckCircle2 className="size-6 text-emerald-600" />
        <p className="text-sm font-medium">최근 30일 응답을 모두 판정했습니다</p>
        <p className="text-xs text-muted-foreground">아래에서 정답 세트로 AI 채점을 돌려 일치율을 확인하세요.</p>
      </Card>
    )
  }

  const ai = result?.aiVerdict
  const meta = message?.emotionType ? CHARACTER_META[message.emotionType] : null

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_380px]">
      <Card className="max-h-[70vh] overflow-y-auto p-5">
        {thread ? (
          <div className="space-y-4">
            <ThreadHeader title={thread.title} createdAt={thread.createdAt} summary={thread.summary} />
            <ConversationThread messages={thread.messages} focusMessageId={current?.messageId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="size-4 animate-spin" />
            불러오는 중
          </div>
        )}
      </Card>

      <Card className="space-y-4 p-5 lg:sticky lg:top-4 lg:self-start">
        <div className="space-y-1">
          <p className="text-xs text-muted-foreground">판정할 응답 (대화에서 테두리로 표시)</p>
          {message && (
            <p className="text-sm">
              <span className="font-medium" style={{ color: meta?.color }}>
                {meta?.emoji} {meta?.label}
              </span>{' '}
              {message.content}
            </p>
          )}
        </div>

        <div className="grid grid-cols-3 gap-2">
          {VERDICTS.map((v) => (
            <Button
              key={v}
              variant={verdict === v ? 'default' : 'outline'}
              size="sm"
              onClick={() => {
                setVerdict(v)
                setTypes([])
                setResult(null)
              }}
            >
              <kbd className="rounded bg-muted px-1 text-[10px] text-muted-foreground">{VERDICT_META[v].key}</kbd>
              {VERDICT_META[v].label}
            </Button>
          ))}
        </div>

        {verdict === 'AWKWARD' && (
          <div className="flex flex-wrap gap-1.5">
            {AWKWARD_TYPES.map((t, i) => (
              <button
                key={t}
                type="button"
                title={AWKWARD_TYPE_META[t].hint}
                onClick={() => setTypes((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]))}
                className={cn(
                  'rounded-full border px-2.5 py-1 text-xs',
                  types.includes(t) ? 'border-red-300 bg-red-50 text-red-700' : 'text-muted-foreground hover:bg-muted',
                )}
              >
                <span className="mr-1 tabular-nums opacity-60">{i + 1}</span>
                {AWKWARD_TYPE_META[t].label}
              </button>
            ))}
          </div>
        )}

        <Textarea value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="메모 (선택)" className="min-h-[3.5rem] text-sm" maxLength={500} />

        <div className="flex items-center gap-2">
          <Button size="sm" onClick={() => void submit()} disabled={!verdict || (verdict === 'AWKWARD' && types.length === 0) || loading}>
            제출 <kbd className="rounded bg-primary-foreground/20 px-1 text-[10px]">Enter</kbd>
          </Button>
          <Button variant="outline" size="sm" onClick={() => void previous()} disabled={index <= 0 || loading}>
            이전 <kbd className="text-[10px] text-muted-foreground">K</kbd>
          </Button>
          <Button variant="outline" size="sm" onClick={() => void next()} disabled={loading}>
            다음 <kbd className="text-[10px] text-muted-foreground">J</kbd>
          </Button>
        </div>
        {error && <p className="text-sm text-destructive">{error}</p>}

        {result && (
          <div
            className={cn(
              'space-y-1.5 rounded-lg border p-3 text-sm',
              result.agreesWithAi === false ? 'border-amber-300 bg-amber-50' : 'border-emerald-200 bg-emerald-50/60',
            )}
          >
            <p className="font-medium">
              {ai === null
                ? 'AI가 아직 채점하지 않은 응답입니다'
                : result.agreesWithAi
                  ? 'AI와 판정이 같습니다'
                  : 'AI와 판정이 다릅니다'}
            </p>
            {ai && (
              <>
                <div className="flex flex-wrap items-center gap-1.5">
                  <Badge variant={VERDICT_META[ai.verdict].variant}>AI {VERDICT_META[ai.verdict].label}</Badge>
                  {ai.awkwardTypes.map((t) => (
                    <Badge key={t} variant="outline">
                      {AWKWARD_TYPE_META[t].label}
                    </Badge>
                  ))}
                </div>
                {ai.evidence && <p className="text-xs text-muted-foreground">근거: “{ai.evidence}”</p>}
                {ai.reason && <p className="text-xs text-muted-foreground">사유: {ai.reason}</p>}
              </>
            )}
            <p className="text-xs text-muted-foreground">J 를 눌러 다음 응답으로 넘어가세요.</p>
          </div>
        )}

        <p className="flex items-start gap-1.5 text-[11px] text-muted-foreground">
          <Keyboard className="mt-0.5 size-3.5 shrink-0" />
          1 어색함 · 2 자연스러움 · 3 애매함. 어색함이면 숫자로 유형을 고르고 Esc 로 판정을 지웁니다. AI 판정은 제출한 뒤에 보입니다.
        </p>
      </Card>
    </div>
  )
}
