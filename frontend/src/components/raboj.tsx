/**
 * Shared status marker. `Stamp` renders a status as a mono label with a colored
 * left-edge mark — the account book's way of flagging a row, never a rounded chip.
 *
 * The streak grid that used to live here went with the Overview's Streak widget;
 * the Quests tab draws its own month grid and year heatmap from the goal calendar.
 */
import type { ReactNode } from "react"

type StampTone = "brass" | "good" | "over" | "muted"
const STAMP_EDGE: Record<StampTone, string> = {
  brass: "edge-mark-accent",
  good: "edge-mark-good",
  over: "edge-mark-bad",
  muted: "",
}
const STAMP_COLOR: Record<StampTone, string> = {
  brass: "text-primary",
  good: "text-good",
  over: "text-destructive",
  muted: "text-muted-foreground",
}

/** A status read as a mono label plus a colored left-edge mark. */
export function Stamp({
  tone = "brass",
  children,
  className,
}: {
  tone?: StampTone
  children: ReactNode
  className?: string
}) {
  return (
    <span
      className={`status-tag inline-flex items-center py-0.5 pl-2.5 ${STAMP_EDGE[tone]} ${STAMP_COLOR[tone]} ${className ?? ""}`}
    >
      {children}
    </span>
  )
}
