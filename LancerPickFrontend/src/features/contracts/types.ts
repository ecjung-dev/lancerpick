export interface ContractDocument {
  id: string
  version: number
  status: 'DRAFT' | 'GENERATING' | 'FAILED' | 'READY' | 'SIGNED' | 'SUPERSEDED'
  specialTerms: string
  canonicalContent: string
  contentHash: string
  fileHash: string | null
  signatures: { memberId: string; role: string; signedAt: string }[]
}
