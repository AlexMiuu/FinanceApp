// Client-side memory of category usage so the add-expense form can offer
// one-tap chips and preselect the last-used category. Per-user, and bounded:
// see STORAGE_REGISTRY.categoryUsage for the key, cap and eviction policy.

import { STORAGE_REGISTRY, readStored, writeStored } from "@/lib/storage"

type Usage = { counts: Record<string, number>; last: string | null }

const entry = STORAGE_REGISTRY.categoryUsage

/**
 * Always returns a fresh object. Callers mutate what they get back, so handing
 * out a shared fallback would let one user's counts bleed into the next user's
 * on the same device — the fallback would be mutated in place and outlive them.
 */
function load(userId: string): Usage {
  const usage = readStored<Usage>(entry, { counts: {}, last: null }, userId)
  // A hand-edited or older value can be missing `counts` entirely.
  return { counts: { ...usage.counts }, last: usage.last ?? null }
}

/**
 * Enforces the registry cap. Categories are user-created and unbounded in number,
 * so without this the record grows by one key on every first use of a new category
 * and is never trimmed. Least-used entries go first; the last-used category is kept
 * regardless, since dropping it would break the form's preselection.
 */
function evictToCap(usage: Usage): Usage {
  const ids = Object.keys(usage.counts)
  if (ids.length <= entry.cap) return usage

  const kept = ids
    .sort((a, b) => {
      if (a === usage.last) return -1
      if (b === usage.last) return 1
      return usage.counts[b] - usage.counts[a]
    })
    .slice(0, entry.cap)

  const counts: Record<string, number> = {}
  for (const id of kept) counts[id] = usage.counts[id]
  return { counts, last: usage.last }
}

export function recordCategoryUse(userId: string, categoryId: string) {
  const usage = load(userId)
  usage.counts[categoryId] = (usage.counts[categoryId] ?? 0) + 1
  usage.last = categoryId
  writeStored(entry, evictToCap(usage), userId)
}

export function lastUsedCategory(userId: string): string | null {
  return load(userId).last
}

/** Top N used category ids, most-used first. */
export function topCategories(userId: string, n: number): string[] {
  return Object.entries(load(userId).counts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map(([id]) => id)
}
