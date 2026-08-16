import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Card } from '@/components/ui/card'

/**
 * 마지막 호출에서 전달된 프롬프트 전문 접이식 뷰어.
 *
 * [systemPromptLabel]만 실험마다 다르다 — 댓글·답글은 공통 프롬프트와 조립된 결과이고,
 * 카드는 조립 없이 단독으로 쓰이기 때문에 무엇을 보고 있는지 라벨로 구분해준다.
 */
export function PromptInspector({
  systemPrompt,
  userContent,
  systemPromptLabel = '시스템 프롬프트 (조립본)',
}: {
  systemPrompt: string
  userContent: string
  systemPromptLabel?: string
}) {
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
            <p className="mb-1.5 text-xs font-medium text-muted-foreground">{systemPromptLabel}</p>
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
