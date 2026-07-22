import { cn } from '@/lib/utils'
import type { MemberStatus } from '@/types/member'

const STYLES: Record<MemberStatus, { label: string; dot: string; className: string }> = {
  ACTIVE: {
    label: '활성',
    dot: 'bg-emerald-500',
    className: 'bg-emerald-50 text-emerald-700 ring-emerald-600/10',
  },
  WITHDRAWN: {
    label: '탈퇴',
    dot: 'bg-muted-foreground/40',
    className: 'bg-muted text-muted-foreground ring-border',
  },
}

export function StatusBadge({ status }: { status: MemberStatus }) {
  const style = STYLES[status]
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset',
        style.className,
      )}
    >
      <span className={cn('size-1.5 rounded-full', style.dot)} />
      {style.label}
    </span>
  )
}
