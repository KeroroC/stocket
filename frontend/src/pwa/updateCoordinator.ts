let pendingWrites: Promise<unknown> = Promise.resolve()

export function trackDraftWrite<T>(write: Promise<T>): Promise<T> {
  pendingWrites = write.catch(() => undefined)
  return write
}

export async function waitForDraftWrites(): Promise<void> {
  await pendingWrites
}
