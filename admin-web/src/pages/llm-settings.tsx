import { useEffect, useMemo, useRef, useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Check, RotateCcw, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { PageHeader } from '@/components/page-header'

interface LlmSettings {
  model: string
  systemPrompt: string
  availableModels: string[]
  defaultModel: string
  defaultSystemPrompt: string
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

export function LlmSettingsPage() {
  const { data, isLoading } = useCustom<LlmSettings>({ url: '/api/admin/llm-settings', method: 'get' })
  const { mutate: save, isLoading: saving } = useCustomMutation()
  const settings = data?.data

  // 편집값(model/prompt)과 저장된 기준값(savedModel/savedPrompt)을 분리해, 한 필드 저장이
  // 다른 필드의 미저장 편집을 건드리지 않게 한다.
  const [model, setModel] = useState('')
  const [prompt, setPrompt] = useState('')
  const [savedModel, setSavedModel] = useState('')
  const [savedPrompt, setSavedPrompt] = useState('')
  const [flash, setFlash] = useState<'model' | 'prompt' | null>(null)
  const initialized = useRef(false)

  useEffect(() => {
    if (settings && !initialized.current) {
      initialized.current = true
      setModel(settings.model)
      setSavedModel(settings.model)
      setPrompt(settings.systemPrompt)
      setSavedPrompt(settings.systemPrompt)
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

  const flashSaved = (field: 'model' | 'prompt') => {
    setFlash(field)
    window.setTimeout(() => setFlash((f) => (f === field ? null : f)), 2500)
  }

  const saveModel = () => {
    if (!modelDirty) {
      return
    }
    save(
      { url: '/api/admin/llm-settings', method: 'put', values: { model, systemPrompt: savedPrompt } },
      {
        onSuccess: () => {
          setSavedModel(model)
          flashSaved('model')
        },
      },
    )
  }

  const savePrompt = () => {
    if (!promptDirty || !prompt.trim()) {
      return
    }
    save(
      { url: '/api/admin/llm-settings', method: 'put', values: { model: savedModel, systemPrompt: prompt } },
      {
        onSuccess: () => {
          setSavedPrompt(prompt)
          flashSaved('prompt')
        },
      },
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="LLM 설정"
        description="모델과 시스템 프롬프트를 수정하면 재배포 없이 다음 댓글 생성부터 반영됩니다."
      />

      {isLoading || !settings ? (
        <Card className="p-6">
          <div className="space-y-6">
            <Skeleton className="h-9 w-full max-w-xs" />
            <Skeleton className="h-64 w-full" />
          </div>
        </Card>
      ) : (
        <Card className="divide-y">
          {/* 모델 */}
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
            </div>
            <p className="text-xs text-muted-foreground">
              Gemini API에서 사용 가능한 모델을 자동으로 불러옵니다. 새 모델은 출시되면 목록에 나타납니다.
            </p>
          </section>

          {/* 시스템 프롬프트 */}
          <section className="space-y-3 p-6">
            <div className="flex items-baseline justify-between">
              <label htmlFor="prompt" className="text-sm font-medium">
                시스템 프롬프트
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
                캐릭터 보이스카드·규칙 등 생성 지침 전체입니다. 신중히 수정하세요.
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
            </div>
          </section>
        </Card>
      )}
    </div>
  )
}
