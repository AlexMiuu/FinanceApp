// Pure rearrangement logic for the dashboard widget grid. Kept out of the
// component so the index arithmetic — the part most likely to be subtly wrong —
// can be exercised without a DOM.

import type { WidgetId } from "@/lib/api"

export type Column = "main" | "side"
export type Layout = Record<Column, WidgetId[]>

/** Mirrors DashboardLayoutService's default so the first paint matches what the server sends. */
export const DEFAULT_LAYOUT: Layout = {
  main: ["balance", "breakdown"],
  side: ["savings", "streak", "quests"],
}

export const OTHER: Record<Column, Column> = { main: "side", side: "main" }

export const WIDGET_NAMES: Record<WidgetId, string> = {
  balance: "Balance carried forward",
  breakdown: "Where your money goes",
  savings: "Savings",
  streak: "Streak",
  quests: "Active quests",
}

export const COLUMN_NAMES: Record<Column, string> = {
  main: "the wide column",
  side: "the narrow column",
}

/** Moves a widget within its own column. Returns the layout unchanged at either end. */
export function reorder(layout: Layout, column: Column, index: number, delta: number): Layout {
  const list = layout[column]
  const target = index + delta
  if (target < 0 || target >= list.length) return layout

  const next = [...list]
  const [widget] = next.splice(index, 1)
  next.splice(target, 0, widget)
  return { ...layout, [column]: next }
}

/** Sends a widget to the end of the other column. A column is allowed to end up empty. */
export function moveToOtherColumn(layout: Layout, column: Column, index: number): Layout {
  const from = [...layout[column]]
  const [widget] = from.splice(index, 1)
  return {
    ...layout,
    [column]: from,
    [OTHER[column]]: [...layout[OTHER[column]], widget],
  } as Layout
}

/**
 * Drops the dragged widget at `index` of `column`, pulling it out of wherever it
 * came from. Within one column, removing the widget first shifts every later
 * position down by one, so a downward move has to compensate.
 */
export function dropAt(
  layout: Layout,
  from: { column: Column; index: number },
  column: Column,
  index: number
): Layout {
  const next: Layout = { main: [...layout.main], side: [...layout.side] }
  const [widget] = next[from.column].splice(from.index, 1)
  const at = from.column === column && from.index < index ? index - 1 : index
  next[column].splice(at, 0, widget)
  return next
}
