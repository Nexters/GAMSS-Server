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
