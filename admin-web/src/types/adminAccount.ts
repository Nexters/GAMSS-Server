export interface AdminAccount {
  id: number | null
  email: string
  source: 'ENV' | 'DB'
  removable: boolean
  addedByEmail: string | null
  createdAt: string | null
}
