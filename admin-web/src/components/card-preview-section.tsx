import { useState } from 'react'
import { useCustomMutation } from '@refinedev/core'
import { AlertTriangle, CircleDollarSign, Clock, Cpu, Play, Ruler, Scissors } from 'lucide-react'
import type { CardPreviewResult } from '@/types/promptPreview'
import { ApiError } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'

/** 서버의 EmotionType과 같은 목록. */
const EMOTIONS: { value: string; label: string }[] = [
  { value: 'JOY', label: '기쁨' },
  { value: 'SADNESS', label: '슬픔' },
  { value: 'ANGER', label: '분노' },
  { value: 'ANXIETY', label: '불안' },
  { value: 'GRUMPY', label: '까칠' },
  { value: 'QUIRKY', label: '엉뚱' },
]

/** 서버 CardSummary.MAX_LENGTH와 같은 값. 넘으면 서버가 어절 경계에서 자른다. */
const MAX_LENGTH = 50

function errorMessageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function Metric({ icon: Icon, label, value }: { icon: typeof Cpu; label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-muted/30 p-2.5">
      <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
        <Icon className="size-3.5" />
        {label}
      </p>
      <p className="mt-1 truncate text-sm font-semibold tabular-nums">{value}</p>
    </div>
  )
}

/**
 * 카드 한 줄 미리보기. 댓글·답글과 달리 대화 세션이 없어(입력은 대표 감정과 요약뿐) 별도 섹션으로 둔다.
 *
 * 공통 프롬프트 오버라이드가 없는 것은 카드 프롬프트가 조립되지 않기 때문이다 — 서버도 CARD를
 * 단독으로 쓴다.
 */
export function CardPreviewSection() {
  const [emotion, setEmotion] = useState('ANGER')
  const [summary, setSummary] = useState('')
  const [cardPrompt, setCardPrompt] = useState('')
  const [result, setResult] = useState<CardPreviewResult | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)
  const { mutate: run, isLoading: running } = useCustomMutation()

  const canRun = summary.trim().length > 0 && !running

  const preview = () => {
    if (!canRun) {
      return
    }
    setRequestError(null)
    run(
      {
        url: '/api/admin/llm-settings/prompt/preview/card',
        method: 'post',
        values: {
          cardPrompt: cardPrompt.trim() ? cardPrompt : null,
          emotion,
          summary,
        },
      },
      {
        onSuccess: (response) => setResult(response.data as unknown as CardPreviewResult),
        onError: (error) =>
          setRequestError(errorMessageOf(error, '카드 미리보기 요청에 실패했습니다. 입력을 확인하고 다시 시도해주세요.')),
      },
    )
  }

  return (
    <Card className="space-y-4 p-4">
      <div>
        <h2 className="text-sm font-semibold">카드 한 줄 미리보기</h2>
        <p className="mt-1 text-xs text-muted-foreground">
          대화 요약을 다듬어 카드에 남을 한 줄을 만듭니다. 카드 프롬프트는 공통 프롬프트와 조립되지 않아 단독으로
          시험합니다.
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
        <div>
          <label className="text-xs font-medium text-muted-foreground" htmlFor="card-preview-emotion">
            대표 감정
          </label>
          <Select
            id="card-preview-emotion"
            className="mt-1 w-full"
            value={emotion}
            onChange={(event) => setEmotion(event.target.value)}
          >
            {EMOTIONS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </Select>
        </div>
        <div>
          <label className="text-xs font-medium text-muted-foreground" htmlFor="card-preview-summary">
            대화 요약 (클라이언트가 보내는 값)
          </label>
          <Textarea
            id="card-preview-summary"
            className="mt-1 min-h-20"
            value={summary}
            maxLength={2000}
            onChange={(event) => setSummary(event.target.value)}
            placeholder="오늘 팀장이 자기 할 일을 다 떠넘김. 야근함."
          />
        </div>
      </div>

      <div>
        <label className="text-xs font-medium text-muted-foreground" htmlFor="card-preview-prompt">
          카드 프롬프트 오버라이드 (비우면 저장된 현재값)
        </label>
        <Textarea
          id="card-preview-prompt"
          className="mt-1 min-h-24 font-mono text-xs"
          value={cardPrompt}
          maxLength={20000}
          onChange={(event) => setCardPrompt(event.target.value)}
          placeholder="비워두면 백오피스에 저장된 카드 프롬프트를 그대로 씁니다."
        />
      </div>

      <Button onClick={preview} disabled={!canRun}>
        <Play className="size-4" />
        {running ? '생성 중…' : '생성'}
      </Button>

      {requestError && (
        <p className="flex items-center gap-1.5 text-xs text-destructive">
          <AlertTriangle className="size-3.5 shrink-0" />
          {requestError}
        </p>
      )}

      {result && (
        <div className="space-y-3 border-t pt-4">
          {result.generationError ? (
            <p className="flex items-center gap-1.5 text-xs text-destructive">
              <AlertTriangle className="size-3.5 shrink-0" />
              생성 실패: {result.generationError}
            </p>
          ) : (
            <>
              <div className="rounded-lg border bg-muted/30 p-3">
                <p className="text-base font-medium">{result.line}</p>
                <p className="mt-2 flex items-center gap-2 text-[11px] text-muted-foreground">
                  <span className={cn('tabular-nums', (result.length ?? 0) > MAX_LENGTH && 'text-destructive')}>
                    {result.length}자 / {MAX_LENGTH}자
                  </span>
                  {result.truncated && (
                    <Badge variant="destructive" className="gap-1">
                      <Scissors className="size-3" />
                      서버가 자름
                    </Badge>
                  )}
                </p>
              </div>

              {result.truncated && (
                <div className="rounded-lg border border-dashed p-3">
                  <p className="flex items-center gap-1.5 text-[11px] text-muted-foreground">
                    <Ruler className="size-3.5" />
                    자르기 전 원문 ({result.rawLength}자) — 프롬프트가 길이 지시를 지키지 못했습니다
                  </p>
                  <p className="mt-1 text-xs">{result.rawLine}</p>
                </div>
              )}
            </>
          )}

          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            <Metric icon={Cpu} label="모델" value={result.model.replace('gemini-', '')} />
            <Metric icon={Clock} label="지연" value={`${result.latencyMs}ms`} />
            <Metric icon={Ruler} label="토큰" value={`${result.usedTokens}`} />
            <Metric icon={CircleDollarSign} label="비용" value={`$${result.estimatedCostUsd.toFixed(5)}`} />
          </div>

          <details className="rounded-lg border p-3">
            <summary className="cursor-pointer text-xs font-medium">실제 전달된 프롬프트 보기</summary>
            <div className="mt-2 space-y-2">
              <div>
                <p className="text-[11px] text-muted-foreground">시스템 프롬프트 (카드 단독)</p>
                <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded bg-muted/40 p-2 font-mono text-[11px]">
                  {result.systemPrompt}
                </pre>
              </div>
              <div>
                <p className="text-[11px] text-muted-foreground">user content</p>
                <pre className="mt-1 overflow-auto whitespace-pre-wrap rounded bg-muted/40 p-2 font-mono text-[11px]">
                  {result.userContent}
                </pre>
              </div>
            </div>
          </details>
        </div>
      )}
    </Card>
  )
}
