export type MemberStatus = 'ACTIVE' | 'WITHDRAWN'

export interface Member {
  id: number
  email: string | null
  name: string | null
  nickname: string | null
  status: MemberStatus
  createdAt: string
}
