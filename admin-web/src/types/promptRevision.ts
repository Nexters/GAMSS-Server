export interface PromptRevision {
  id: number
  promptType: string
  version: number
  savedBy: string | null
  restoredFromVersion: number | null
  createdAt: string
  length: number
  preview: string
}

export interface PromptRevisionDetail {
  id: number
  promptType: string
  version: number
  savedBy: string | null
  restoredFromVersion: number | null
  createdAt: string
  systemPrompt: string
}

export interface PromptRevisionPage {
  content: PromptRevision[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
