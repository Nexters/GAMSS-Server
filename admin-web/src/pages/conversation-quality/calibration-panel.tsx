import { useEffect } from 'react'
import { useCustom, useCustomMutation } from '@refinedev/core'
import { Loader2, Play } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { MetricCard } from '@/components/metric-card'
import { SectionTitle } from '@/components/section-title'
import { AWKWARD_TYPE_META, type AwkwardType } from '@/lib/quality'

interface CalibrationStatus {
  run: {
    judgeModel: string
    judgePromptVersion: number
    total: number
    processed: number
    failed: number
    running: boolean
    aborted: boolean
  } | null
  report: {
    judgeModel: string
    judgePromptVersion: number
    humanVerdictCount: number
    comparedPairs: number
    aiHumanAgreement: number | null
    humanHumanAgreement: number | null
    humanHumanPairs: number
    byType: {
      type: AwkwardType
      humanMarked: number
      missedRate: number | null
      aiMarked: number
      overflagRate: number | null
      humanHumanAgreement: number | null
    }[]
  }
}

const pct = (v: number | null) => (v === null ? '-' : `${v}%`)

export function CalibrationPanel() {
  const { data, refetch } = useCustom<CalibrationStatus>({ url: '/api/admin/conversation-quality/calibration', method: 'get' })
  const { mutate: start, isLoading: starting } = useCustomMutation()
  const status = data?.data
  const running = status?.run?.running ?? false

  useEffect(() => {
    if (!running) {
      return
    }
    const id = window.setInterval(() => void refetch(), 3000)
    return () => window.clearInterval(id)
  }, [running, refetch])

  const report = status?.report

  return (
    <section className="space-y-4">
      <SectionTitle
        title="정답 세트로 AI 채점 확인"
        description={`사람 판정이 있는 응답을 지금 채점 설정(${report?.judgeModel ?? '-'} · 채점 프롬프트 v${report?.judgePromptVersion ?? '-'})으로 다시 채점해 비교합니다. 채점 설정을 바꾼 뒤 자동 채점을 켜기 전에 돌려 보세요.`}
        actions={
          <Button
            size="sm"
            disabled={running || starting || (report?.humanVerdictCount ?? 0) === 0}
            onClick={() => start({ url: '/api/admin/conversation-quality/calibration', method: 'post', values: {} }, { onSuccess: () => void refetch() })}
          >
            {running ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4" />}
            {running ? `채점 중 ${status?.run?.processed ?? 0} / ${status?.run?.total ?? 0}` : '정답 세트로 채점 돌려보기'}
          </Button>
        }
      />

      {status?.run && !running && status.run.total > 0 && (
        <p className="text-xs text-muted-foreground">
          마지막 실행: {status.run.processed} / {status.run.total}건 처리, 실패 {status.run.failed}건{status.run.aborted && ' (업스트림이 막혀 중간에 멈춤)'}
        </p>
      )}

      {report && (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <MetricCard label="AI-사람 일치율" value={pct(report.aiHumanAgreement)} hint={`${report.comparedPairs}쌍 비교`} />
            <MetricCard label="사람끼리 일치율" value={pct(report.humanHumanAgreement)} hint={`${report.humanHumanPairs}쌍 · 두 명 이상 판정한 응답만`} />
            <MetricCard label="사람 판정" value={report.humanVerdictCount.toLocaleString()} hint="AI 일치율이 사람끼리 일치율에 가까우면 자동 채점을 켜도 됩니다" />
          </div>
          <Card className="overflow-hidden">
            <div className="border-b px-4 py-3">
              <p className="text-sm font-medium">유형별 비교</p>
              <p className="text-xs text-muted-foreground">놓친 비율은 사람만 어색하다고 본 비율, 과잉 지적 비율은 AI만 어색하다고 본 비율입니다.</p>
            </div>
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>유형</TableHead>
                  <TableHead className="text-right">사람이 표시</TableHead>
                  <TableHead className="text-right" title="사람은 어색함, AI는 자연스러움">
                    놓친 비율
                  </TableHead>
                  <TableHead className="text-right">AI가 표시</TableHead>
                  <TableHead className="text-right" title="AI는 어색함, 사람은 자연스러움">
                    과잉 지적 비율
                  </TableHead>
                  <TableHead className="text-right" title="낮으면 유형 정의가 모호하다는 뜻">
                    사람끼리 일치율
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {report.byType.map((t) => (
                  <TableRow key={t.type} className="hover:bg-transparent">
                    <TableCell className="text-sm">{AWKWARD_TYPE_META[t.type].label}</TableCell>
                    <TableCell className="text-right tabular-nums">{t.humanMarked}</TableCell>
                    <TableCell className="text-right tabular-nums">{pct(t.missedRate)}</TableCell>
                    <TableCell className="text-right tabular-nums">{t.aiMarked}</TableCell>
                    <TableCell className="text-right tabular-nums">{pct(t.overflagRate)}</TableCell>
                    <TableCell className="text-right tabular-nums">{pct(t.humanHumanAgreement)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          </Card>
        </>
      )}
    </section>
  )
}
