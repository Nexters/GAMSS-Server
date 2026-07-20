export type MemberStatus = 'ACTIVE' | 'WITHDRAWN'

export interface Member {
  id: number
  email: string | null
  nickname: string | null
  status: MemberStatus
  createdAt: string
}

// 목(mock) 회원 데이터. 실제 /api/admin/members 연동 전까지 화면 검증용으로 사용한다.
const NICKNAMES = [
  '바다',
  '노을',
  '민들레',
  '고래',
  '별밤',
  '구름',
  '햇살',
  '단비',
  '숲길',
  '파랑',
  '초록',
  '가온',
  '나린',
  '보늬',
  '해솔',
  '슬기',
  '온새미',
  '도담',
  'windy',
  'calm',
]

function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`
}

export const MEMBERS: Member[] = Array.from({ length: 47 }, (_, i) => {
  const id = i + 1
  const withdrawn = id % 9 === 0
  const noEmail = id % 7 === 0 // 애플 로그인 등 이메일 미제공 케이스
  const noNickname = id % 5 === 0 // 닉네임 후설정 전 케이스
  const month = pad(((id * 3) % 6) + 1)
  const day = pad(((id * 7) % 27) + 1)
  return {
    id,
    email: noEmail ? null : `user${id}@example.com`,
    nickname: noNickname ? null : NICKNAMES[i % NICKNAMES.length],
    status: withdrawn ? 'WITHDRAWN' : 'ACTIVE',
    createdAt: `2026-${month}-${day}T${pad((id * 2) % 24)}:${pad((id * 13) % 60)}:00Z`,
  }
})
