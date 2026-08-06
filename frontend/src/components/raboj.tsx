/**
 * The răboj — a Carpathian shepherd's notched tally stick. Each on-budget day is
 * a notch carved into the rail; notches bundle in fives (four cuts crossed by a
 * fifth), exactly as a cioban tallied sheep. This is the app's signature counter:
 * a streak is a physical object, not a progress bar.
 */
import type { ReactNode } from "react"
export function RabojStreak({ count, className }: { count: number; className?: string }) {
  // Fixed-width viewBox so the stick always fills its container (width:100%);
  // notches are left-packed and the rest of the stick shows faint guide marks —
  // a real tally stick with room to grow, never a stub floating in the card.
  const VW = 340
  const H = 58
  const pad = 18
  const top = 12
  const bot = 46
  const gap = 13
  const bundleGap = 11
  const maxFit = 20

  const safe = Math.max(Math.round(count) || 0, 0)
  const shown = Math.min(safe, maxFit)

  // Positions for every slot (real + ghost) at consistent spacing.
  const xs: number[] = []
  let x = pad
  for (let i = 0; i < maxFit; i++) {
    xs.push(x)
    x += gap
    if ((i + 1) % 5 === 0) x += bundleGap
  }

  // A diagonal slash closes each completed bundle of five real notches.
  const slashes: [number, number][] = []
  for (let g = 0; g * 5 + 4 < shown; g++) slashes.push([xs[g * 5], xs[g * 5 + 4]])

  return (
    <svg
      viewBox={`0 0 ${VW} ${H}`}
      className={className}
      style={{ width: "100%", height: "auto", display: "block" }}
      role="img"
      aria-label={`${safe} notches — ${safe} on-budget day${safe === 1 ? "" : "s"}`}
    >
      <defs>
        <linearGradient id="raboj-wood" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#33261b" />
          <stop offset="52%" stopColor="#241a12" />
          <stop offset="100%" stopColor="#17100a" />
        </linearGradient>
      </defs>

      {/* The stick — always full width */}
      <rect x="0" y={top - 8} width={VW} height={bot - top + 16} rx="9" fill="url(#raboj-wood)" />
      <rect
        x="0.5"
        y={top - 7.5}
        width={VW - 1}
        height={bot - top + 15}
        rx="8.5"
        fill="none"
        stroke="rgba(199,154,91,0.28)"
        strokeWidth="1"
      />
      <line x1="8" y1={top - 6} x2={VW - 8} y2={top - 6} stroke="rgba(199,154,91,0.22)" strokeWidth="1.5" />

      {/* Faint guide marks for slots not yet notched */}
      {xs.map((xp, i) =>
        i >= shown ? (
          <line
            key={`ghost-${i}`}
            x1={xp}
            y1={top + 4}
            x2={xp}
            y2={bot - 4}
            stroke="rgba(199,154,91,0.11)"
            strokeWidth="2"
            strokeLinecap="round"
          />
        ) : null
      )}

      {/* Carved notches — a dark cut with a brass-lit edge; the latest glows */}
      {xs.slice(0, shown).map((xp, i) => {
        const recent = i === shown - 1
        return (
          <g key={`notch-${i}`}>
            <line x1={xp} y1={top} x2={xp} y2={bot} stroke="#0e0a06" strokeWidth="3" strokeLinecap="round" />
            <line
              x1={xp + 1.4}
              y1={top + 1}
              x2={xp + 1.4}
              y2={bot - 1}
              stroke={recent ? "#e6c185" : "rgba(199,154,91,0.5)"}
              strokeWidth="1.4"
              strokeLinecap="round"
            />
          </g>
        )
      })}

      {/* Bundle slashes (the fifth cut) */}
      {slashes.map(([x1, x2], i) => (
        <g key={`slash-${i}`}>
          <line x1={x1 - 3} y1={bot - 2} x2={x2 + 3} y2={top + 2} stroke="#0e0a06" strokeWidth="3" strokeLinecap="round" />
          <line
            x1={x1 - 3}
            y1={bot - 3.2}
            x2={x2 + 3}
            y2={top + 0.8}
            stroke="rgba(199,154,91,0.5)"
            strokeWidth="1.4"
            strokeLinecap="round"
          />
        </g>
      ))}

      {/* Overflow beyond what the stick shows */}
      {safe > maxFit && (
        <text
          x={VW - 12}
          y={(top + bot) / 2 + 4}
          textAnchor="end"
          fill="#c79a5b"
          fontSize="12"
          fontWeight="600"
          fontFamily="var(--font-mono)"
        >
          +{safe - maxFit}
        </text>
      )}
    </svg>
  )
}

type StampTone = "brass" | "good" | "over" | "muted"
const STAMP_COLOR: Record<StampTone, string> = {
  brass: "#c79a5b",
  good: "#9cb37a",
  over: "#c96a4e",
  muted: "#bfae97",
}

/** An inked rubber stamp — for statuses that a ledger would stamp (met / over / done). */
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
    <span className={`stamp inline-block text-[11px] ${className ?? ""}`} style={{ color: STAMP_COLOR[tone] }}>
      {children}
    </span>
  )
}
