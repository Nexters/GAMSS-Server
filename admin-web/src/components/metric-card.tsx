import type { ReactNode } from 'react'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

type Tone = 'default' | 'danger' | 'success'

const TONE_VALUE: Record<Tone, string> = {
  default: 'text-foreground',
  danger: 'text-red-600',
  success: 'text-emerald-600',
}

/**
 * 대시보드 KPI 한 칸. 라벨 + 큰 수치 + (선택) 보조 설명. tone 으로 위험/정상 강조를 표현한다.
 */
export function MetricCard({
  label,
  value,
  hint,
  tone = 'default',
  icon,
}: {
  label: string
  value: string
  hint?: ReactNode
  tone?: Tone
  icon?: ReactNode
}) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">{label}</p>
        {icon && <span className="text-muted-foreground/60">{icon}</span>}
      </div>
      <p className={cn('mt-1.5 text-2xl font-semibold tracking-tight tabular-nums', TONE_VALUE[tone])}>{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </Card>
  )
}
