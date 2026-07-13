import { ref } from 'vue'

function createKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `inventory-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function useInventoryCommands() {
  const idempotencyKey = ref(createKey())
  const attempted = ref(false)

  function changed() {
    if (attempted.value) {
      idempotencyKey.value = createKey()
      attempted.value = false
    }
  }

  async function execute<T>(command: (key: string) => Promise<T>): Promise<T> {
    attempted.value = true
    return command(idempotencyKey.value)
  }

  function reset() {
    idempotencyKey.value = createKey()
    attempted.value = false
  }

  return { idempotencyKey, changed, execute, reset }
}
