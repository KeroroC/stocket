import { apiRequest } from './http'

export interface DiagnosticCheck { status: 'OK' | 'WARN' | 'ERROR'; count: number; checkedAt: string; actionCode: string }
export interface DiagnosticsResponse { checks: Record<string, DiagnosticCheck> }
export const getDiagnostics = () => apiRequest<DiagnosticsResponse>('/api/v1/admin/diagnostics')
