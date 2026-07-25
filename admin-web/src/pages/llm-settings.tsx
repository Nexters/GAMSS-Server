import { useEffect, useMemo, useRef, useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Check, Layers, RotateCcw, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageHeader } from '@/components/page-header'
import { cn } from '@/lib/utils'

type PromptType = 'COMMON' | 'COMMENT' | 'REPLY' | 'CARD'

interface LlmSettings {
  promptType: PromptType
  modelEditable: boolean
  model: string
  systemPrompt: string
  availableModels: string[]
  defaultModel: string
  defaultSystemPrompt: string
}

const TABS: { value: PromptType; label: string; hint: string }[] = [
  { value: 'COMMON', label: '공통', hint: '세 타입이 공유하는 톤·경계·말맛지침·보이스카드. 여기를 바꾸면 댓글·답글·카드에 모두 반영됩니다.' },
  { value: 'COMMENT', label: '댓글', hint: '여러 감정 캐릭터가 일기에 코멘트를 달고 서로 티키타카하는 생성.' },
  { value: 'REPLY', label: '답글', hint: '유저가 캐릭터 댓글에 답글을 달면 그 캐릭터 1명이 재응답하는 생성.' },
  { value: 'CARD', label: '카드', hint: '대화 종료 시 대표 캐릭터가 유저를 대신해 남기는 한 줄 카드 대사.' },
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

export function LlmSettingsPage() {
  const [type, setType] = useState<PromptType>('COMMENT')
  const { data, isLoading, isError, refetch } = useCustom<LlmSettings>({
    url: `/api/admin/llm-settings?promptType=${type}`,
    method: 'get',
  })
  const { mutate: save, isLoading: saving } = useCustomMutation()
  const settings = data?.data

  // 편집값과 저장된 기준값을 분리해, 한 필드 저장이 다른 필드의 미저장 편집을 건드리지 않게 한다.
  const [model, setModel] = useState('')
  const [prompt, setPrompt] = useState('')
  const [savedModel, setSavedModel] = useState('')
  const [savedPrompt, setSavedPrompt] = useState('')
  const [flash, setFlash] = useState<'model' | 'prompt' | null>(null)
  const [saveError, setSaveError] = useState<'model' | 'prompt' | null>(null)
  // 탭을 바꿔 다른 타입 데이터가 로드되면 편집 상태를 그 타입 값으로 다시 초기화한다.
  const loadedType = useRef<PromptType | null>(null)

  useEffect(() => {
    if (settings && settings.promptType !== loadedType.current) {
      loadedType.current = settings.promptType
      setModel(settings.model)
      setSavedModel(settings.model)
      setPrompt(settings.systemPrompt)
      setSavedPrompt(settings.systemPrompt)
      setFlash(null)
      setSaveError(null)
    }
  }, [settings])

  // 현재/저장/기본 모델이 동적 목록에 없을 수도 있어 항상 포함시킨다.
  const modelOptions = useMemo(() => {
    if (!settings) {
      return []
    }
    return Array.from(new Set([savedModel, model, ...settings.availableModels].filter(Boolean)))
  }, [settings, savedModel, model])

  const modelDirty = model !== savedModel
  const promptDirty = prompt.trim() !== savedPrompt.trim()
  const ready = Boolean(settings) && settings?.promptType === type

  const flashSaved = (field: 'model' | 'prompt') => {
    setFlash(field)
    window.setTimeout(() => setFlash((f) => (f === field ? null : f)), 2500)
  }

  const saveModel = () => {
    if (!modelDirty) {
      return
    }
    save(
      { url: '/api/admin/llm-settings', method: 'put', values: { promptType: type, model, systemPrompt: savedPrompt } },
      {
        onSuccess: () => {
          setSavedModel(model)
          setSaveError((e) => (e === 'model' ? null : e))
          flashSaved('model')
        },
        onError: () => setSaveError('model'),
      },
    )
  }

  const savePrompt = () => {
    if (!promptDirty || !prompt.trim()) {
      return
    }
    const values =
      settings?.modelEditable === false
        ? { promptType: type, systemPrompt: prompt }
        : { promptType: type, model: savedModel, systemPrompt: prompt }
    save(
      { url: '/api/admin/llm-settings', method: 'put', values },
      {
        onSuccess: () => {
          setSavedPrompt(prompt)
          setSaveError((e) => (e === 'prompt' ? null : e))
          flashSaved('prompt')
        },
        onError: () => setSaveError('prompt'),
      },
    )
  }

  const activeTab = TABS.find((t) => t.value === type)

  return (
    <div className="space-y-6">
      <PageHeader
        title="LLM 설정"
        description="모델·시스템 프롬프트를 타입별로 수정하면 재배포 없이 다음 생성부터 반영됩니다."
      />

      <div className="flex items-center gap-2 rounded-lg border bg-muted/40 p-2.5 text-xs text-muted-foreground">
        <Layers className="size-4 shrink-0 text-muted-foreground/70" />
        <span>
          실제 시스템 프롬프트는 <span className="font-medium text-foreground">공통</span> +{' '}
          <span className="font-medium text-foreground">타입(댓글·답글·카드)</span> 으로 조립됩니다. 캐릭터 성격 등
          공통 부분은 <span className="font-medium text-foreground">공통</span> 탭에서 한 번에 바꾸세요.
        </span>
      </div>

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
          <p className="text-sm text-muted-foreground">설정을 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </Card>
      ) : isLoading || !ready ? (
        <Card className="p-6">
          <div className="space-y-6">
            <Skeleton className="h-9 w-full max-w-xs" />
            <Skeleton className="h-64 w-full" />
          </div>
        </Card>
      ) : (
        <Card className="divide-y">
          {/* 모델 — COMMON은 모델이 없어 숨김 */}
          {settings?.modelEditable && (
            <section className="space-y-3 p-6">
              <label htmlFor="model" className="text-sm font-medium">
                모델
              </label>
              <div className="flex flex-wrap items-center gap-2">
                <div className="w-full max-w-xs">
                  <Select id="model" value={model} onChange={(e) => setModel(e.target.value)}>
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
                <Button size="sm" onClick={saveModel} disabled={!modelDirty || saving}>
                  <Save className="size-4" />
                  저장
                </Button>
                <SavedFlash show={flash === 'model'} />
                {saveError === 'model' && <span className="text-sm text-destructive">저장에 실패했습니다</span>}
              </div>
              <p className="text-xs text-muted-foreground">
                Gemini API에서 사용 가능한 모델을 자동으로 불러옵니다. 새 모델은 출시되면 목록에 나타납니다.
              </p>
            </section>
          )}

          {/* 시스템 프롬프트 */}
          <section className="space-y-3 p-6">
            <div className="flex items-baseline justify-between">
              <label htmlFor="prompt" className="text-sm font-medium">
                {type === 'COMMON' ? '공통 프롬프트' : '타입 프롬프트'}
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
                  : '이 타입의 역할·규칙·출력형식입니다. 생성 시 공통 프롬프트 뒤에 붙습니다.'}
              </p>
              <SavedFlash show={flash === 'prompt'} />
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPrompt(settings.defaultSystemPrompt)}
                disabled={saving || prompt.trim() === settings.defaultSystemPrompt.trim()}
              >
                <RotateCcw className="size-4" />
                기본값 변경
              </Button>
              <Button size="sm" onClick={savePrompt} disabled={!promptDirty || saving || !prompt.trim()}>
                <Save className="size-4" />
                저장
              </Button>
              {saveError === 'prompt' && <span className="text-sm text-destructive">저장에 실패했습니다</span>}
            </div>
          </section>
        </Card>
      )}
    </div>
  )
}
