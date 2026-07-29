import { useEffect, useRef, useState } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Check, Gauge, RotateCcw, Save } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'

interface TokenPolicy {
  dailyTokenLimit: number
  resetHour: number
  updatedAt: string
}

const HOURS = Array.from({ length: 24 }, (_, h) => h)

export function PolicySection() {
  const { data, isLoading, isError, refetch } = useCustom<TokenPolicy>({ url: '/api/admin/token-policy', method: 'get' })
  const { mutate: save, isLoading: saving } = useCustomMutation()
  const policy = data?.data

  const [limit, setLimit] = useState('')
  const [resetHour, setResetHour] = useState(5)
  const [savedLimit, setSavedLimit] = useState('')
  const [savedResetHour, setSavedResetHour] = useState(5)
  const [flash, setFlash] = useState(false)
  const [error, setError] = useState(false)
  const initialized = useRef(false)

  useEffect(() => {
    if (policy && !initialized.current) {
      initialized.current = true
      setLimit(String(policy.dailyTokenLimit))
      setResetHour(policy.resetHour)
      setSavedLimit(String(policy.dailyTokenLimit))
      setSavedResetHour(policy.resetHour)
    }
  }, [policy])

  const parsedLimit = Number(limit)
  const validLimit = limit.trim() !== '' && Number.isInteger(parsedLimit) && parsedLimit >= 0
  const dirty = limit !== savedLimit || resetHour !== savedResetHour

  const onSave = () => {
    if (!dirty || !validLimit) {
      return
    }
    save(
      { url: '/api/admin/token-policy', method: 'put', values: { dailyTokenLimit: parsedLimit, resetHour } },
      {
        onSuccess: () => {
          setSavedLimit(limit)
          setSavedResetHour(resetHour)
          setError(false)
          setFlash(true)
          window.setTimeout(() => setFlash(false), 2500)
        },
        onError: () => setError(true),
      },
    )
  }

  return (
    <Card className="space-y-4 p-6">
      <div className="flex flex-wrap items-center gap-2">
        <Gauge className="size-4 text-muted-foreground" />
        <span className="text-sm font-medium">유저 일일 토큰 상한</span>
        <span className="rounded bg-amber-50 px-1.5 py-0.5 text-[11px] font-medium text-amber-700">prod에만 적용</span>
      </div>

      {isError && !policy ? (
        <div className="flex items-center gap-3">
          <p className="text-sm text-muted-foreground">상한 정책을 불러오지 못했습니다.</p>
          <Button variant="outline" size="sm" onClick={() => refetch()}>
            <RotateCcw className="size-4" />
            다시 시도
          </Button>
        </div>
      ) : isLoading || !policy ? (
        <Skeleton className="h-9 w-full max-w-md" />
      ) : (
        <>
          <div className="flex flex-wrap items-end gap-4">
            <div className="space-y-1.5">
              <label htmlFor="daily-limit" className="text-xs font-medium text-muted-foreground">
                일일 상한(토큰)
              </label>
              <Input
                id="daily-limit"
                type="number"
                min={0}
                step={1000}
                value={limit}
                onChange={(e) => setLimit(e.target.value)}
                className="w-48 tabular-nums"
              />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="reset-hour" className="text-xs font-medium text-muted-foreground">
                리셋 시각(KST)
              </label>
              <Select
                id="reset-hour"
                value={String(resetHour)}
                onChange={(e) => setResetHour(Number(e.target.value))}
                className="w-32"
              >
                {HOURS.map((h) => (
                  <option key={h} value={h}>
                    {String(h).padStart(2, '0')}:00
                  </option>
                ))}
              </Select>
            </div>
            <Button size="sm" onClick={onSave} disabled={!dirty || saving || !validLimit}>
              <Save className="size-4" />
              저장
            </Button>
            {flash && (
              <span className="flex items-center gap-1.5 text-sm text-emerald-600">
                <Check className="size-4" />
                저장되었습니다
              </span>
            )}
            {error && <span className="text-sm text-destructive">저장에 실패했습니다</span>}
          </div>
          <p className="text-xs text-muted-foreground">
            한 유저가 하루에 소비할 수 있는 총 토큰입니다. 매일 <span className="font-medium text-foreground">{String(resetHour).padStart(2, '0')}:00</span>(KST)에
            사용량이 초기화됩니다. 상한을 넘으면 메시지 저장은 유지된 채 댓글·답글·카드 생성만 차단됩니다. dev 환경에는 상한이 적용되지 않습니다.
          </p>
        </>
      )}
    </Card>
  )
}
