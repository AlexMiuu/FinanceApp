// Client-side memory of category usage so the add-expense form can offer
// one-tap chips and preselect the last-used category. Per-user, localStorage.

type Usage = { counts: Record<string, number>; last: string | null }

const key = (userId: string) => `pf-category-usage-${userId}`

function load(userId: string): Usage {
  try {
    const raw = localStorage.getItem(key(userId))
    if (raw) return JSON.parse(raw) as Usage
  } catch {
    /* corrupted storage -> reset */
  }
  return { counts: {}, last: null }
}

export function recordCategoryUse(userId: string, categoryId: string) {
  const usage = load(userId)
  usage.counts[categoryId] = (usage.counts[categoryId] ?? 0) + 1
  usage.last = categoryId
  try {
    localStorage.setItem(key(userId), JSON.stringify(usage))
  } catch {
    /* storage full — chips just won't update */
  }
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
