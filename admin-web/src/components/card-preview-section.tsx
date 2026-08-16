import { useState } from 'react'
import { useCustomMutation } from '@refinedev/core'
import { AlertTriangle, CircleDollarSign, Clock, Cpu, Play, Ruler, Scissors, Sparkles } from 'lucide-react'
import type { CardPreviewResult } from '@/types/promptPreview'
import { ApiError } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Textarea } from '@/components/ui/textarea'
import { MetricTile } from '@/components/playground/metric-tile'
import { PromptInspector } from '@/components/playground/prompt-inspector'
import { PromptOverrideEditor } from '@/components/playground/prompt-override-editor'
import { cn } from '@/lib/utils'

/** 서버의 EmotionType과 같은 목록. 위쪽 실험의 캐릭터 칩과 같은 이모지를 쓴다. */
const EMOTIONS: { value: string; label: string; emoji: string }[] = [
  { value: 'JOY', label: '기쁨', emoji: '😊' },
  { value: 'SADNESS', label: '슬픔', emoji: '😢' },
  { value: 'ANGER', label: '분노', emoji: '😡' },
  { value: 'ANXIETY', label: '불안', emoji: '😰' },
  { value: 'GRUMPY', label: '까칠', emoji: '😤' },
  { value: 'QUIRKY', label: '엉뚱', emoji: '🤪' },
]

/** 서버 CardSummary.MAX_LENGTH와 같은 값. 넘으면 서버가 어절 경계에서 자른다. */
const MAX_LENGTH = 50

/** 위쪽 실험의 샘플 일기와 같은 상한(서버 검증과 일치). */
const SUMMARY_MAX_LENGTH = 2000

function errorMessageOf(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * 카드 한 줄 미리보기. 댓글·답글과 달리 대화 세션이 없어(입력은 대표 감정과 요약뿐) 별도 실험으로 둔다.
 *
 * 레이아웃·라벨·글자수 표시는 위쪽 실험(PromptPlaygroundPage)과 같은 규칙을 따른다 — 한 화면 안에서
 * 규칙이 갈리면 사용자는 두 도구를 쓰는 것처럼 느낀다. 공통 프롬프트 오버라이드가 없는 것은 카드
 * 프롬프트가 조립되지 않기 때문이다(서버도 CARD를 단독으로 쓴다).
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
    <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      {/* 실험 조건 */}
      <div className="space-y-4">
        <Card className="space-y-4 p-5">
          <div>
            <label htmlFor="card-preview-summary" className="mb-1.5 block text-sm font-medium">
              대화 요약 <span className="text-destructive">*</span>{' '}
              <span className="font-normal text-muted-foreground">(클라이언트가 보내는 값)</span>
            </label>
            <Textarea
              id="card-preview-summary"
              value={summary}
              onChange={(e) => setSummary(e.target.value)}
              maxLength={SUMMARY_MAX_LENGTH}
              placeholder="예) 오늘 팀장이 자기 할 일을 다 떠넘김. 야근함."
              className="min-h-[7rem]"
            />
            <p className="mt-1 text-right text-[11px] tabular-nums text-muted-foreground">
              {summary.length.toLocaleString()} / {SUMMARY_MAX_LENGTH.toLocaleString()}
            </p>
          </div>

          <div className="space-y-2.5">
            <span className="text-sm font-medium">
              대표 감정 <span className="text-destructive">*</span>
            </span>
            <div className="flex flex-wrap gap-1.5">
              {EMOTIONS.map((item) => (
                <button
                  key={item.value}
                  type="button"
                  onClick={() => setEmotion(item.value)}
                  className={cn(
                    'flex items-center gap-1 rounded-full border px-3 py-1.5 text-sm transition-colors',
                    emotion === item.value
                      ? 'border-foreground/30 bg-secondary font-medium'
                      : 'text-muted-foreground hover:bg-muted/40',
                  )}
                >
                  <span>{item.emoji}</span>
                  {item.label}
                </button>
              ))}
            </div>
            <p className="text-[11px] text-muted-foreground">
              실제 서비스는 카드의 대표 감정을 씁니다. 감정은 말투가 아니라 어떤 사건을 고를지의 기준입니다.
            </p>
          </div>
        </Card>

        <PromptOverrideEditor type="CARD" title="카드 프롬프트 오버라이드" value={cardPrompt} onChange={setCardPrompt} />

        <div className="flex items-center gap-3">
          <Button onClick={preview} disabled={!canRun}>
            <Play className="size-4" />
            {running ? '생성 중…' : result ? '다시 생성해보기' : '생성해보기'}
          </Button>
          <p className="text-xs text-muted-foreground">
            카드 프롬프트는 공통 프롬프트와 조립되지 않아 단독으로 시험합니다.
          </p>
        </div>
        {requestError && <p className="text-sm text-destructive">{requestError}</p>}

        {/* 실패했을 때도 보여준다 — 오버라이드가 잘못돼 실패한 경우 무엇을 보냈는지가 바로 단서다. */}
        {result && (
          <PromptInspector
            systemPrompt={result.systemPrompt}
            userContent={result.userContent}
            systemPromptLabel="시스템 프롬프트 (카드 단독)"
          />
        )}
      </div>

      {/* 결과 */}
      <div className="space-y-4">
        {!result ? (
          <Card className="flex flex-col items-center justify-center gap-3 p-16 text-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-muted">
              <Sparkles className="size-6 text-muted-foreground" />
            </div>
            <div>
              <p className="text-sm font-medium">아직 만들어본 카드가 없습니다</p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                왼쪽에 대화 요약을 입력하고 생성해보세요. 서버가 요약을 {MAX_LENGTH}자 이내 한 줄로 다듬습니다.
              </p>
            </div>
          </Card>
        ) : (
          <>
            {/* 지표는 성공·실패와 무관하게 보여준다. 서버는 파싱이 실패해도 이미 과금된 토큰을 실어
                보내주는데(GeminiCardMessageGenerator 가 파싱 전에 뽑아둔다), 화면에서 가리면
                원인을 좁히려고 한 번 더 호출하게 된다. 지연 단위는 위쪽 실험과 같은 초 단위다. */}
            <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
              <MetricTile icon={Cpu} label="모델" value={result.model.replace('gemini-', '')} />
              <MetricTile icon={Clock} label="지연" value={`${(result.latencyMs / 1000).toFixed(1)}s`} />
              <MetricTile icon={Ruler} label="토큰" value={result.usedTokens.toLocaleString()} />
              <MetricTile icon={CircleDollarSign} label="비용" value={`$${result.estimatedCostUsd.toFixed(5)}`} />
            </div>

            {result.generationError && (
              <div className="flex items-start gap-2.5 rounded-lg border border-destructive/30 bg-destructive/5 p-3.5 text-sm">
                <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
                <div>
                  <p className="font-medium text-destructive">생성 실패</p>
                  <p className="mt-0.5 break-keep text-muted-foreground">{result.generationError}</p>
                </div>
              </div>
            )}

            {/* 한 줄은 실패 시 null 이라 성공했을 때만 그린다. */}
            {!result.generationError && (
              <Card className="space-y-3 p-5">
                <div className="flex items-center gap-2">
                  <p className="text-xs font-medium text-muted-foreground">카드에 남을 한 줄</p>
                  {result.truncated && (
                    <Badge variant="destructive" className="gap-1 text-[10px]">
                      <Scissors className="size-3" />
                      서버가 자름
                    </Badge>
                  )}
                </div>
                <p className="break-keep text-lg font-medium leading-relaxed">{result.line}</p>
                {/* line은 서버가 이미 자른 값이라 상한을 넘지 않는다 — 넘긴 쪽은 원문이다. */}
                <p className={cn('text-[11px] tabular-nums text-muted-foreground', result.truncated && 'text-destructive')}>
                  {result.length}자 / {MAX_LENGTH}자
                </p>
              </Card>
            )}

            {result.truncated && (
              <Card className="space-y-1.5 border-dashed p-4">
                <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                  <Ruler className="size-3.5" />
                  자르기 전 원문 ({result.rawLength}자)
                </p>
                <p className="break-keep text-sm">{result.rawLine}</p>
                <p className="text-[11px] text-muted-foreground">
                  프롬프트가 길이 지시를 지키지 못했습니다. 서버 자르기에 기대는 만큼 문장이 어색해질 수 있습니다.
                </p>
              </Card>
            )}

          </>
        )}
      </div>
    </div>
  )
}
