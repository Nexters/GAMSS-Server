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
  /** NONSENSE일 때 한 줄로 그대로 쓴 엉뚱이 소재. EVENT거나 판정이 실패하면 null. */
  eongttungTopic: string | null
  /** 실제 저장될 한 줄. 생성 실패 시 null. */
  line: string | null
  length: number | null
  /** LLM이 그대로 돌려준 값(자르기 전). 프롬프트가 길이 지시를 지키는지 보는 용도. */
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
