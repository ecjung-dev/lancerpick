/** keyCode 229 also protects browsers that end IME composition before keydown. */
export function chatEnterAction(event: {
  key: string; altKey: boolean; isComposing: boolean; keyCode: number; repeat: boolean
}): 'send' | 'newline' | 'ignore' {
  if (event.key !== 'Enter' || event.isComposing || event.keyCode === 229) return 'ignore'
  if (event.altKey) return 'newline'
  return event.repeat ? 'ignore' : 'send'
}
