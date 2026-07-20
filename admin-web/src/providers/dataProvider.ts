import type { DataProvider } from '@refinedev/core'
import { MEMBERS, type Member } from '@/data/members'

// 목(mock) 데이터 프로바이더. 지금은 members 리소스만 인메모리로 제공한다.
// 실제 백엔드(/api/admin/**) 연동 시 @refinedev/simple-rest 등으로 교체한다.
const TABLES: Record<string, Member[]> = {
  members: MEMBERS,
}

function getTable(resource: string): Member[] {
  const table = TABLES[resource]
  if (!table) {
    throw new Error(`알 수 없는 리소스: ${resource}`)
  }
  return table
}

function matchesSearch(member: Member, keyword: string): boolean {
  const q = keyword.toLowerCase()
  return (
    String(member.id).includes(q) ||
    (member.email?.toLowerCase().includes(q) ?? false) ||
    (member.nickname?.toLowerCase().includes(q) ?? false)
  )
}

export const mockDataProvider: DataProvider = {
  getList: async ({ resource, pagination, filters }) => {
    let rows = [...getTable(resource)]

    const search = filters?.find((f) => 'field' in f && f.field === 'q')
    if (search && typeof search.value === 'string' && search.value.trim()) {
      rows = rows.filter((row) => matchesSearch(row, search.value.trim()))
    }

    const total = rows.length
    const current = pagination?.current ?? 1
    const pageSize = pagination?.pageSize ?? 10
    const start = (current - 1) * pageSize
    const data = rows.slice(start, start + pageSize)

    // 인메모리라 즉시지만, 실제 API 감을 주기 위한 약간의 지연
    await new Promise((r) => setTimeout(r, 150))
    return { data: data as never, total }
  },

  getOne: async ({ resource, id }) => {
    const row = getTable(resource).find((r) => String(r.id) === String(id))
    if (!row) {
      throw new Error(`${resource} 리소스에서 id=${id} 를 찾을 수 없습니다.`)
    }
    await new Promise((r) => setTimeout(r, 100))
    return { data: row as never }
  },

  create: async () => {
    throw new Error('목 프로바이더는 생성을 지원하지 않습니다.')
  },
  update: async () => {
    throw new Error('목 프로바이더는 수정을 지원하지 않습니다.')
  },
  deleteOne: async () => {
    throw new Error('목 프로바이더는 삭제를 지원하지 않습니다.')
  },
  getApiUrl: () => '',
}
