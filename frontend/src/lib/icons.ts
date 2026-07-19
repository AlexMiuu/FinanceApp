import type { Category } from "@/lib/api"

const TOP_LEVEL_ICONS: Record<string, string> = {
  Housing: "🏠",
  Food: "🛒",
  Transport: "⛽",
  Health: "💊",
  Entertainment: "🎬",
  Shopping: "🛍️",
}

/** Emoji for an expense row, keyed by its top-level category name. */
export function categoryIcon(categories: Category[], categoryId: string): string {
  const category = categories.find((c) => c.id === categoryId)
  if (!category) return "💳"
  const top = category.parentId
    ? categories.find((c) => c.id === category.parentId)?.name
    : category.name
  return TOP_LEVEL_ICONS[top ?? ""] ?? "💳"
}
