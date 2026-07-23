import { useEffect, useMemo, useState } from 'react'
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
}

export function LlmSettingsPage() {
  const { data, isLoading, refetch } = useCustom<LlmSettings>({
    url: '/api/admin/llm-settings',
    method: 'get',
  })
  const { mutate: save, isLoading: saving } = useCustomMutation()

  const settings = data?.data
  const [model, setModel] = useState('')
  const [prompt, setPrompt] = useState('')
  const [justSaved, setJustSaved] = useState(false)

  useEffect(() => {
    if (settings) {
      setModel(settings.model)
      setPrompt(settings.systemPrompt)
    }
  }, [settings])

  // 현재 모델이 목록(동적 조회)에 없을 수도 있어(예: 조회 실패) 항상 포함시킨다.
  const modelOptions = useMemo(() => {
    if (!settings) {
      return []
    }
    return Array.from(new Set([settings.model, ...settings.availableModels]))
  }, [settings])

  const dirty = settings != null && (model !== settings.model || prompt.trim() !== settings.systemPrompt.trim())

  const onSave = () => {
    if (!dirty || !prompt.trim()) {
      return
    }
    save(
      { url: '/api/admin/llm-settings', method: 'put', values: { model, systemPrompt: prompt } },
      {
        onSuccess: () => {
          setJustSaved(true)
          window.setTimeout(() => setJustSaved(false), 2500)
          refetch()
        },
      },
    )
  }

  const onReset = () => {
    if (settings) {
      setModel(settings.model)
      setPrompt(settings.systemPrompt)
    }
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
        <Card>
          <div className="space-y-6 p-6">
            {/* 모델 */}
            <div className="space-y-2">
              <label htmlFor="model" className="text-sm font-medium">
                모델
              </label>
              <div className="max-w-xs">
                <Select id="model" value={model} onChange={(e) => setModel(e.target.value)}>
                  {modelOptions.map((m) => (
                    <option key={m} value={m}>
                      {m}
                    </option>
                  ))}
                </Select>
              </div>
              <p className="text-xs text-muted-foreground">
                Gemini API에서 사용 가능한 모델을 자동으로 불러옵니다. 새 모델은 출시되면 목록에 나타납니다.
              </p>
            </div>

            {/* 시스템 프롬프트 */}
            <div className="space-y-2">
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
              <p className="text-xs text-muted-foreground">
                캐릭터 보이스카드·규칙 등 생성 지침 전체입니다. 신중히 수정하세요.
              </p>
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 border-t px-6 py-4">
            {justSaved && (
              <span className="flex items-center gap-1.5 text-sm text-emerald-600">
                <Check className="size-4" />
                저장되었습니다
              </span>
            )}
            {dirty && !justSaved && <span className="text-sm text-muted-foreground">변경사항이 있습니다</span>}
            <Button variant="ghost" size="sm" onClick={onReset} disabled={!dirty || saving}>
              <RotateCcw className="size-4" />
              되돌리기
            </Button>
            <Button size="sm" onClick={onSave} disabled={!dirty || saving || !prompt.trim()}>
              <Save className="size-4" />
              {saving ? '저장 중…' : '저장'}
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}
