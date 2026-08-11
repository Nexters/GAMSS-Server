import { useRef, useState } from 'react'
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
  Reply,
  Send,
  Sigma,
  X,
} from 'lucide-react'
import type { PreviewResult, ReplyPreviewResult } from '@/types/promptPreview'
import { ApiError } from '@/lib/api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
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
const labelOf = (value: string) => characterOf(value)?.label ?? value

// 서버 DTO의 @Size 제한과 같은 값. 서버가 2000자, 요약 근사는 여유를 둔 1800자 예산을 쓴다.
const DIARY_MAX_LENGTH = 2000
const SUMMARY_MAX_LENGTH = 1800

// 400(INVALID_INPUT)은 순수한 입력 오류라 서버가 내려준 사유를 그대로 보여준다.
const errorMessageOf = (error: unknown, fallback: string) =>
  error instanceof ApiError && error.detail ? error.detail : fallback

/** 세션에 쌓이는 말풍선 하나. 유저 메시지 또는 캐릭터 메시지. */
interface SessionItem {
  id: number
  kind: 'user' | 'character'
  text: string
  characterId?: string
  /** 캐릭터 티키타카·재응답의 답장 대상, 또는 유저 답장의 대상 캐릭터. */
  replyTo?: string
}

interface LastMeta {
  model: string
  latencyMs: number
  usedTokens: number
  cachedTokens: number
  inputTokens: number
  outputTokens: number
  estimatedCostUsd: number
}

interface LastRun {
  meta: LastMeta
  systemPrompt: string
  userContent: string
  validationError: string | null
  generationError: string | null
  conditions: { characters: string[]; tikitakaCount: number; eongttungTopic: string | null } | null
}

/** 접이식 프롬프트 오버라이드 에디터. 비워두면 저장된 현재값으로 생성된다. */
function PromptOverrideEditor({
  type,
  title,
  value,
  onChange,
}: {
  type: 'COMMON' | 'COMMENT' | 'REPLY'
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

/** 세션 말풍선. 유저는 오른쪽, 캐릭터는 왼쪽 정렬. 캐릭터 말풍선에는 답장 버튼이 뜬다. */
function SessionBubble({ item, onReply }: { item: SessionItem; onReply: (item: SessionItem) => void }) {
  if (item.kind === 'user') {
    return (
      <div className="flex justify-end">
        <div className="min-w-0 max-w-[85%] space-y-1 text-right">
          <div className="flex items-center justify-end gap-1.5 text-xs">
            <span className="font-medium">유저</span>
            {item.replyTo && (
              <span className="flex items-center gap-0.5 text-muted-foreground">
                <CornerDownRight className="size-3" />
                {labelOf(item.replyTo)}에게
              </span>
            )}
          </div>
          <p className="inline-block whitespace-pre-wrap break-keep rounded-2xl rounded-tr-sm bg-primary px-3.5 py-2 text-left text-sm leading-relaxed text-primary-foreground">
            {item.text}
          </p>
        </div>
      </div>
    )
  }
  const character = characterOf(item.characterId ?? '')
  return (
    <div className="group flex gap-2.5">
      <div className="flex size-8 shrink-0 items-center justify-center rounded-full border bg-background text-base">
        {character?.emoji ?? '🙂'}
      </div>
      <div className="min-w-0 max-w-[85%] space-y-1">
        <div className="flex items-center gap-1.5 text-xs">
          <span className="font-medium">{character?.label ?? item.characterId}</span>
          {item.replyTo && (
            <span className="flex items-center gap-0.5 text-muted-foreground">
              <CornerDownRight className="size-3" />
              {labelOf(item.replyTo)}에게
            </span>
          )}
          <button
            type="button"
            onClick={() => onReply(item)}
            aria-label={`${character?.label ?? item.characterId}에게 답장`}
            className="flex items-center gap-0.5 rounded px-1 py-0.5 text-[11px] text-muted-foreground opacity-0 transition-opacity hover:bg-muted hover:text-foreground focus-visible:opacity-100 group-hover:opacity-100"
          >
            <Reply className="size-3" />
            답장
          </button>
        </div>
        <p className="w-fit whitespace-pre-wrap break-keep rounded-2xl rounded-tl-sm border bg-muted/40 px-3.5 py-2 text-sm leading-relaxed">
          {item.text}
        </p>
      </div>
    </div>
  )
}

/** 마지막 호출에서 전달된 프롬프트 전문 접이식 뷰어. */
function PromptInspector({ systemPrompt, userContent }: { systemPrompt: string; userContent: string }) {
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
        <span className="ml-auto text-xs text-muted-foreground">마지막 호출 기준</span>
      </button>
      {open && (
        <div className="space-y-3 border-t p-4">
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted-foreground">시스템 프롬프트 (조립본)</p>
            <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-md border bg-muted/20 p-3 font-mono text-[12px] leading-relaxed">
              {systemPrompt}
            </pre>
          </div>
          <div>
            <p className="mb-1.5 text-xs font-medium text-muted-foreground">user content</p>
            <pre className="max-h-56 overflow-auto whitespace-pre-wrap rounded-md border bg-muted/20 p-3 font-mono text-[12px] leading-relaxed">
              {userContent}
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
  const [replyPrompt, setReplyPrompt] = useState('')
  const [selected, setSelected] = useState<Emotion[]>([])
  const [tikitaka, setTikitaka] = useState(0)

  const [session, setSession] = useState<SessionItem[]>([])
  const [firstDiary, setFirstDiary] = useState('')
  const [sessionCost, setSessionCost] = useState(0)
  const [lastRun, setLastRun] = useState<LastRun | null>(null)
  const [composer, setComposer] = useState('')
  const [replyTarget, setReplyTarget] = useState<SessionItem | null>(null)
  const [requestError, setRequestError] = useState<string | null>(null)

  const { mutate: run, isLoading: running } = useCustomMutation()

  // 말풍선 id는 단조 증가 카운터로 발급한다 - 세션 길이 기반 오프셋은 길어지면 충돌한다.
  const idRef = useRef(0)
  const nextId = () => {
    idRef.current += 1
    return idRef.current
  }

  // updater는 순수해야 하므로(StrictMode 이중 호출) setTikitaka는 updater 밖에서 부른다.
  const toggleCharacter = (value: Emotion) => {
    const next = selected.includes(value) ? selected.filter((v) => v !== value) : [...selected, value]
    setSelected(next)
    if (next.length < 2) {
      setTikitaka(0)
    }
  }

  const canStart = diary.trim().length > 0 && !running && selected.length > 0

  const feedToItems = (result: PreviewResult): SessionItem[] => [
    ...(result.comments ?? []).map((c) => ({ id: nextId(), kind: 'character' as const, text: c.text, characterId: c.characterId })),
    ...(result.tikitaka ?? []).map((t) => ({
      id: nextId(),
      kind: 'character' as const,
      text: t.text,
      characterId: t.characterId,
      replyTo: t.replyTo,
    })),
  ]

  const metaOf = (r: PreviewResult | ReplyPreviewResult): LastMeta => ({
    model: r.model,
    latencyMs: r.latencyMs,
    usedTokens: r.usedTokens,
    cachedTokens: r.cachedTokens,
    inputTokens: r.inputTokens,
    outputTokens: r.outputTokens,
    estimatedCostUsd: r.estimatedCostUsd,
  })

  // 프로덕션에서 클라이언트가 보내는 '채팅방 임시 요약'을 세션 대화 내용으로 근사한다.
  // 문자 단위로 자르면 첫 줄의 화자 라벨이 깨진 파편으로 시작하므로, 최근 대화부터 줄 단위로
  // 담고 관리자가 지정한 요약은 항상 보존한다.
  const transcriptSummary = () => {
    const base = summary.trim()
    const lines = session.map((item) => `${item.kind === 'user' ? '유저' : labelOf(item.characterId ?? '')}: ${item.text}`)
    let budget = SUMMARY_MAX_LENGTH - (base ? base.length + 3 : 0)
    const kept: string[] = []
    for (let i = lines.length - 1; i >= 0; i -= 1) {
      const cost = lines[i].length + (kept.length > 0 ? 3 : 0)
      if (cost > budget) {
        break
      }
      budget -= cost
      kept.unshift(lines[i])
    }
    return [base, ...kept].filter(Boolean).join(' / ')
  }

  /**
   * 댓글 피드 미리보기 한 번. 세션 시작(교체)과 이어보내기(추가)가 같은 요청 조건을 쓰도록
   * 호출 경로를 하나로 모은다 - 두 경로의 조건이 갈라지면 "같은 조건 비교"라는 목적이 깨진다.
   */
  const runFeedPreview = ({
    diaryContent,
    conversationSummary,
    replaceSession,
  }: {
    diaryContent: string
    conversationSummary: string | null
    replaceSession: boolean
  }) => {
    setRequestError(null)
    run(
      {
        url: '/api/admin/llm-settings/prompt/preview',
        method: 'post',
        values: {
          diaryContent,
          currentConversationSummary: conversationSummary,
          commonPrompt: commonPrompt.trim() ? commonPrompt : null,
          commentPrompt: commentPrompt.trim() ? commentPrompt : null,
          characters: selected,
          tikitakaCount: tikitaka,
        },
      },
      {
        onSuccess: (response) => {
          const result = response.data as unknown as PreviewResult
          const newItems: SessionItem[] = [{ id: nextId(), kind: 'user', text: diaryContent }, ...feedToItems(result)]
          if (replaceSession) {
            setFirstDiary(diaryContent)
            setSession(newItems)
            setSessionCost(result.estimatedCostUsd)
            setReplyTarget(null)
          }
          if (!replaceSession) {
            setSession((prev) => [...prev, ...newItems])
            setSessionCost((cost) => cost + result.estimatedCostUsd)
          }
          setLastRun({
            meta: metaOf(result),
            systemPrompt: result.systemPrompt,
            userContent: result.userContent,
            validationError: result.validationError,
            generationError: result.generationError,
            conditions: { characters: result.characters, tikitakaCount: result.tikitakaCount, eongttungTopic: result.eongttungTopic },
          })
          setComposer('')
        },
        onError: (error) =>
          setRequestError(errorMessageOf(error, '미리보기 요청에 실패했습니다. 입력을 확인하고 다시 시도해주세요.')),
      },
    )
  }

  const startSession = () => {
    if (!canStart) {
      return
    }
    runFeedPreview({ diaryContent: diary, conversationSummary: summary.trim() || null, replaceSession: true })
  }

  const sendComposer = () => {
    const text = composer.trim()
    if (!text || running) {
      return
    }
    setRequestError(null)
    if (replyTarget) {
      run(
        {
          url: '/api/admin/llm-settings/prompt/preview/reply',
          method: 'post',
          values: {
            diaryContent: firstDiary,
            character: replyTarget.characterId,
            characterComment: replyTarget.text,
            userReply: text,
            commonPrompt: commonPrompt.trim() ? commonPrompt : null,
            replyPrompt: replyPrompt.trim() ? replyPrompt : null,
          },
        },
        {
          onSuccess: (response) => {
            const result = response.data as unknown as ReplyPreviewResult
            const newItems: SessionItem[] = [
              { id: nextId(), kind: 'user', text, replyTo: replyTarget.characterId },
              ...(result.replyText !== null
                ? [{ id: nextId(), kind: 'character' as const, text: result.replyText, characterId: result.character }]
                : []),
            ]
            setSession((prev) => [...prev, ...newItems])
            setSessionCost((cost) => cost + result.estimatedCostUsd)
            setLastRun({
              meta: metaOf(result),
              systemPrompt: result.systemPrompt,
              userContent: result.userContent,
              validationError: result.validationError,
              generationError: result.generationError,
              conditions: null,
            })
            setComposer('')
            setReplyTarget(null)
          },
          onError: (error) => setRequestError(errorMessageOf(error, '답장 미리보기 요청에 실패했습니다.')),
        },
      )
      return
    }
    // 일반 이어보내기는 현재 선택된 조건으로 새 피드를 만든다 - 캐릭터가 비어 있으면 보낼 수 없다.
    if (selected.length === 0) {
      return
    }
    runFeedPreview({ diaryContent: text, conversationSummary: transcriptSummary() || null, replaceSession: false })
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
                maxLength={DIARY_MAX_LENGTH}
                placeholder="예) 오늘 팀장님한테 깨졌는데 생각해보니 내 잘못이 아니었다"
                className="min-h-[7rem]"
              />
              <p className="mt-1 text-right text-[11px] tabular-nums text-muted-foreground">
                {diary.length.toLocaleString()} / {DIARY_MAX_LENGTH.toLocaleString()}
              </p>
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
            <PromptOverrideEditor type="REPLY" title="답글 프롬프트 오버라이드" value={replyPrompt} onChange={setReplyPrompt} />
          </div>

          <div className="flex items-center gap-3">
            <Button onClick={startSession} disabled={!canStart}>
              <Play className="size-4" />
              {running && session.length === 0 ? '생성 중…' : session.length > 0 ? '새로 생성해보기' : '생성해보기'}
            </Button>
            <p className="text-xs text-muted-foreground">
              {session.length > 0 ? '누르면 세션을 새로 시작합니다.' : '실제 LLM을 호출합니다. 결과는 저장되지 않습니다.'}
            </p>
          </div>
          {requestError && <p className="text-sm text-destructive">{requestError}</p>}
        </div>

        {/* 결과 세션 */}
        <div className="space-y-4">
          {session.length === 0 ? (
            <Card className="flex flex-col items-center justify-center gap-3 p-16 text-center">
              <div className="flex size-12 items-center justify-center rounded-full bg-muted">
                <FlaskConical className="size-6 text-muted-foreground" />
              </div>
              <div>
                <p className="text-sm font-medium">아직 실행한 실험이 없습니다</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  왼쪽에서 샘플 일기를 입력하고 생성해보세요. 생성 후에는 실제 유저처럼 이어서 메시지를 보내거나 캐릭터에게
                  답장할 수 있습니다.
                </p>
              </div>
            </Card>
          ) : (
            <>
              {lastRun?.generationError && (
                <div className="flex items-start gap-2.5 rounded-lg border border-destructive/30 bg-destructive/5 p-3.5 text-sm">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-destructive" />
                  <div>
                    <p className="font-medium text-destructive">생성 실패</p>
                    <p className="mt-0.5 break-keep text-muted-foreground">{lastRun.generationError}</p>
                  </div>
                </div>
              )}
              {lastRun?.validationError && (
                <div className="flex items-start gap-2.5 rounded-lg border border-amber-500/40 bg-amber-500/5 p-3.5 text-sm">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                  <div>
                    <p className="font-medium text-amber-700">출력 계약 위반</p>
                    <p className="mt-0.5 break-keep text-muted-foreground">
                      {lastRun.validationError} - 실제 생성이라면 재시도됐을 응답입니다.
                    </p>
                  </div>
                </div>
              )}

              {lastRun && (
                <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
                  <MetricTile icon={Cpu} label="모델" value={lastRun.meta.model.replace('gemini-', '')} />
                  <MetricTile icon={Clock} label="지연 (마지막)" value={`${(lastRun.meta.latencyMs / 1000).toFixed(1)}s`} />
                  <MetricTile
                    icon={Sigma}
                    label="토큰 (마지막)"
                    value={lastRun.meta.usedTokens.toLocaleString()}
                    sub={`입력 ${lastRun.meta.inputTokens.toLocaleString()} · 캐시 ${lastRun.meta.cachedTokens.toLocaleString()} · 출력 ${lastRun.meta.outputTokens.toLocaleString()}`}
                  />
                  <MetricTile
                    icon={CircleDollarSign}
                    label="비용 (마지막)"
                    value={`$${lastRun.meta.estimatedCostUsd.toFixed(5)}`}
                    sub={`세션 누적 $${sessionCost.toFixed(5)}`}
                  />
                </div>
              )}

              <Card className="space-y-4 p-5">
                {lastRun?.conditions && (
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="mr-1 text-xs font-medium text-muted-foreground">마지막 생성 조건</span>
                    {lastRun.conditions.characters.map((character) => (
                      <CharacterChip key={character} value={character} />
                    ))}
                    <Badge variant="secondary" className="whitespace-nowrap text-[10px]">
                      티키타카 {lastRun.conditions.tikitakaCount}
                    </Badge>
                    {lastRun.conditions.eongttungTopic && (
                      <Badge variant="secondary" className="whitespace-nowrap text-[10px]">
                        엉뚱 소재: {lastRun.conditions.eongttungTopic}
                      </Badge>
                    )}
                  </div>
                )}

                <div className="space-y-3.5">
                  {session.map((item) => (
                    <SessionBubble key={item.id} item={item} onReply={(target) => setReplyTarget(target)} />
                  ))}
                  {running && <p className="text-center text-xs text-muted-foreground">생성 중…</p>}
                </div>

                {/* 이어서 보내기 컴포저 */}
                <div className="space-y-2 border-t pt-3">
                  {replyTarget && (
                    <div className="flex items-center gap-1.5 text-xs">
                      <Badge variant="secondary" className="gap-1">
                        <CornerDownRight className="size-3" />
                        {labelOf(replyTarget.characterId ?? '')}에게 답장
                      </Badge>
                      <span className="max-w-64 truncate text-muted-foreground">"{replyTarget.text}"</span>
                      <button
                        type="button"
                        onClick={() => setReplyTarget(null)}
                        className="rounded p-0.5 text-muted-foreground hover:bg-muted hover:text-foreground"
                      >
                        <X className="size-3.5" />
                      </button>
                    </div>
                  )}
                  <div className="flex items-center gap-2">
                    <Input
                      value={composer}
                      onChange={(e) => setComposer(e.target.value)}
                      maxLength={DIARY_MAX_LENGTH}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
                          sendComposer()
                        }
                      }}
                      placeholder={
                        replyTarget
                          ? `${labelOf(replyTarget.characterId ?? '')}의 댓글에 답장하기…`
                          : selected.length === 0
                            ? '이어서 보내려면 왼쪽에서 등장 캐릭터를 선택하세요'
                            : '실제 유저처럼 이어서 메시지 보내기 (새 댓글 피드가 생성됩니다)'
                      }
                      disabled={running}
                    />
                    <Button
                      size="sm"
                      onClick={sendComposer}
                      disabled={!composer.trim() || running || (!replyTarget && selected.length === 0)}
                    >
                      <Send className="size-4" />
                      보내기
                    </Button>
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    캐릭터 말풍선에 마우스를 올리면 답장 버튼이 나타납니다. 답장은 그 캐릭터 1명이 재응답하고, 일반 메시지는
                    지금 조건으로 새 댓글 피드를 생성합니다(이전 대화는 요약으로 전달).
                  </p>
                </div>
              </Card>

              {lastRun && <PromptInspector systemPrompt={lastRun.systemPrompt} userContent={lastRun.userContent} />}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
