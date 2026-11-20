import { useCallback, useState } from 'react'
import { useCustom } from '@refinedev/core'
import { useNavigate } from 'react-router-dom'
import { ClipboardCheck, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Select } from '@/components/ui/select'
import { PageHeader } from '@/components/page-header'
import { SectionTitle } from '@/components/section-title'
import { SegmentedTabs } from '@/components/segmented-tabs'
import { ConversationThreadPanel } from '@/components/conversation-thread-panel'
import type { Emotion, LabelingNext, ResponseRow } from '@/lib/quality'
import { CalibrationPanel } from './calibration-panel'
import { LabelingMode } from './labeling-mode'
import { ResponseList, type ResponseFilters } from './response-list'
import { StatsSection } from './stats-section'

type Mode = 'browse' | 'label'

function kstDate(offsetDays = 0): string {
  const d = new Date(Date.now() + 9 * 3600 * 1000 + offsetDays * 86400 * 1000)
  return d.toISOString().slice(0, 10)
}

export function ConversationQualityPage() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('browse')
  const [days, setDays] = useState(14)
  const [filters, setFilters] = useState<ResponseFilters>({
    from: kstDate(-6),
    to: kstDate(),
    emotionType: '',
    kind: '',
    aiVerdict: 'AWKWARD',
    awkwardType: '',
    humanState: 'ALL',
    promptVersion: '',
  })
  const [selected, setSelected] = useState<ResponseRow | null>(null)
  const { data: progressData } = useCustom<LabelingNext>({ url: '/api/admin/conversation-quality/labeling/next', method: 'get' })
  const [progress, setProgress] = useState<{ mine: number; target: number } | null>(null)
  const progressLoaded = progress !== null || progressData !== undefined
  const mine = progress?.mine ?? progressData?.data.mineCount ?? 0
  const target = progress?.target ?? progressData?.data.target ?? 100
  const onProgress = useCallback((m: number, t: number) => setProgress({ mine: m, target: t }), [])

  return (
    <div className="space-y-8">
      <PageHeader
        title="대화 품질"
        description="캐릭터 응답이 어색했는지 봅니다. 어색한 응답을 대화 흐름 안에서 읽고, 실험실에서 바로 고쳐 볼 수 있습니다."
        actions={
          mode === 'browse' && (
            <Select value={String(days)} onChange={(e) => setDays(Number(e.target.value))} className="w-28">
              <option value="7">최근 7일</option>
              <option value="14">최근 14일</option>
              <option value="30">최근 30일</option>
            </Select>
          )
        }
      />

      <div className="flex flex-wrap items-center gap-4">
        <SegmentedTabs
          value={mode}
          onChange={setMode}
          options={[
            { value: 'browse', label: '살펴보기' },
            { value: 'label', label: '판정하기' },
          ]}
        />
        <div className="flex items-center gap-2.5 rounded-lg border bg-background px-3 py-1.5" title="내가 판정한 응답 수. 정답 세트의 목표는 100건입니다.">
          <span className="text-xs text-muted-foreground">내 정답 세트</span>
          <div className="h-1.5 w-24 overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${Math.min(100, (mine / target) * 100)}%` }} />
          </div>
          <span className="text-xs font-medium tabular-nums">
            {mine}
            <span className="text-muted-foreground"> / {target}</span>
          </span>
        </div>
      </div>

      {mode === 'browse' && progressLoaded && mine === 0 && (
        <Card className="flex flex-wrap items-center justify-between gap-3 p-5">
          <div className="flex items-start gap-3">
            <ClipboardCheck className="mt-0.5 size-5 text-primary" />
            <div>
              <p className="text-sm font-medium">정답 세트 만들기 0 / {target}</p>
              <p className="text-xs text-muted-foreground">
                AI 채점을 믿으려면 먼저 사람이 직접 판정한 응답이 필요합니다. {target}건을 판정하면 AI 채점과 얼마나 맞는지 확인할 수 있습니다.
              </p>
            </div>
          </div>
          <Button size="sm" onClick={() => setMode('label')}>
            시작하기
          </Button>
        </Card>
      )}

      {mode === 'browse' && progressLoaded && mine >= target && (
        <Card className="flex flex-wrap items-center justify-between gap-3 p-5">
          <p className="text-sm">정답 세트 {mine}건이 모였습니다. 판정하기 탭에서 AI 채점 일치율을 확인하세요.</p>
          <Button variant="outline" size="sm" onClick={() => setMode('label')}>
            일치율 확인
          </Button>
        </Card>
      )}

      {mode === 'browse' ? (
        <>
          <section className="space-y-4">
            <SectionTitle
              title="어색함 지표"
              description={`매일 06:00에 새로 생긴 응답을 AI가 채점합니다. (최근 ${days}일 기준)`}
              actions={
                <Button variant="outline" size="sm" onClick={() => navigate('/llm-settings', { state: { promptType: 'QUALITY_JUDGE' } })}>
                  <SlidersHorizontal className="size-4" />
                  채점 모델·프롬프트 설정
                </Button>
              }
            />
            <StatsSection
              days={days}
              selectedCharacter={filters.emotionType}
              onSelectCharacter={(emotion: Emotion | '') => setFilters((f) => ({ ...f, emotionType: emotion }))}
            />
          </section>
          <section className="space-y-4">
            <SectionTitle title="응답 살펴보기" description="행을 누르면 대화 흐름 안에서 그 응답을 봅니다. 원문 열람 기록이 남습니다." />
            <ResponseList filters={filters} onChange={setFilters} selectedMessageId={selected?.messageId ?? null} onSelect={setSelected} />
          </section>
        </>
      ) : (
        <>
          <section className="space-y-4">
            <SectionTitle title="직접 판정하기" description="AI 판정은 제출한 뒤에 보입니다. 숫자 키로 고르고 Enter로 제출합니다." />
            <LabelingMode onProgress={onProgress} />
          </section>
          <CalibrationPanel key={mine > 0 ? 'ready' : 'empty'} />
        </>
      )}

      {mode === 'browse' && selected && (
        <ConversationThreadPanel
          key={selected.conversationId}
          conversationId={selected.conversationId}
          focusMessageId={selected.messageId}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  )
}
