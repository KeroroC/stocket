export type ReceiveWizardKind =
  | 'IDENTIFY'
  | 'MATCH'
  | 'DETAILS'
  | 'CONFIRM'
  | 'SUBMITTING'
  | 'CONFLICT'
  | 'COMPLETED'

export interface ReceiveWizardState {
  kind: ReceiveWizardKind
  error?: string
  conflicts?: string[]
  entryId?: string
}
