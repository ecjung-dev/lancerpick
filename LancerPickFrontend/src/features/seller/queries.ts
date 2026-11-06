import { getMySellerProfile, getPublicSellerProfile, getSellerSession } from './api'
import { useQuery } from '@tanstack/react-query'

export function usePublicSellerProfile(id: string) {
  return useQuery({
    queryKey: ['public-seller-profile', id],
    queryFn: ({ signal }) => getPublicSellerProfile(id, signal),
    retry: false,
  })
}

export function useSellerSession() {
  return useQuery({
    queryKey: ['seller-profile-session'],
    queryFn: ({ signal }) => getSellerSession(signal),
    staleTime: 0,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: 'always',
  })
}

export function useSellerProfile(memberId: string) {
  return useQuery({
    queryKey: ['seller-profile', memberId],
    queryFn: ({ signal }) => getMySellerProfile(signal),
    gcTime: 0,
    staleTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })
}
