import { useEffect, useMemo, useRef, useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Check, Cpu, Layers, RotateCcw, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageHeader } from '@/components/page-header'
import { PromptRevisionHistory } from '@/pages/prompt-revision-history'
import { cn } from '@/lib/utils'

type PromptType = 'COMMON' | 'COMMENT' | 'REPLY' | 'CARD' | 'CARD_EMOTION' | 'EONGTTUNG_TOPIC'

interface PromptTab {
  value: PromptType
  /** 탭 버튼에 보이는 이름. */
  label: string
  /** 탭 아래 한 줄 설명 — 이 프롬프트가 무엇을 만드는지. */
  hint: string
  /** 편집기 위 라벨. */
  editorLabel: string
  /** 편집기 아래 주의사항. */
  editorNote: string
  /** 공통 프롬프트와 조립되지 않고 단독으로 쓰이는 타입(조립 안내 배너를 숨긴다). */
  standalone?: boolean
}

/**
 * 탭 정의. `Record<PromptType, ...>`이라 타입을 하나 늘리고 탭을 빠뜨리면 컴파일이 막힌다 —
 * 배열로 두면 누락돼도 조회가 undefined를 돌려주며 라벨이 조용히 빈 칸으로 렌더된다.
 */
const TABS: Record<PromptType, PromptTab> = {
  COMMON: {
    value: 'COMMON',
    label: '공통',
    hint: '댓글·답글이 공유하는 톤·경계·말맛지침·보이스카드. 여기를 바꾸면 두 타입에 반영됩니다(카드·카드 감정·엉뚱이 소재는 제외).',
    editorLabel: '공통 프롬프트',
    editorNote: '캐릭터 보이스카드·말맛지침 등 댓글·답글이 공유하는 부분입니다. 신중히 수정하세요.',
  },
  COMMENT: {
    value: 'COMMENT',
    label: '댓글',
    hint: '여러 감정 캐릭터가 일기에 코멘트를 달고 서로 티키타카하는 생성.',
    editorLabel: '타입 프롬프트',
    editorNote: '이 타입의 역할·규칙·출력형식입니다. 생성 시 공통 프롬프트 뒤에 붙습니다.',
  },
  REPLY: {
    value: 'REPLY',
    label: '답글',
    hint: '유저가 캐릭터 댓글에 답글을 달면 그 캐릭터 1명이 재응답하는 생성.',
    editorLabel: '타입 프롬프트',
    editorNote: '이 타입의 역할·규칙·출력형식입니다. 생성 시 공통 프롬프트 뒤에 붙습니다.',
  },
  CARD: {
    value: 'CARD',
    label: '카드',
    hint: '대화 종료 시 그날 있었던 일을 유저 시점 한 줄(공백 포함 50자 이하)로 요약하는 생성.',
    editorLabel: '카드 한 줄 프롬프트',
    editorNote:
      '캐릭터 말투를 쓰지 않는 요약이라 공통 프롬프트와 조립하지 않고 단독으로 쓰입니다. 길이는 45자로 지시하되 50자를 넘으면 서버가 어절 경계에서 자릅니다.',
    standalone: true,
  },
  CARD_EMOTION: {
    value: 'CARD_EMOTION',
    label: '카드 감정',
    hint: '카드 생성 요청에 감정이 없을 때, 유저가 보낸 메시지(없으면 요청 요약)를 보고 감정 6종 중 하나를 고르는 분류.',
    editorLabel: '감정 분류 프롬프트',
    editorNote: '분류 작업이라 공통 프롬프트와 조립하지 않고 단독으로 쓰입니다. 응답은 감정 6종으로 강제됩니다.',
    standalone: true,
  },
  EONGTTUNG_TOPIC: {
    value: 'EONGTTUNG_TOPIC',
    label: '엉뚱이 소재',
    hint: '엉뚱이가 꺼낼 소재 목록. 한 줄에 하나씩 적으면 생성 시 서버가 무작위로 한 줄을 고릅니다.',
    editorLabel: '소재 목록 (한 줄에 하나)',
    editorNote: '빈 줄은 무시됩니다. 목록을 전부 비우면 저장할 수 없습니다.',
    standalone: true,
  },
}

function SavedFlash({ show }: { show: boolean }) {
  if (!show) {
    return null
  }
  return (
    <span className="flex items-center gap-1.5 text-sm text-emerald-600">
      <Check className="size-4" />
      저장되었습니다
    </span>
  )
}

interface ModelSetting {
  model: string
  availableModels: string[]
  defaultModel: string
}

function ModelSection() {
  const { data, isLoading, isError, refetch } = useCustom<ModelSetting>({ url: '/api/admin/llm-settings/model', method: 'get' })
  const { mutate: save, isLoading: saving } = useCustomMutation()
  const settings = data?.data

  const [model, setModel] = useState('')
  const [savedModel, setSavedModel] = useState('')
  const [flash, setFlash] = useState(false)
  const [error, setError] = useState(false)
  const initialized = useRef(false)

  useEffect(() => {
    if (settings && !initialized.current) {
      initialized.current = true
      setModel(settings.model)
      setSavedModel(settings.model)
    }
  }, [settings])

  const modelOptions = useMemo(() => {
    if (!settings) {
      return []
    }
    return Array.from(new Set([savedModel, model, ...settings.availableModels].filter(Boolean)))
  }, [settings, savedModel, model])

  const dirty = model !== savedModel

  const onSave = () => {
    if (!dirty) {
      return
    }
    save(
      { url: '/api/admin/llm-settings/model', method: 'put', values: { model } },
      {
        onSuccess: () => {
          setSavedModel(model)
          setError(false)
          setFlash(true)
          window.setTimeout(() => setFlash(false), 2500)
        },
        onError: () => setError(true),
      },
    )
  }

  return (
    <Card className="space-y-3 p-6">
      <div className="flex items-center gap-2">
        <Cpu className="size-4 text-muted-foreground" />
        <span className="text-sm font-medium">모델</span>
        <span className="rounded bg-muted px-1.5 py-0.5 text-[11px] font-medium text-muted-foreground">앱 전체 공통</span>
      </div>

      {isError && !settings ? (
        <div className="flex items-center gap-3">
          <p className="text-sm text-muted-foreground">모델을 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </div>
      ) : isLoading || !settings ? (
        <Skeleton className="h-9 w-full max-w-xs" />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <div className="w-full max-w-xs">
              <Select value={model} onChange={(e) => setModel(e.target.value)}>
                {modelOptions.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </Select>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setModel(settings.defaultModel)}
              disabled={saving || model === settings.defaultModel}
            >
              <RotateCcw className="size-4" />
              기본값 변경
            </Button>
            <Button size="sm" onClick={onSave} disabled={!dirty || saving}>
              <Save className="size-4" />
              저장
            </Button>
            <SavedFlash show={flash} />
            {error && <span className="text-sm text-destructive">저장에 실패했습니다</span>}
          </div>
          <p className="text-xs text-muted-foreground">
            댓글·답글·카드 생성과 카드 감정 분류가 모두 이 모델을 씁니다. Gemini API에서 사용 가능한 모델을 자동으로 불러옵니다.
          </p>
        </>
      )}
    </Card>
  )
}

interface PromptSetting {
  promptType: PromptType
  systemPrompt: string
}

function PromptSection() {
  const [type, setType] = useState<PromptType>('COMMENT')
  const { data, isLoading, isError, refetch } = useCustom<PromptSetting>({
    url: `/api/admin/llm-settings/prompt?promptType=${type}`,
    method: 'get',
  })
  const { mutate: save, isLoading: saving } = useCustomMutation()
  const settings = data?.data

  const [prompt, setPrompt] = useState('')
  const [savedPrompt, setSavedPrompt] = useState('')
  const [flash, setFlash] = useState(false)
  const [error, setError] = useState(false)
  const loadedType = useRef<PromptType | null>(null)

  useEffect(() => {
    if (settings && settings.promptType !== loadedType.current) {
      loadedType.current = settings.promptType
      setPrompt(settings.systemPrompt)
      setSavedPrompt(settings.systemPrompt)
      setFlash(false)
      setError(false)
    }
  }, [settings])

  const dirty = prompt.trim() !== savedPrompt.trim()
  const ready = Boolean(settings) && settings?.promptType === type
  const activeTab = TABS[type]

  const onSave = () => {
    if (!dirty || !prompt.trim()) {
      return
    }
    save(
      { url: '/api/admin/llm-settings/prompt', method: 'put', values: { promptType: type, systemPrompt: prompt } },
      {
        onSuccess: () => {
          setSavedPrompt(prompt)
          setError(false)
          setFlash(true)
          window.setTimeout(() => setFlash(false), 2500)
        },
        onError: () => setError(true),
      },
    )
  }

  return (
    <div className="space-y-4">
      {!activeTab.standalone && (
        <div className="flex items-center gap-2 rounded-lg border bg-muted/40 p-2.5 text-xs text-muted-foreground">
          <Layers className="size-4 shrink-0 text-muted-foreground/70" />
          <span>
            실제 시스템 프롬프트는 <span className="font-medium text-foreground">공통</span> +{' '}
            <span className="font-medium text-foreground">타입(댓글·답글)</span> 으로 조립됩니다. 캐릭터 성격 등
            공통 부분은 <span className="font-medium text-foreground">공통</span> 탭에서 한 번에 바꾸세요.
          </span>
        </div>
      )}

      <div className="inline-flex items-center rounded-lg border bg-muted/40 p-1">
        {Object.values(TABS).map((tab) => (
          <button
            key={tab.value}
            type="button"
            onClick={() => setType(tab.value)}
            className={cn(
              'rounded-md px-4 py-1.5 text-sm font-medium transition-colors',
              type === tab.value
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <p className="text-sm text-muted-foreground">{activeTab.hint}</p>

      {isError && !settings ? (
        <Card className="flex flex-col items-start gap-3 p-6">
          <p className="text-sm text-muted-foreground">프롬프트를 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </Card>
      ) : isLoading || !ready ? (
        <Card className="p-6">
          <Skeleton className="h-64 w-full" />
        </Card>
      ) : (
        <Card className="space-y-3 p-6">
          <div className="flex items-baseline justify-between">
            <label htmlFor="prompt" className="text-sm font-medium">
              {activeTab.editorLabel}
            </label>
            <span className="text-xs tabular-nums text-muted-foreground">{prompt.length.toLocaleString()}자</span>
          </div>
          <Textarea
            id="prompt"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            spellCheck={false}
            className="min-h-[28rem] font-mono text-[13px] leading-relaxed"
          />
          <div className="flex flex-wrap items-center gap-2">
            <p className="mr-auto text-xs text-muted-foreground">{activeTab.editorNote}</p>
            <SavedFlash show={flash} />
            <Button size="sm" onClick={onSave} disabled={!dirty || saving || !prompt.trim()}>
              <Save className="size-4" />
              저장
            </Button>
            {error && <span className="text-sm text-destructive">저장에 실패했습니다</span>}
          </div>
        </Card>
      )}

      {ready && (
        <PromptRevisionHistory
          key={type}
          type={type}
          currentPrompt={savedPrompt}
          onLoadToEditor={(content) => setPrompt(content)}
          onRestored={(content) => {
            setPrompt(content)
            setSavedPrompt(content)
          }}
        />
      )}
    </div>
  )
}

export function LlmSettingsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="LLM 설정"
        description="모델은 앱 전체 공통, 프롬프트는 타입별로 수정하면 재배포 없이 다음 생성부터 반영됩니다."
      />
      <ModelSection />
      <PromptSection />
    </div>
  )
}
