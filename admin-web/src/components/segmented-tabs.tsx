import { cn } from '@/lib/utils'

/**
 * 화면 안에서 성격이 다른 영역을 오가는 탭. 한 화면의 하위 전환이라 라우팅은 하지 않는다.
 *
 * 프롬프트 설정(llm-settings)과 실험실이 같은 모양을 쓴다 — 같은 동작에 다른 모양을 쓰면
 * 사용자는 둘을 다른 기능으로 배운다.
 */
export function SegmentedTabs<T extends string>({
  value,
  onChange,
  options,
  className,
}: {
  value: T
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  className?: string
}) {
  return (
    <div className={cn('inline-flex items-center rounded-lg border bg-muted/40 p-1', className)}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={value === option.value}
          className={cn(
            'rounded-md px-4 py-1.5 text-sm font-medium transition-colors',
            value === option.value ? 'bg-background text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
