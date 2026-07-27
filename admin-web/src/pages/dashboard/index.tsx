import { Suspense, lazy } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { PageHeader } from '@/components/page-header'

// 차트(recharts)는 무거워서 지연 로드해 초기·로그인 번들에서 제외한다.
const UsageSection = lazy(() => import('./usage-section').then((m) => ({ default: m.UsageSection })))
const QualitySection = lazy(() => import('./quality-section').then((m) => ({ default: m.QualitySection })))

function SectionTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className="space-y-0.5">
      <h2 className="text-sm font-semibold tracking-tight text-foreground">{title}</h2>
      <p className="text-xs text-muted-foreground">{description}</p>
    </div>
  )
}

export function DashboardPage() {
  return (
    <div className="space-y-8">
      <PageHeader title="대시보드" description="서비스 사용량과 LLM 생성 품질을 한눈에 확인합니다. (최근 14일 기준)" />

      <section className="space-y-4">
        <SectionTitle title="사용량 / 도입" description="오늘의 활동량과 회원·감정 지표" />
        <Suspense fallback={<Skeleton className="h-[380px]" />}>
          <UsageSection />
        </Suspense>
      </section>

      <section className="space-y-4">
        <SectionTitle title="LLM 품질 / 안정성" description="생성 성공률·지연·토큰과 즉시 대응이 필요한 신호" />
        <Suspense fallback={<Skeleton className="h-[380px]" />}>
          <QualitySection />
        </Suspense>
      </section>
    </div>
  )
}
