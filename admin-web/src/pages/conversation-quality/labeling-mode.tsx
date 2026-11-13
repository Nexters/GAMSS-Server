import { useCallback, useEffect, useRef, useState } from 'react'
import { AlertTriangle, CheckCircle2, Keyboard, Loader2 } from 'lucide-react'
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
  const canSubmit = Boolean(verdict) && !(verdict === 'AWKWARD' && types.length === 0) && !loading

  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_400px]">
      <Card className="flex max-h-[calc(100vh-15rem)] min-h-[28rem] flex-col overflow-hidden">
        <div className="border-b px-5 py-4">{thread ? <ThreadHeader thread={thread} /> : <div className="h-10" />}</div>
        <div className="flex-1 overflow-y-auto bg-muted/20 px-5 py-5">
          {thread ? (
            <ConversationThread messages={thread.messages} focusMessageId={current?.messageId} mode="label" />
          ) : (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              불러오는 중
            </div>
          )}
        </div>
      </Card>

      <Card className="space-y-5 p-5 lg:sticky lg:top-4 lg:self-start">
        <div className="space-y-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">이 응답이 어색한가요?</p>
          {message ? (
            <div className="rounded-xl border bg-muted/30 p-3">
              <p className="mb-1 text-xs font-semibold" style={{ color: meta?.color }}>
                {meta?.emoji} {meta?.label}
              </p>
              <p className="text-sm leading-relaxed">{message.content}</p>
            </div>
          ) : (
            <div className="h-16 animate-pulse rounded-xl bg-muted" />
          )}
        </div>

        <div className="grid grid-cols-3 gap-2">
          {VERDICTS.map((v) => {
            const selected = verdict === v
            return (
              <button
                key={v}
                type="button"
                onClick={() => {
                  setVerdict(v)
                  setTypes([])
                  setResult(null)
                }}
                className={cn(
                  'flex flex-col items-center gap-1 rounded-xl border px-2 py-3 text-sm font-medium transition-all',
                  !selected && 'hover:border-foreground/30 hover:bg-muted/50',
                  selected && v === 'AWKWARD' && 'border-rose-400 bg-rose-50 text-rose-700 ring-2 ring-rose-200',
                  selected && v === 'NATURAL' && 'border-emerald-400 bg-emerald-50 text-emerald-700 ring-2 ring-emerald-200',
                  selected && v === 'UNSURE' && 'border-zinc-400 bg-zinc-100 text-zinc-700 ring-2 ring-zinc-200',
                )}
              >
                {VERDICT_META[v].label}
                <kbd className="rounded border bg-background px-1.5 text-[10px] font-normal text-muted-foreground">{VERDICT_META[v].key}</kbd>
              </button>
            )
          })}
        </div>

        {verdict === 'AWKWARD' && !result && (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">어디가 어색한가요? 숫자키로 여러 개 고를 수 있습니다</p>
            <div className="grid grid-cols-2 gap-1.5">
              {AWKWARD_TYPES.map((t, i) => {
                const on = types.includes(t)
                return (
                  <button
                    key={t}
                    type="button"
                    title={AWKWARD_TYPE_META[t].hint}
                    onClick={() => setTypes((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]))}
                    className={cn(
                      'flex items-center gap-2 rounded-lg border px-2.5 py-1.5 text-left text-xs transition-colors',
                      on ? 'border-rose-300 bg-rose-50 text-rose-700' : 'text-muted-foreground hover:bg-muted/60',
                    )}
                  >
                    <kbd className={cn('rounded px-1 text-[10px] tabular-nums', on ? 'bg-rose-100' : 'bg-muted')}>{i + 1}</kbd>
                    {AWKWARD_TYPE_META[t].label}
                  </button>
                )
              })}
            </div>
          </div>
        )}

        <Textarea value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="메모 (선택) · 기준을 다듬을 때 참고합니다" className="min-h-[3.5rem] resize-none text-sm" maxLength={500} />

        {!result ? (
          <Button className="w-full" onClick={() => void submit()} disabled={!canSubmit}>
            {verdict === 'AWKWARD' && types.length === 0 ? '유형을 하나 이상 고르세요' : '제출하고 AI 판정 보기'}
            <kbd className="ml-1 rounded bg-primary-foreground/20 px-1.5 text-[10px]">Enter</kbd>
          </Button>
        ) : (
          <div
            className={cn(
              'space-y-2 rounded-xl border p-3.5 text-sm',
              ai === null ? 'bg-muted/40' : result.agreesWithAi ? 'border-emerald-200 bg-emerald-50/70' : 'border-amber-300 bg-amber-50',
            )}
          >
            <p className="flex items-center gap-1.5 font-semibold">
              {ai === null ? 'AI가 아직 채점하지 않았습니다' : result.agreesWithAi ? (
                <>
                  <CheckCircle2 className="size-4 text-emerald-600" />
                  AI와 판정이 같습니다
                </>
              ) : (
                <>
                  <AlertTriangle className="size-4 text-amber-600" />
                  AI와 판정이 다릅니다
                </>
              )}
            </p>
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="w-8 text-xs text-muted-foreground">나</span>
              <Badge variant={VERDICT_META[result.myVerdict.verdict].variant}>{VERDICT_META[result.myVerdict.verdict].label}</Badge>
              {result.myVerdict.awkwardTypes.map((t) => (
                <Badge key={t} variant="outline">
                  {AWKWARD_TYPE_META[t].label}
                </Badge>
              ))}
            </div>
            {ai && (
              <>
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="w-8 text-xs text-muted-foreground">AI</span>
                  <Badge variant={VERDICT_META[ai.verdict].variant}>{VERDICT_META[ai.verdict].label}</Badge>
                  {ai.awkwardTypes.map((t) => (
                    <Badge key={t} variant="outline">
                      {AWKWARD_TYPE_META[t].label}
                    </Badge>
                  ))}
                </div>
                {ai.evidence && <p className="text-xs text-muted-foreground">근거 “{ai.evidence}”</p>}
                {ai.reason && <p className="text-xs text-muted-foreground">{ai.reason}</p>}
                {!result.agreesWithAi && <p className="text-xs text-amber-800">다른 판정은 채점 프롬프트를 고칠 때 가장 좋은 재료입니다. 메모를 남겨 두세요.</p>}
              </>
            )}
            <Button size="sm" className="w-full" onClick={() => void next()}>
              다음 응답 <kbd className="ml-1 rounded bg-primary-foreground/20 px-1.5 text-[10px]">J</kbd>
            </Button>
          </div>
        )}

        {error && <p className="text-sm text-destructive">{error}</p>}

        <div className="space-y-2 border-t pt-3 text-xs text-muted-foreground">
          <div className="flex items-center justify-between">
            <button type="button" className="rounded px-1 hover:text-foreground disabled:opacity-40" onClick={() => void previous()} disabled={index <= 0 || loading}>
              ← 이전 <kbd className="ml-0.5 rounded bg-muted px-1 text-[10px]">K</kbd>
            </button>
            <button type="button" className="rounded px-1 hover:text-foreground disabled:opacity-40" onClick={() => void next()} disabled={loading}>
              건너뛰기 <kbd className="ml-0.5 rounded bg-muted px-1 text-[10px]">J</kbd> →
            </button>
          </div>
          <p className="flex items-center gap-1.5 text-[11px]">
            <Keyboard className="size-3.5 shrink-0" />
            Esc 로 판정을 지웁니다. AI 판정은 제출한 뒤에 보입니다.
          </p>
        </div>
      </Card>
    </div>
  )
}
