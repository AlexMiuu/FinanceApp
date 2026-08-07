/**
 * The single registry of every browser-storage key Argali writes (D9).
 *
 * Rules this module exists to enforce:
 *  - **Namespaced.** Every key lives under the `argali:` prefix, so clearing our
 *    own state can never touch another app sharing the origin.
 *  - **Regenerable only.** If losing a key would lose user data, it belongs in a
 *    service instead. The dashboard widget layout is the worked example: it is a
 *    choice nothing can regenerate, so it lives in User Service, not here.
 *  - **Bounded.** Every entry declares a cap and an eviction policy below. Nothing
 *    in browser storage is allowed to grow with usage forever.
 *  - **Enumerable.** No module calls `localStorage`/`sessionStorage` directly; they
 *    go through this file. That is what makes the M8 data inventory honest — you
 *    cannot document client storage you cannot list.
 *  - **Clearable.** `clearArgaliStorage()` wipes the lot, and it runs on sign-out
 *    *and* on account deletion.
 */

const PREFIX = "argali:"

type StorageArea = "local" | "session"

type StorageEntry = {
  /** Key suffix; the full key is `argali:<name>` (plus `:<userId>` when perUser). */
  name: string
  area: StorageArea
  /** Per-user keys are suffixed with the user id so two accounts never share a cache. */
  perUser: boolean
  /** Max entries held inside the stored value. `1` means a scalar, not a collection. */
  cap: number
  /** How the cap is enforced once reached. */
  eviction: string
  /** Why this is safe to lose — every entry must have an answer. */
  regenerableBecause: string
}

export const STORAGE_REGISTRY = {
  categoryUsage: {
    name: "category-usage",
    area: "local",
    perUser: true,
    cap: 40,
    eviction: "least-used first; ties broken by dropping the entry that is not the last-used one",
    regenerableBecause:
      "only ranks the category chips in the add-expense sheet; the categories themselves live in Expense Service",
  },
  splashSeen: {
    name: "splash-seen",
    area: "session",
    perUser: false,
    cap: 1,
    eviction: "scalar flag, overwritten in place; the browser drops it when the tab session ends",
    regenerableBecause: "only suppresses a repeat of the boot animation within one tab session",
  },
} as const satisfies Record<string, StorageEntry>

type RegisteredEntry = (typeof STORAGE_REGISTRY)[keyof typeof STORAGE_REGISTRY]

function area(entry: RegisteredEntry): Storage | null {
  try {
    return entry.area === "local" ? window.localStorage : window.sessionStorage
  } catch {
    // Storage can throw outright when the browser blocks it (Safari private mode).
    return null
  }
}

function fullKey(entry: RegisteredEntry, userId?: string): string {
  if (!entry.perUser) return PREFIX + entry.name
  if (!userId) throw new Error(`Storage key ${entry.name} is per-user but no user id was given`)
  return `${PREFIX}${entry.name}:${userId}`
}

/** Returns `fallback` when the key is absent, unreadable, or holds corrupted JSON. */
export function readStored<T>(entry: RegisteredEntry, fallback: T, userId?: string): T {
  const store = area(entry)
  if (!store) return fallback
  try {
    const raw = store.getItem(fullKey(entry, userId))
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

/** Writes are best-effort: a full or blocked quota must never break a user action. */
export function writeStored<T>(entry: RegisteredEntry, value: T, userId?: string): void {
  const store = area(entry)
  if (!store) return
  try {
    store.setItem(fullKey(entry, userId), JSON.stringify(value))
  } catch {
    /* quota exceeded or storage blocked — the cached value simply does not update */
  }
}

/**
 * Removes every `argali:`-prefixed key from both storage areas. Called on sign-out
 * and on account deletion, so one user's cached state is never visible to the next
 * person to sign in on the same browser.
 *
 * Deliberately prefix-scoped rather than `Storage.clear()`: this origin is not
 * guaranteed to be ours alone, and wiping a neighbour's keys is not ours to do.
 */
export function clearArgaliStorage(): void {
  for (const store of storageAreas()) {
    const doomed: string[] = []
    for (let i = 0; i < store.length; i++) {
      const key = store.key(i)
      if (key !== null && key.startsWith(PREFIX)) doomed.push(key)
    }
    // Collected first: removing during iteration reindexes the store underneath us.
    for (const key of doomed) store.removeItem(key)
  }
}

function storageAreas(): Storage[] {
  try {
    return [window.localStorage, window.sessionStorage]
  } catch {
    return []
  }
}
