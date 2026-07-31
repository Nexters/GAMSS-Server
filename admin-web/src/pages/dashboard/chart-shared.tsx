import { RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

/** "2026-07-26" → "7/26" (차트 X축 라벨용). */
export function shortDate(date: string): string {
  const [, month, day] = date.split('-')
  return `${Number(month)}/${Number(day)}`
}

interface SeriesPayload {
  name?: string
  value?: number
  color?: string
}

/** 여러 시리즈(대화·카드·메시지, 성공·실패 등)를 색점과 함께 보여주는 공통 차트 툴팁. */
export function SeriesTooltip(props: { active?: boolean; payload?: SeriesPayload[]; label?: string }) {
  if (!props.active || !props.payload?.length) {
    return null
  }
  return (
    <div className="rounded-md border bg-background px-2.5 py-1.5 text-xs shadow-sm">
      <p className="mb-1 font-medium">{props.label}</p>
      {props.payload.map((p) => (
        <p key={p.name} className="flex items-center gap-1.5 text-muted-foreground">
          <span className="size-2 rounded-full" style={{ background: p.color }} />
          {p.name} {p.value ?? 0}
        </p>
      ))}
    </div>
  )
}

/** 대시보드 섹션의 지표 로드 실패 상태. "데이터 없음"과 구분해 재시도를 제공한다. */
export function SectionError({ onRetry }: { onRetry: () => void }) {
  return (
    <Card className="flex flex-col items-start gap-3 p-6">
      <p className="text-sm text-muted-foreground">지표를 불러오지 못했습니다.</p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        <RotateCcw className="size-4" />
        다시 시도
      </Button>
    </Card>
  )
}
