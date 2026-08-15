import type { Cpu } from 'lucide-react'
import { Card } from '@/components/ui/card'

/** 호출 1회의 지표(모델·지연·토큰·비용) 타일. 실험 종류와 무관하게 같은 모양을 쓴다. */
export function MetricTile({
  icon: Icon,
  label,
  value,
  sub,
}: {
  icon: typeof Cpu
  label: string
  value: string
  sub?: string
}) {
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
