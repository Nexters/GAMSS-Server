import { VERDICT_META, type Verdict } from '@/lib/quality'
import { cn } from '@/lib/utils'

const STYLES: Record<Verdict, { dot: string; className: string }> = {
  AWKWARD: { dot: 'bg-rose-500', className: 'bg-rose-50 text-rose-700 ring-rose-600/10' },
  NATURAL: { dot: 'bg-emerald-500', className: 'bg-emerald-50 text-emerald-700 ring-emerald-600/10' },
  UNSURE: { dot: 'bg-muted-foreground/40', className: 'bg-muted text-muted-foreground ring-border' },
}

export function VerdictBadge({ verdict, prefix, className }: { verdict: Verdict; prefix?: string; className?: string }) {
  const style = STYLES[verdict]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        style.className,
        className,
      )}
    >
      <span className={cn('size-1.5 rounded-full', style.dot)} />
      {prefix && <span className="font-normal opacity-70">{prefix}</span>}
      {VERDICT_META[verdict].label}
    </span>
  )
}

export function TypeChip({ label, title }: { label: string; title?: string }) {
  return (
    <span title={title} className="inline-flex items-center whitespace-nowrap rounded-md border bg-background px-1.5 py-0.5 text-[11px] text-muted-foreground">
      {label}
    </span>
  )
}
