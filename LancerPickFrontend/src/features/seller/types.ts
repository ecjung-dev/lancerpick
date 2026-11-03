export interface SellerProfile {
  id: string
  displayName: string
  introduction: string
  specialty: string
  portfolioUrl: string | null
}

export interface SellerProfileInput {
  introduction: string
  specialty: string
  portfolioUrl: string | null
}

export interface SellerSession { id: string; displayName: string }
