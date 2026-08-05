/**
 * Argali brand mark + the app's icon set. One hand: the ram's-horn logo and a
 * house icon set drawn at a 1.7 stroke with round joins, matching the Ledger v6
 * comp. No emoji or Unicode glyphs stand in for these.
 */
import type { ReactNode } from "react"

/** The Argali ram's-horn logomark. `withEye` adds the punctuating dot. */
export function ArgaliMark({
  className,
  withEye = true,
  strokeWidth = 7,
}: {
  className?: string
  withEye?: boolean
  strokeWidth?: number
}) {
  return (
    <svg
      viewBox="-8 -13 116 116"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
      {withEye && <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />}
    </svg>
  )
}

/** The single-horn glyph used inline beside section headings. */
export function HornGlyph({ className }: { className?: string }) {
  return (
    <svg
      viewBox="30 0 60 70"
      fill="none"
      stroke="currentColor"
      strokeWidth={8}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
    </svg>
  )
}

export type NavIconName = "overview" | "expenses" | "reports" | "quests" | "account"

const PATHS: Record<NavIconName, ReactNode> = {
  overview: (
    <>
      <path d="M3 8.6 10 3.2l7 5.4V16a1.4 1.4 0 0 1-1.4 1.4H4.4A1.4 1.4 0 0 1 3 16z" />
      <path d="M7.6 17.4v-4.2a1.2 1.2 0 0 1 1.2-1.2h2.4a1.2 1.2 0 0 1 1.2 1.2v4.2" />
    </>
  ),
  expenses: (
    <>
      <path d="M5.2 2.8h9.6A1.4 1.4 0 0 1 16.2 4.2v13.4l-2.6-1.7-2.6 1.7-2.6-1.7-2.6 1.7V4.2A1.4 1.4 0 0 1 5.2 2.8z" />
      <path d="M7.6 7.4h4.8M7.6 10.8h3.2" />
    </>
  ),
  reports: (
    <>
      <path d="M3.4 16.6h13.2" />
      <path d="M6.2 16.6V9.4M10 16.6V4.8M13.8 16.6v-4.6" />
    </>
  ),
  quests: <path d="M10 2.9 12.2 7.4l5 .7-3.6 3.5.9 4.9L10 14.2l-4.5 2.3.9-4.9L2.8 8.1l5-.7z" />,
  account: (
    <>
      <circle cx="10" cy="7.2" r="3.1" />
      <path d="M4 17.1a6 6 0 0 1 12 0" />
    </>
  ),
}

/** Nav / affordance icons in the app's one consistent stroke. */
export function NavIcon({
  name,
  className,
  size = 20,
}: {
  name: NavIconName
  className?: string
  size?: number
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {PATHS[name]}
    </svg>
  )
}

/** Magnifier used in search fields — same hand as the nav set. */
export function SearchIcon({ className, size = 17 }: { className?: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <circle cx="9" cy="9" r="5.4" />
      <path d="m13.2 13.2 3.4 3.4" />
    </svg>
  )
}

/** Notification bell — same hand as the nav set. */
export function BellIcon({ className, size = 19 }: { className?: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M6.2 8.4a3.8 3.8 0 0 1 7.6 0c0 4 1.2 5 1.2 5H5s1.2-1 1.2-5Z" />
      <path d="M8.4 16a1.8 1.8 0 0 0 3.2 0" />
    </svg>
  )
}
