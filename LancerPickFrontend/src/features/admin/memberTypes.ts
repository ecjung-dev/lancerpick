import type { MemberRole } from '../auth/types'

export type MemberRestriction = 'NONE' | 'GENERAL_RESTRICTION' | 'SECURITY_LOCK'
export type SanctionKind = Exclude<MemberRestriction, 'NONE'>

export interface AdminMemberSummary {
  id: string
  email: string
  displayName: string
  role: MemberRole
  restriction: MemberRestriction
  createdAt: string
}

export interface MemberSanction {
  id: string
  kind: SanctionKind
  state: 'ACTIVE' | 'EXPIRED' | 'REVOKED' | 'SCHEDULED'
  reason: string
  appliedBy: string
  startsAt: string
  endsAt: string
  revokedAt: string | null
  revokedBy: string | null
  revokeReason: string | null
  timerStopDeliveredAt: string | null
}

export interface AdminMemberDetail {
  member: AdminMemberSummary
  emailVerifiedAt: string | null
  serverTime: string
  canManage: boolean
  activeSanctions: MemberSanction[]
}

export interface AdminMemberFilter {
  q: string
  restriction: MemberRestriction | ''
}

export type MemberSanctionCommand =
  | {
      action: 'apply'
      kind: SanctionKind
      reason: string
      endsAt: string
      requestKey: string
    }
  | { action: 'revoke'; sanctionId: string; reason: string }
