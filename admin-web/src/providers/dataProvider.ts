import type { DataProvider } from '@refinedev/core'
import { apiFetch } from '@/lib/api'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

// 백엔드 PageResponse 형태(/api/admin/members)
interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * 백오피스 데이터 프로바이더. /api/admin/** 를 관리자 토큰으로 호출한다.
 * 백오피스는 조회 전용이라 생성·수정·삭제는 지원하지 않는다.
 */
export const dataProvider: DataProvider = {
  getList: async ({ resource, pagination, filters }) => {
    const current = pagination?.current ?? 1
    const pageSize = pagination?.pageSize ?? 10

    const params = new URLSearchParams({
      page: String(current - 1),
      size: String(pageSize),
    })
    const search = filters?.find((f) => 'field' in f && f.field === 'q')
    if (search && typeof search.value === 'string' && search.value.trim()) {
      params.set('search', search.value.trim())
    }
    const status = filters?.find((f) => 'field' in f && f.field === 'status')
    if (status && typeof status.value === 'string' && status.value) {
      params.set('status', status.value)
    }

    const data = await apiFetch<PageResponse<unknown>>(`/api/admin/${resource}?${params.toString()}`)
    return { data: data.content as never, total: data.totalElements }
  },

  getOne: async ({ resource, id }) => {
    const data = await apiFetch<unknown>(`/api/admin/${resource}/${id}`)
    return { data: data as never }
  },

  // 회원 탈퇴 등 관리 액션에 사용한다(useCustomMutation → dataProvider.custom).
  custom: async ({ url, method, payload, headers }) => {
    const data = await apiFetch<unknown>(url, {
      method: (method ?? 'get').toUpperCase(),
      headers: payload ? { 'Content-Type': 'application/json', ...headers } : headers,
      body: payload ? JSON.stringify(payload) : undefined,
    })
    return { data: data as never }
  },

  create: async () => {
    throw new Error('백오피스는 생성을 지원하지 않습니다.')
  },
  update: async () => {
    throw new Error('백오피스는 수정을 지원하지 않습니다.')
  },
  deleteOne: async () => {
    throw new Error('백오피스는 삭제를 지원하지 않습니다.')
  },
  getApiUrl: () => BASE_URL,
}
