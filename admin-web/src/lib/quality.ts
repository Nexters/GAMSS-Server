export type Emotion = 'JOY' | 'SADNESS' | 'ANGER' | 'ANXIETY' | 'GRUMPY' | 'QUIRKY'
export type Verdict = 'AWKWARD' | 'NATURAL' | 'UNSURE'
export type AwkwardType = 'OFF_CONTEXT' | 'BROKEN_FLOW' | 'REPETITIVE' | 'UNNATURAL_TONE' | 'PERSONA_BREAK' | 'USER_CONFUSED' | 'OTHER'
export type ResponseKind = 'COMMENT' | 'REPLY'

export const CHARACTER_META: Record<Emotion, { label: string; emoji: string; color: string }> = {
  JOY: { label: '기쁨', emoji: '😊', color: '#f59e0b' },
  SADNESS: { label: '슬픔', emoji: '😢', color: '#3b82f6' },
  ANGER: { label: '분노', emoji: '😡', color: '#ef4444' },
  ANXIETY: { label: '불안', emoji: '😰', color: '#8b5cf6' },
  GRUMPY: { label: '까칠', emoji: '😤', color: '#64748b' },
  QUIRKY: { label: '엉뚱', emoji: '🤪', color: '#10b981' },
}

export const EMOTIONS = Object.keys(CHARACTER_META) as Emotion[]

export const VERDICT_META: Record<Verdict, { label: string; variant: 'destructive' | 'success' | 'muted'; key: string }> = {
  AWKWARD: { label: '어색함', variant: 'destructive', key: '1' },
  NATURAL: { label: '자연스러움', variant: 'success', key: '2' },
  UNSURE: { label: '애매함', variant: 'muted', key: '3' },
}

export const AWKWARD_TYPE_META: Record<AwkwardType, { label: string; hint: string }> = {
  OFF_CONTEXT: { label: '동문서답', hint: '사용자가 한 말과 맞지 않는 반응. 쓴 적 없는 일을 지어내는 것도 포함' },
  BROKEN_FLOW: { label: '흐름 끊김', hint: '다른 캐릭터나 사용자 답장에 답하면서 그 말에 반응하지 않음' },
  REPETITIVE: { label: '반복', hint: '다른 캐릭터나 앞선 발화와 거의 같은 말' },
  UNNATURAL_TONE: { label: '말투 이상', hint: '존댓말 섞임, 번역투, 상담사 같은 말, AI 같은 밈' },
  PERSONA_BREAK: { label: '캐릭터 붕괴', hint: '캐릭터 성격이나 말투와 맞지 않음. 사용자를 캐릭터 이름이나 "우리"로 부르는 것도 포함' },
  USER_CONFUSED: { label: '사용자가 어색해함', hint: '다음 사용자 말이 이 응답 때문에 혼란이나 거부로 읽힘' },
  OTHER: { label: '기타', hint: '위에 없는 어색함' },
}

export const AWKWARD_TYPES = Object.keys(AWKWARD_TYPE_META) as AwkwardType[]

export const KIND_LABEL: Record<ResponseKind, string> = { COMMENT: '댓글', REPLY: '답글' }

export interface AiVerdictView {
  verdict: Verdict
  awkwardTypes: AwkwardType[]
  evidence: string | null
  reason: string | null
  judgeModel: string
  judgePromptVersion: number
  createdAt: string
}

export interface HumanVerdictView {
  verdict: Verdict
  awkwardTypes: AwkwardType[]
  memo: string | null
  updatedAt: string
}

export interface ResponseInfo {
  kind: ResponseKind
  triggerMessageId: number
  model: string | null
  commonPromptVersion: number | null
  typePromptVersion: number | null
  aiVerdict: AiVerdictView | null
  aiHidden: boolean
  myVerdict: HumanVerdictView | null
  humanVerdictCount: number
  humanAwkwardCount: number
}

export interface ThreadMessage {
  id: number
  senderType: 'USER' | 'CHARACTER'
  emotionType: Emotion | null
  content: string
  repliesToMessageId: number | null
  rootMessageId: number | null
  createdAt: string
  response: ResponseInfo | null
}

export interface ThreadView {
  conversationId: number
  memberId: number
  title: string | null
  status: 'ACTIVE' | 'ENDED' | 'DELETED'
  summary: string | null
  createdAt: string
  messages: ThreadMessage[]
}

export interface ResponseRow {
  messageId: number
  conversationId: number
  emotionType: Emotion
  kind: ResponseKind
  content: string
  createdAt: string
  model: string | null
  typePromptVersion: number | null
  aiVerdict: AiVerdictView | null
  myVerdict: HumanVerdictView | null
  humanVerdictCount: number
}

export interface HumanVerdictResult {
  messageId: number
  myVerdict: HumanVerdictView
  aiVerdict: AiVerdictView | null
  agreesWithAi: boolean | null
  mineCount: number
}

export interface LabelingNext {
  messageId: number | null
  conversationId: number | null
  mineCount: number
  target: number
}

/** 실험실을 이 응답의 입력으로 채워 열 때 넘기는 값. 라우터 state 로 전달한다. */
export interface PlaygroundInput {
  kind: ResponseKind
  diary: string
  summary: string | null
  characters: Emotion[]
  tikitakaCount: number
  character: Emotion | null
  characterComment: string | null
  userReply: string | null
}

/** 티키타카를 답장 대상 바로 뒤에 둔다(앱과 같은 흐름). */
export function orderThread(messages: ThreadMessage[]): ThreadMessage[] {
  const byId = new Map(messages.map((m) => [m.id, m]))
  const result: ThreadMessage[] = []
  const placed = new Set<number>()
  const childrenOf = (id: number) =>
    messages.filter((m) => m.senderType === 'CHARACTER' && m.repliesToMessageId === id && byId.get(id)?.senderType === 'CHARACTER')
  const place = (m: ThreadMessage) => {
    if (placed.has(m.id)) {
      return
    }
    placed.add(m.id)
    result.push(m)
    childrenOf(m.id).forEach(place)
  }
  messages.forEach(place)
  return result
}
