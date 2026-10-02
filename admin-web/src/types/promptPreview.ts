export interface PreviewComment {
  characterId: string
  text: string
}

export interface PreviewTikitaka {
  characterId: string
  replyTo: string
  text: string
}

export interface PreviewResult {
  model: string
  systemPrompt: string
  userContent: string
  characters: string[]
  tikitakaCount: number
  eongttungTopic: string | null
  comments: PreviewComment[] | null
  tikitaka: PreviewTikitaka[] | null
  validationError: string | null
  generationError: string | null
  usedTokens: number
  cachedTokens: number
  inputTokens: number
  outputTokens: number
  estimatedCostUsd: number
  latencyMs: number
}

export interface ReplyPreviewResult {
  model: string
  systemPrompt: string
  userContent: string
  character: string
  replyText: string | null
  validationError: string | null
  generationError: string | null
  usedTokens: number
  cachedTokens: number
  inputTokens: number
  outputTokens: number
  estimatedCostUsd: number
  latencyMs: number
}

export interface CardPreviewResult {
  model: string
  systemPrompt: string
  userContent: string
  /** 실제 카드에 붙을 대표 감정. 판정이 NONSENSE면 요청한 감정과 달리 QUIRKY. */
  emotion: string
  /** 카드 한 줄 판정. 판정 호출이 실패하면 null. */
  kind: 'EVENT' | 'NONSENSE' | null
  /** 실제 저장될 한 줄. 생성 실패 시 null. */
  line: string | null
  length: number | null
  /** 다듬기 전 한 줄. EVENT면 LLM이 돌려준 원문, NONSENSE면 유저가 보낸 첫 메시지다. 길이 초과 여부를 보는 용도. */
  rawLine: string | null
  rawLength: number | null
  truncated: boolean
  generationError: string | null
  usedTokens: number
  cachedTokens: number
  inputTokens: number
  outputTokens: number
  estimatedCostUsd: number
  latencyMs: number
}
