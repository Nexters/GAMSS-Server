import { cn } from '@/lib/utils'

/** 이름 첫 글자로 만든 원형 아바타. 이미지가 없을 때의 기본 표시. */
export function Avatar({ name, className }: { name?: string | null; className?: string }) {
  const initial = name?.trim()?.[0]?.toUpperCase() ?? '?'
  return (
    <span
      className={cn(
        'inline-flex shrink-0 select-none items-center justify-center rounded-full bg-secondary font-medium text-secondary-foreground',
        className,
      )}
    >
      {initial}
    </span>
  )
}
