export type TokenLedgerType = 'PURCHASE' | 'CONSUME' | 'REFUND' | 'REFUND_REVERSED' | 'ADMIN_GRANT' | 'ADMIN_REVOKE'

export interface TokenLedgerEntry {
  id: number
  type: TokenLedgerType
  amount: number
  balanceAfter: number
  adminEmail: string | null
  reason: string | null
  createdAt: string
}

export interface PaidTokens {
  balance: number
  ledger: {
    content: TokenLedgerEntry[]
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
}
