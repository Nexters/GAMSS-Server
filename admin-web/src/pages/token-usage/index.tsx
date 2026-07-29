import { PageHeader } from '@/components/page-header'
import { PolicySection } from './policy-section'
import { UsageTable } from './usage-table'

export function TokenUsagePage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="토큰 사용량"
        description="유저 일일 토큰 상한을 조절하고, 모든 대화방의 토큰 사용량을 확인합니다."
      />
      <PolicySection />
      <UsageTable />
    </div>
  )
}
