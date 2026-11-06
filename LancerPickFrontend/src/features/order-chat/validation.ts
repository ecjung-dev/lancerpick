import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { SendChatMessageInput } from './types.ts'

export const MAX_MESSAGE_LENGTH = 2000
const uuidPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function prepareChatMessage(
  input: SendChatMessageInput,
): SendChatMessageInput {
  const content = normalizeText(input.content)
  if (
    !content ||
    codePointLength(content) > MAX_MESSAGE_LENGTH ||
    !uuidPattern.test(input.clientMessageId)
  ) {
    throw new Error('메시지는 1~2,000자로 입력해 주세요.')
  }
  return { clientMessageId: input.clientMessageId.toLowerCase(), content }
}

export function validChatSequence(value: string, allowZero = false) {
  return (
    /^[0-9]+$/.test(value) &&
    BigInt(value) >= (allowZero ? 0n : 1n) &&
    BigInt(value) <= 9223372036854775807n
  )
}

export function chatStatusAllowsSend(status: string) {
  return [
    'REQUESTED',
    'AWAITING_CONTRACT',
    'AWAITING_PAYMENT',
    'IN_PROGRESS',
    'AWAITING_CONFIRMATION',
    'REVISION_IN_PROGRESS',
    'AWAITING_BALANCE',
  ].includes(status)
}
