/**
 * The răboj — a Carpathian shepherd's notched tally stick. Each on-budget day is
 * a notch carved into the rail; notches bundle in fives (four cuts crossed by a
 * fifth), exactly as a cioban tallied sheep. This is the app's signature counter:
 * a streak is a physical object, not a progress bar.
 */
import type { ReactNode } from "react"
export function RabojStreak({ count, className }: { count: number; className?: string }) {
  const shown = Math.min(Math.max(count, 0), 35)
  const gap = 13
  const bundleGap = 13
  const pad = 18
  const top = 16
  const bot = 54
  const H = 70

  const xs: number[] = []
  let x = pad
  for (let i = 0; i < shown; i++) {
    xs.push(x)
    x += gap
    if ((i + 1) % 5 === 0) x += bundleGap
  }
  const width = Math.max(x - gap + pad, pad * 2 + 40)

  // A diagonal slash closes each completed bundle of five.
  const slashes: [number, number][] = []
  for (let g = 0; g * 5 + 4 < shown; g++) {
    slashes.push([xs[g * 5], xs[g * 5 + 4]])
  }

  return (
    <svg
      viewBox={`0 0 ${width} ${H}`}
      className={className}
      style={{ width: "100%", maxWidth: width, height: "auto" }}
      role="img"
      aria-label={`${count} notches — ${count} on-budget day${count === 1 ? "" : "s"}`}
    >
      <defs>
        <linearGradient id="raboj-wood" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#33261b" />
          <stop offset="52%" stopColor="#241a12" />
          <stop offset="100%" stopColor="#17100a" />
        </linearGradient>
      </defs>

      {/* The stick */}
      <rect x="0" y={top - 8} width={width} height={bot - top + 16} rx="9" fill="url(#raboj-wood)" />
      <rect
        x="0.5"
        y={top - 7.5}
        width={width - 1}
        height={bot - top + 15}
        rx="8.5"
        fill="none"
        stroke="rgba(199,154,91,0.28)"
        strokeWidth="1"
      />
      {/* brass top bevel */}
      <line x1="8" y1={top - 6} x2={width - 8} y2={top - 6} stroke="rgba(199,154,91,0.22)" strokeWidth="1.5" />

      {shown === 0 && (
        <text
          x={width / 2}
          y={(top + bot) / 2 + 4}
          textAnchor="middle"
          fill="#7c6b57"
          fontSize="12"
          fontFamily="var(--font-sans)"
        >
          no notches yet
        </text>
      )}

      {/* Notches — carved: a dark cut with a brass-lit right edge */}
      {xs.map((xp, i) => {
        const recent = i === shown - 1
        return (
          <g key={i}>
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
        <g key={`s${i}`}>
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
    <span className={`stamp inline-block text-[10px] ${className ?? ""}`} style={{ color: STAMP_COLOR[tone] }}>
      {children}
    </span>
  )
}
