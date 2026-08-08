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

type PromptType = 'COMMON' | 'COMMENT' | 'REPLY' | 'CARD' | 'EONGTTUNG_TOPIC'

const TABS: { value: PromptType; label: string; hint: string }[] = [
  { value: 'COMMON', label: '공통', hint: '세 타입이 공유하는 톤·경계·말맛지침·보이스카드. 여기를 바꾸면 댓글·답글·카드에 모두 반영됩니다.' },
  { value: 'COMMENT', label: '댓글', hint: '여러 감정 캐릭터가 일기에 코멘트를 달고 서로 티키타카하는 생성.' },
  { value: 'REPLY', label: '답글', hint: '유저가 캐릭터 댓글에 답글을 달면 그 캐릭터 1명이 재응답하는 생성.' },
  { value: 'CARD', label: '카드', hint: '대화 종료 시 대표 캐릭터가 유저를 대신해 남기는 한 줄 카드 대사.' },
  { value: 'EONGTTUNG_TOPIC', label: '엉뚱이 소재', hint: '엉뚱이가 꺼낼 소재 목록. 한 줄에 하나씩 적으면 생성 시 서버가 무작위로 한 줄을 고릅니다.' },
]

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
            댓글·답글·카드 생성이 모두 이 모델을 씁니다. Gemini API에서 사용 가능한 모델을 자동으로 불러옵니다.
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
  const activeTab = TABS.find((t) => t.value === type)

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
      {type !== 'EONGTTUNG_TOPIC' && (
        <div className="flex items-center gap-2 rounded-lg border bg-muted/40 p-2.5 text-xs text-muted-foreground">
          <Layers className="size-4 shrink-0 text-muted-foreground/70" />
          <span>
            실제 시스템 프롬프트는 <span className="font-medium text-foreground">공통</span> +{' '}
            <span className="font-medium text-foreground">타입(댓글·답글·카드)</span> 으로 조립됩니다. 캐릭터 성격 등
            공통 부분은 <span className="font-medium text-foreground">공통</span> 탭에서 한 번에 바꾸세요.
          </span>
        </div>
      )}

      <div className="inline-flex items-center rounded-lg border bg-muted/40 p-1">
        {TABS.map((tab) => (
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

      {activeTab && <p className="text-sm text-muted-foreground">{activeTab.hint}</p>}

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
              {type === 'COMMON' ? '공통 프롬프트' : type === 'EONGTTUNG_TOPIC' ? '소재 목록 (한 줄에 하나)' : '타입 프롬프트'}
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
            <p className="mr-auto text-xs text-muted-foreground">
              {type === 'COMMON'
                ? '캐릭터 보이스카드·말맛지침 등 세 타입이 공유하는 부분입니다. 신중히 수정하세요.'
                : type === 'EONGTTUNG_TOPIC'
                  ? '빈 줄은 무시됩니다. 최소 한 줄은 있어야 엉뚱이가 등장할 수 있습니다.'
                  : '이 타입의 역할·규칙·출력형식입니다. 생성 시 공통 프롬프트 뒤에 붙습니다.'}
            </p>
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
