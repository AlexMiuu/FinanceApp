// Suggestions for the add-expense sheet, derived from the user's own history.
//
// Nothing here infers anything from other people, and nothing is fabricated: a
// suggestion is only ever a purchase this user has already recorded. Kept pure and
// DOM-free so `npm run logic-check` can exercise it.

import type { Expense } from "@/lib/api"

/** A previously recorded purchase, offered for one-tap repeat. */
export type RepeatSuggestion = {
  /** The id of the expense this was drawn from — a stable React key. */
  id: string
  note: string
  /** Bani, as stored. */
  amount: number
  categoryId: string
}

/**
 * The comparison form of a description. Case and stray whitespace are noise —
 * "Mega Image", "mega image" and "Mega  Image " are the same shop.
 */
export function normaliseNote(note: string): string {
  return note.trim().toLowerCase().replace(/\s+/g, " ")
}

/**
 * The most recent distinct purchases, newest first.
 *
 * Distinct means same description, amount and category together: the same weekly
 * shop logged four times is one suggestion, not four, but the same shop at a
 * different amount is genuinely a different thing to repeat.
 *
 * Entries with no description are skipped — a chip reading only "148,20" tells
 * you nothing about what it was, so it cannot be confirmed at a glance.
 *
 * `expenses` is expected newest-first, which is the order the API returns them
 * (`ExpenseService` sorts by expenseDate then createdAt, both descending).
 */
export function recentRepeats(expenses: Expense[], limit: number): RepeatSuggestion[] {
  const seen = new Set<string>()
  const out: RepeatSuggestion[] = []

  for (const expense of expenses) {
    if (out.length >= limit) break

    const note = expense.note?.trim()
    if (!note) continue

    const key = `${normaliseNote(note)}|${expense.amount}|${expense.categoryId}`
    if (seen.has(key)) continue

    seen.add(key)
    out.push({ id: expense.id, note, amount: expense.amount, categoryId: expense.categoryId })
  }

  return out
}

/**
 * The category this description was last filed under, or null when it is new.
 *
 * Returns the *most recent* match rather than the most frequent one: when someone
 * refiles a shop into a different category, the newer decision is the one they
 * meant. Only ever a suggestion — the caller must leave it overridable.
 */
export function categoryForNote(expenses: Expense[], note: string): string | null {
  const wanted = normaliseNote(note)
  if (!wanted) return null

  for (const expense of expenses) {
    if (expense.note && normaliseNote(expense.note) === wanted) return expense.categoryId
  }
  return null
}
