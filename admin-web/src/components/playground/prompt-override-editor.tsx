import { useState } from 'react'
import { useCustom } from '@refinedev/core'
import { ChevronDown, ChevronRight, Download } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/** 오버라이드할 수 있는 프롬프트 종류. 서버 PromptType 중 실험실에서 다루는 것들이다. */
export type OverridablePromptType = 'COMMON' | 'COMMENT' | 'REPLY' | 'CARD'

/**
 * 접이식 프롬프트 오버라이드 에디터. 비워두면 저장된 현재값으로 생성된다.
 *
 * 실험실의 두 실험(댓글·답글 / 카드)이 같은 컴포넌트를 쓴다 — 같은 모양을 각자 들고 있으면
 * 한쪽만 고쳐져 화면이 다시 어긋난다.
 */
export function PromptOverrideEditor({
  type,
  title,
  value,
  onChange,
}: {
  type: OverridablePromptType
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
