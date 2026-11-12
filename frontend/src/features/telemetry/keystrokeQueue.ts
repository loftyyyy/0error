import type { KeystrokeEvent } from '../typing/typingEngine'

/**
 * Durable outbox for telemetry batches. IndexedDB is the primary store; an
 * in-memory fallback keeps typing usable in private modes where IndexedDB fails.
 */
export interface KeystrokeBatch {
  id?: number
  batchId: string
  sessionId: string
  createdAt: number
  attempts: number
  events: KeystrokeEvent[]
}

const DB_NAME = '0error-telemetry'
const DB_VERSION = 1
const STORE = 'keystroke_batches'

let dbPromise: Promise<IDBDatabase> | null = null
let memoryMode = typeof indexedDB === 'undefined'
const memoryBatches: KeystrokeBatch[] = []
let memoryId = 1

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise

  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: 'id', autoIncrement: true })
        store.createIndex('createdAt', 'createdAt')
        store.createIndex('sessionId', 'sessionId')
      }
    }

    request.onsuccess = () => {
      const db = request.result
      db.onversionchange = () => db.close()
      resolve(db)
    }

    request.onerror = () => reject(request.error ?? new Error('IndexedDB open failed'))
  })

  dbPromise.catch(() => {
    memoryMode = true
  })

  return dbPromise
}

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('IndexedDB request failed'))
  })
}

function transactionDone(transaction: IDBTransaction): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error ?? new Error('IndexedDB transaction failed'))
    transaction.onabort = () => reject(transaction.error ?? new Error('IndexedDB transaction aborted'))
  })
}

export async function enqueueBatch(
  batch: Omit<KeystrokeBatch, 'id' | 'attempts'>,
): Promise<void> {
  if (!memoryMode) {
    try {
      const db = await openDb()
      const transaction = db.transaction(STORE, 'readwrite')
      transaction.objectStore(STORE).add({ ...batch, attempts: 0 })
      await transactionDone(transaction)
      return
    } catch {
      memoryMode = true
    }
  }

  memoryBatches.push({ ...batch, id: memoryId, attempts: 0 })
  memoryId += 1
}

export async function readOldestBatch(): Promise<KeystrokeBatch | null> {
  if (!memoryMode) {
    try {
      const db = await openDb()
      const transaction = db.transaction(STORE, 'readonly')
      const store = transaction.objectStore(STORE)

      const batch = await new Promise<KeystrokeBatch | null>((resolve, reject) => {
        const request = store.index('createdAt').openCursor()
        request.onsuccess = () => {
          const cursor = request.result
          resolve(cursor ? (cursor.value as KeystrokeBatch) : null)
        }
        request.onerror = () => reject(request.error ?? new Error('IndexedDB cursor failed'))
      })

      await transactionDone(transaction)
      return batch
    } catch {
      memoryMode = true
    }
  }

  memoryBatches.sort((a, b) => a.createdAt - b.createdAt)
  return memoryBatches[0] ?? null
}

export async function deleteBatch(id: number): Promise<void> {
  if (!memoryMode) {
    try {
      const db = await openDb()
      const transaction = db.transaction(STORE, 'readwrite')
      transaction.objectStore(STORE).delete(id)
      await transactionDone(transaction)
      return
    } catch {
      memoryMode = true
    }
  }

  const index = memoryBatches.findIndex((batch) => batch.id === id)
  if (index >= 0) memoryBatches.splice(index, 1)
}

/** Number of queued batches for one session, used to prove telemetry landed. */
export async function countPendingBatches(sessionId: string): Promise<number> {
  if (!memoryMode) {
    try {
      const db = await openDb()
      const transaction = db.transaction(STORE, 'readonly')
      const store = transaction.objectStore(STORE)
      const count = await requestToPromise(store.index('sessionId').count(sessionId))
      await transactionDone(transaction)
      return count
    } catch {
      memoryMode = true
    }
  }

  return memoryBatches.filter((batch) => batch.sessionId === sessionId).length
}

export async function markBatchAttempt(id: number): Promise<void> {
  if (memoryMode) {
    const batch = memoryBatches.find((candidate) => candidate.id === id)
    if (batch) batch.attempts += 1
    return
  }

  try {
    const db = await openDb()
    const transaction = db.transaction(STORE, 'readwrite')
    const store = transaction.objectStore(STORE)
    const existing = await requestToPromise(store.get(id))
    if (existing) {
      const batch = existing as KeystrokeBatch
      batch.attempts += 1
      store.put(batch)
    }
    await transactionDone(transaction)
  } catch {
    memoryMode = true
  }
}
