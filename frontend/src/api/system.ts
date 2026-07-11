export interface SystemStatus {
  name: string
  version: string
}

export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await fetch('/api/v1/system', {
    credentials: 'same-origin',
  })

  if (!response.ok) {
    throw new Error(`System API returned ${response.status}`)
  }

  return response.json() as Promise<SystemStatus>
}
