/**
 * Shared status/streak widgets. `RabojStreak` renders on-budget days as a
 * calendar-style grid of filled/empty squares; `Stamp` renders a status as a
 * mono label with a colored left-edge mark. Export names/props are kept
 * stable so callers don't need to change.
 */
import type { ReactNode } from "react"

export function RabojStreak({ count, className }: { count: number; className?: string }) {
  const maxFit = 31
  const safe = Math.max(Math.round(count) || 0, 0)
  const shown = Math.min(safe, maxFit)
  const cells = Array.from({ length: maxFit }, (_, i) => i < shown)

  return (
    <div
      className={`flex flex-wrap gap-1.5 ${className ?? ""}`}
      role="img"
      aria-label={`${safe} on-budget day${safe === 1 ? "" : "s"}`}
    >
      {cells.map((filled, i) => (
        <span
          key={i}
          className="size-[18px] flex-none border"
          style={{
            background: filled ? "#123945" : "transparent",
            borderColor: filled ? "#4C93A6" : "var(--border)",
          }}
        />
      ))}
      {safe > maxFit && (
        <span className="figure text-primary self-center pl-1 text-[11px]">+{safe - maxFit}</span>
      )}
    </div>
  )
}

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
