import { useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import {
  AlertTriangle,
  ChevronDown,
  ChevronRight,
  CircleDollarSign,
  Clock,
  CornerDownRight,
  Cpu,
  Download,
  FlaskConical,
  Play,
  Sigma,
} from 'lucide-react'
import type { PreviewComment, PreviewResult } from '@/types/promptPreview'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageHeader } from '@/components/page-header'
import { cn } from '@/lib/utils'

type Emotion = 'JOY' | 'WARM' | 'ANGER' | 'ANXIETY' | 'GRUMPY' | 'QUIRKY'

const CHARACTERS: { value: Emotion; label: string; emoji: string }[] = [
  { value: 'JOY', label: '기쁨', emoji: '😊' },
  { value: 'WARM', label: '다정', emoji: '🥰' },
  { value: 'ANGER', label: '분노', emoji: '😡' },
  { value: 'ANXIETY', label: '불안', emoji: '😰' },
  { value: 'GRUMPY', label: '까칠', emoji: '😤' },
  { value: 'QUIRKY', label: '엉뚱', emoji: '🤪' },
]

const characterOf = (value: string) => CHARACTERS.find((c) => c.value === value)

/** 접이식 프롬프트 오버라이드 에디터. 비워두면 저장된 현재값으로 생성된다. */
function PromptOverrideEditor({
  type,
  title,
  value,
  onChange,
}: {
  type: 'COMMON' | 'COMMENT'
  title: string
  value: string
  onChange: (value: string) => void
}) {
  const [open, setOpen] = useState(false)
  const { refetch } = useCustom<{ systemPrompt: string }>({
    url: `/api/admin/llm-settings/prompt?promptType=${type}`,
    method: 'get',
    queryOptions: { enabled: false },
  })

  const loadSaved = async () => {
    const result = await refetch()
    const saved = result.data?.data?.systemPrompt
    if (saved !== undefined) {
      onChange(saved)
    }
  }

  return (
    <div className="rounded-md border">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center gap-2 px-3 py-2 text-sm hover:bg-muted/40"
      >
        {open ? <ChevronDown className="size-4 text-muted-foreground" /> : <ChevronRight className="size-4 text-muted-foreground" />}
        <span className="font-medium">{title}</span>
        <Badge variant={value.trim() ? 'default' : 'secondary'} className="ml-auto text-[10px]">
          {value.trim() ? '오버라이드' : '저장값 사용'}
        </Badge>
      </button>
      {open && (
        <div className="space-y-2 border-t p-3">
          <Textarea
            value={value}
            onChange={(e) => onChange(e.target.value)}
            spellCheck={false}
            placeholder="비워두면 저장된 현재값으로 생성합니다."
            className="min-h-[12rem] font-mono text-[12px] leading-relaxed"
          />
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={loadSaved}>
              <Download className="size-4" />
              저장값 불러와서 수정
            </Button>
            {value.trim() && (
              <Button variant="outline" size="sm" onClick={() => onChange('')}>
                비우기(저장값 사용)
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function MetricTile({ icon: Icon, label, value, sub }: { icon: typeof Cpu; label: string; value: string; sub?: string }) {
  return (
    <Card className="p-4">
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <Icon className="size-3.5" />
        {label}
      </div>
      <p className="mt-1 truncate text-lg font-semibold tabular-nums tracking-tight">{value}</p>
      {sub && <p className="text-[11px] text-muted-foreground">{sub}</p>}
    </Card>
  )
}

function CharacterChip({ value }: { value: string }) {
  const character = characterOf(value)
  return (
    <Badge variant="outline" className="gap-1 whitespace-nowrap">
      <span>{character?.emoji}</span>
      {character?.label ?? value}
    </Badge>
  )
}

function FeedBubble({ comment }: { comment: PreviewComment & { replyTo?: string } }) {
  const character = characterOf(comment.characterId)
  return (
    <div className="flex gap-2.5">
      <div className="flex size-8 shrink-0 items-center justify-center rounded-full border bg-background text-base">
        {character?.emoji ?? '🙂'}
      </div>
      <div className="min-w-0 space-y-1">
        <div className="flex items-center gap-1.5 text-xs">
          <span className="font-medium">{character?.label ?? comment.characterId}</span>
          {comment.replyTo && (
            <span className="flex items-center gap-0.5 text-muted-foreground">
              <CornerDownRight className="size-3" />
              {characterOf(comment.replyTo)?.label ?? comment.replyTo}에게
            </span>
          )}
        </div>
        <p className="w-fit whitespace-pre-wrap break-keep rounded-2xl rounded-tl-sm border bg-muted/40 px-3.5 py-2 text-sm leading-relaxed">
          {comment.text}
        </p>
      </div>
    </div>
  )
}

/** 전달된 프롬프트 전문(시스템·유저) 접이식 뷰어. */
function PromptInspector({ result }: { result: PreviewResult }) {
  const [open, setOpen] = useState(false)
  return (
    <Card className="overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center gap-2 px-4 py-3 text-sm hover:bg-muted/40"
      >
        {open ? <ChevronDown className="size-4 text-muted-foreground" /> : <ChevronRight className="size-4 text-muted-foreground" />}
        <span className="font-medium">LLM에 실제로 전달된 내용</span>
        <span className="ml-auto text-xs text-muted-foreground">시스템 프롬프트 + user content</span>
      </button>
      {open && (
        <div className="space-y-3 border-t p-4">
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted-foreground">시스템 프롬프트 (공통 + 댓글 조립본)</p>
            <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-md border bg-muted/20 p-3 font-mono text-[12px] leading-relaxed">
              {result.systemPrompt}
            </pre>
          </div>
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted-foreground">user content</p>
            <pre className="max-h-56 overflow-auto whitespace-pre-wrap rounded-md border bg-muted/20 p-3 font-mono text-[12px] leading-relaxed">
              {result.userContent}
            </pre>
          </div>
        </div>
      )}
    </Card>
  )
}

export function PromptPlaygroundPage() {
  const [diary, setDiary] = useState('')
  const [summary, setSummary] = useState('')
  const [commonPrompt, setCommonPrompt] = useState('')
  const [commentPrompt, setCommentPrompt] = useState('')
  const [selected, setSelected] = useState<Emotion[]>([])
  const [tikitaka, setTikitaka] = useState(0)
  const [result, setResult] = useState<PreviewResult | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)

  const { mutate: run, isLoading: running } = useCustomMutation()

  const toggleCharacter = (value: Emotion) => {
    setSelected((prev) => {
      const next = prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]
      if (next.length < 2) {
        setTikitaka(0)
      }
      return next
    })
  }

  const canRun = diary.trim().length > 0 && !running && selected.length > 0

  const onRun = () => {
    if (!canRun) {
      return
    }
    setRequestError(null)
    run(
      {
        url: '/api/admin/llm-settings/prompt/preview',
        method: 'post',
        values: {
          diaryContent: diary,
          currentConversationSummary: summary.trim() || null,
          commonPrompt: commonPrompt.trim() ? commonPrompt : null,
          commentPrompt: commentPrompt.trim() ? commentPrompt : null,
          characters: selected,
          tikitakaCount: tikitaka,
        },
      },
      {
        onSuccess: (response) => setResult(response.data as unknown as PreviewResult),
        onError: () => setRequestError('미리보기 요청에 실패했습니다. 입력을 확인하고 다시 시도해주세요.'),
      },
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="프롬프트 실험실"
        description="저장하기 전에 프롬프트를 실제 LLM으로 시험합니다. 프롬프트도 생성 로그도 저장되지 않으며, 호출마다 소액의 비용이 발생합니다."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        {/* 실험 조건 */}
        <div className="space-y-4">
          <Card className="space-y-4 p-5">
            <div>
              <label htmlFor="diary" className="mb-1.5 block text-sm font-medium">
                샘플 일기 <span className="text-destructive">*</span>
              </label>
              <Textarea
                id="diary"
                value={diary}
                onChange={(e) => setDiary(e.target.value)}
                placeholder="예) 오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다"
                className="min-h-[7rem]"
              />
            </div>
            <div>
              <label htmlFor="summary" className="mb-1.5 block text-sm font-medium">
                채팅방 임시 요약 <span className="font-normal text-muted-foreground">(선택)</span>
              </label>
              <Input
                id="summary"
                value={summary}
                onChange={(e) => setSummary(e.target.value)}
                placeholder="이 채팅방에서 오간 대화의 요약이 있다면"
              />
            </div>

            <div className="space-y-2.5">
              <span className="text-sm font-medium">
                등장 캐릭터 <span className="text-destructive">*</span>
              </span>
              <div className="flex flex-wrap gap-1.5">
                {CHARACTERS.map((character) => (
                  <button
                    key={character.value}
                    type="button"
                    onClick={() => toggleCharacter(character.value)}
                    className={cn(
                      'flex items-center gap-1 rounded-full border px-3 py-1.5 text-sm transition-colors',
                      selected.includes(character.value)
                        ? 'border-foreground/30 bg-secondary font-medium'
                        : 'text-muted-foreground hover:bg-muted/40',
                    )}
                  >
                    <span>{character.emoji}</span>
                    {character.label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-2">
                  <span className="text-xs text-muted-foreground">티키타카</span>
                  <div className="w-24">
                    <Select
                      value={String(tikitaka)}
                      onChange={(e) => setTikitaka(Number(e.target.value))}
                      disabled={selected.length < 2}
                    >
                      <option value="0">0개</option>
                      <option value="1">1개</option>
                      <option value="2">2개</option>
                    </Select>
                  </div>
                  {selected.length < 2 && <span className="text-[11px] text-muted-foreground">캐릭터 2명 이상일 때 지정 가능</span>}
              </div>
            </div>
          </Card>

          <div className="space-y-2">
            <PromptOverrideEditor type="COMMON" title="공통 프롬프트 오버라이드" value={commonPrompt} onChange={setCommonPrompt} />
            <PromptOverrideEditor type="COMMENT" title="댓글 프롬프트 오버라이드" value={commentPrompt} onChange={setCommentPrompt} />
          </div>

          <div className="flex items-center gap-3">
            <Button onClick={onRun} disabled={!canRun}>
              <Play className="size-4" />
              {running ? '생성 중…' : '생성해보기'}
            </Button>
            <p className="text-xs text-muted-foreground">실제 LLM을 호출합니다. 결과는 저장되지 않습니다.</p>
          </div>
          {requestError && <p className="text-sm text-destructive">{requestError}</p>}
        </div>

        {/* 결과 */}
        <div className="space-y-4">
          {running ? (
            <Card className="space-y-3 p-5">
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-4/5" />
            </Card>
          ) : !result ? (
            <Card className="flex flex-col items-center justify-center gap-3 p-16 text-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-muted">
                <FlaskConical className="size-6 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-medium">아직 실행한 실험이 없습니다</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  왼쪽에서 샘플 일기를 입력하고 생성해보세요. 여러 안을 바꿔가며 비교할 수 있습니다.
                </p>
              </div>
            </Card>
          ) : (
            <>
              {result.generationError && (
                <div className="flex items-start gap-2.5 rounded-lg border border-destructive/30 bg-destructive/5 p-3.5 text-sm">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
                  <div>
                    <p className="font-medium text-destructive">생성 실패</p>
                    <p className="mt-0.5 break-keep text-muted-foreground">{result.generationError}</p>
                  </div>
                </div>
              )}
              {result.validationError && (
                <div className="flex items-start gap-2.5 rounded-lg border border-amber-500/40 bg-amber-500/5 p-3.5 text-sm">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                  <div>
                    <p className="font-medium text-amber-700">출력 계약 위반</p>
                    <p className="mt-0.5 break-keep text-muted-foreground">
                      {result.validationError} - 실제 생성이라면 재시도됐을 응답입니다.
                    </p>
                  </div>
                </div>
              )}

              <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
                <MetricTile icon={Cpu} label="모델" value={result.model.replace('gemini-', '')} />
                <MetricTile icon={Clock} label="지연" value={`${(result.latencyMs / 1000).toFixed(1)}s`} />
                <MetricTile
                  icon={Sigma}
                  label="토큰"
                  value={result.usedTokens.toLocaleString()}
                  sub={`입력 ${result.inputTokens.toLocaleString()} · 캐시 ${result.cachedTokens.toLocaleString()} · 출력 ${result.outputTokens.toLocaleString()}`}
                />
                <MetricTile icon={CircleDollarSign} label="예상 비용" value={`$${result.estimatedCostUsd.toFixed(5)}`} />
              </div>

              <Card className="space-y-4 p-5">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="mr-1 text-xs font-medium text-muted-foreground">이번 조건</span>
                  {result.characters.map((character) => (
                    <CharacterChip key={character} value={character} />
                  ))}
                  <Badge variant="secondary" className="whitespace-nowrap text-[10px]">
                    티키타카 {result.tikitakaCount}
                  </Badge>
                  {result.eongttungTopic && (
                    <Badge variant="secondary" className="whitespace-nowrap text-[10px]">
                      엉뚱 소재: {result.eongttungTopic}
                    </Badge>
                  )}
                </div>

                {result.comments ? (
                  <div className="space-y-3.5">
                    {result.comments.map((comment, i) => (
                      <FeedBubble key={`c-${i}`} comment={comment} />
                    ))}
                    {result.tikitaka?.map((t, i) => (
                      <FeedBubble key={`t-${i}`} comment={t} />
                    ))}
                  </div>
                ) : (
                  <p className="py-6 text-center text-sm text-muted-foreground">생성 실패로 표시할 피드가 없습니다.</p>
                )}
              </Card>

              <PromptInspector result={result} />
            </>
          )}
        </div>
      </div>
    </div>
  )
}
