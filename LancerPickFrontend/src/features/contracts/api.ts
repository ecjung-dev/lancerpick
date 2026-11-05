import { apiRequest, apiPdfRequest } from '../../shared/api/client.ts'
import type { ContractDocument } from './types.ts'

export const acceptContractOrder = (id: string) =>
  apiRequest(`${path(id)}/accept`, { method: 'POST' })
export const prepareContract = (
  id: string,
  expectedVersion: number,
  specialTerms: string,
) =>
  apiRequest<ContractDocument>(`${path(id)}/draft`, {
    method: 'POST',
    json: { expectedVersion, specialTerms },
  })
export const signContract = (id: string, document: ContractDocument) =>
  apiRequest<ContractDocument>(`${path(id, document.version)}/sign`, {
    method: 'POST',
    json: {
      version: document.version,
      contentHash: document.contentHash,
      fileHash: document.fileHash,
      agreed: true,
    },
  })

function path(id: string, version?: number) {
  if (!/^[1-9][0-9]*$/.test(id) || BigInt(id) > 9223372036854775807n)
    throw new Error('주문번호를 확인해 주세요.')
  if (
    version !== undefined &&
    (!Number.isInteger(version) || version < 1 || version > 2147483647)
  )
    throw new Error('계약 버전을 확인해 주세요.')
  return `/orders/${id}/contract`
}
export const getContract = (
  id: string,
  version?: number,
  signal?: AbortSignal,
) =>
  apiRequest<ContractDocument>(
    `${path(id, version)}${version === undefined ? '' : `?version=${version}`}`,
    { signal, cache: 'no-store' },
  )
export const generateContract = (id: string, version: number) =>
  apiRequest<ContractDocument>(`${path(id, version)}/generate`, {
    method: 'POST',
    json: { version },
  })
export const downloadContract = (
  id: string,
  version: number,
  signal?: AbortSignal,
) => apiPdfRequest(`${path(id, version)}/file?version=${version}`, signal)

export async function verifyContractPdf(blob: Blob, expectedHash: string) {
  if (!/^[0-9a-f]{64}$/.test(expectedHash))
    throw new Error('계약서 해시를 확인할 수 없습니다.')
  const actual = Array.from(
    new Uint8Array(
      await crypto.subtle.digest('SHA-256', await blob.arrayBuffer()),
    ),
    (b) => b.toString(16).padStart(2, '0'),
  ).join('')
  if (actual !== expectedHash)
    throw new Error('계약서 파일이 변경되었습니다. 상태를 다시 확인해 주세요.')
}
