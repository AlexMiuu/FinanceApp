import { useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { ArgaliMark, ChevronIcon, NavIcon, type NavIconName } from "@/components/brand"

export type PageKey = "dashboard" | "expenses" | "reports" | "quests" | "profile"

const NAV: { key: PageKey; label: string; hint: string; icon: NavIconName }[] = [
  { key: "dashboard", label: "Overview", hint: "Money at a glance", icon: "overview" },
  { key: "expenses", label: "Expenses", hint: "Spending log", icon: "expenses" },
  { key: "reports", label: "Reports", hint: "Trends & reports", icon: "reports" },
  { key: "quests", label: "Quests", hint: "Goals & streaks", icon: "quests" },
  { key: "profile", label: "Account", hint: "Account & pay tools", icon: "account" },
]

export function Sidebar({
  page,
  onNavigate,
  onAdd,
  expanded,
  onToggle,
}: {
  page: PageKey
  onNavigate: (page: PageKey) => void
  onAdd: () => void
  expanded: boolean
  onToggle: () => void
}) {
  const { user } = useAuth()
  const [hover, setHover] = useState<PageKey | null>(null)
  const initial = (user?.displayName ?? "?").charAt(0).toUpperCase()

  return (
    <aside
      className="bg-sidebar border-sidebar-border sticky top-0 z-30 hidden h-svh flex-none flex-col items-stretch gap-1 border-r p-[20px_14px] transition-[width] duration-200 ease-out md:flex"
      style={{ width: expanded ? 236 : 78 }}
    >
      {/* Brand */}
      <div className="flex items-center gap-3 px-1 pb-4">
        <div className="text-primary grid size-10 flex-none place-items-center">
          <ArgaliMark className="size-[30px]" strokeWidth={7} />
        </div>
        {expanded && (
          <span className="font-mono text-foreground text-[11px] font-medium tracking-[0.2em] uppercase">
            Argali
          </span>
        )}
      </div>
      <div className="bg-sidebar-border mb-3 h-px" />

      {/* Nav */}
      {NAV.map((item) => {
        const active = page === item.key
        return (
          <div key={item.key} className="relative">
            <button
              onClick={() => onNavigate(item.key)}
              onMouseEnter={() => setHover(item.key)}
              onMouseLeave={() => setHover(null)}
              aria-current={active ? "page" : undefined}
              aria-label={item.label}
              className={`flex h-11 w-full cursor-pointer items-center gap-3.5 border-none px-3.5 text-left transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3] ${
                active
                  ? "edge-mark-accent bg-popover text-primary"
                  : "text-sidebar-foreground hover:bg-popover"
              }`}
            >
              <span className="grid size-[20px] flex-none place-items-center">
                <NavIcon name={item.icon} size={18} />
              </span>
              {expanded && (
                <span className="font-mono text-[10px] font-medium tracking-[0.08em] uppercase">
                  {item.label}
                </span>
              )}
            </button>
            {!expanded && hover === item.key && (
              <div
                className="bg-popover border-border pointer-events-none absolute top-1/2 left-[calc(100%+12px)] z-[60] -translate-y-1/2 border px-2.5 py-1.5 text-[13px] font-medium whitespace-nowrap shadow-[0_12px_24px_rgba(0,0,0,0.4)]"
                style={{ animation: "fadeIn .1s ease" }}
              >
                {item.label}
              </div>
            )}
          </div>
        )
      })}

      <div className="flex-1" />

      {/* Collapse toggle */}
      <button
        onClick={onToggle}
        aria-label={expanded ? "Collapse sidebar" : "Expand sidebar"}
        className="text-muted-foreground border-border hover:border-[#4C93A6] flex cursor-pointer items-center gap-3.5 border bg-transparent px-3 py-2.5 text-left transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
      >
        <span className="grid size-[20px] flex-none place-items-center">
          <ChevronIcon size={15} className={expanded ? "rotate-180" : ""} />
        </span>
        {expanded && <span className="ledger-label text-[10px]">Collapse</span>}
      </button>

      {/* Add */}
      <button
        onClick={onAdd}
        aria-label="Add transaction"
        className="bg-[#123945] border-[#4C93A6] text-[#C4E7F0] hover:bg-[#174756] mt-1.5 flex cursor-pointer items-center gap-3.5 border px-3 py-3 text-left font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
      >
        <span className="grid size-[20px] flex-none place-items-center text-[19px] leading-none">+</span>
        {expanded && <span className="text-[13.5px]">Add expense</span>}
      </button>

      {/* User */}
      <button
        onClick={() => onNavigate("profile")}
        className="border-sidebar-border mt-1.5 flex cursor-pointer items-center gap-3 border-none border-t bg-transparent px-1 pt-3.5 text-left focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
      >
        <div className="bg-popover border-border text-primary grid size-[32px] flex-none place-items-center rounded-full border text-sm font-medium">
          {initial}
        </div>
        {expanded && (
          <div className="min-w-0">
            <div className="truncate text-[13.5px] font-medium">{user?.displayName}</div>
            <div className="text-muted-foreground text-[12px]">Level 4 · Saver</div>
          </div>
        )}
      </button>
    </aside>
  )
}

/** Mobile bottom nav — the rail folds to a bar under 768px. */
export function MobileNav({
  page,
  onNavigate,
  onAdd,
}: {
  page: PageKey
  onNavigate: (page: PageKey) => void
  onAdd: () => void
}) {
  return (
    <nav className="bg-sidebar border-sidebar-border fixed inset-x-0 bottom-0 z-30 flex items-center justify-around border-t px-1 py-1.5 md:hidden">
      {NAV.map((item) => {
        const active = page === item.key
        return (
          <button
            key={item.key}
            onClick={() => onNavigate(item.key)}
            aria-current={active ? "page" : undefined}
            aria-label={item.label}
            className={`flex min-h-[60px] flex-1 cursor-pointer flex-col items-center justify-center gap-1 border-none bg-transparent py-1.5 font-mono text-[9.5px] font-medium tracking-[0.06em] uppercase focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3] ${
              active ? "text-primary" : "text-sidebar-foreground"
            }`}
            style={{ boxShadow: active ? "inset 0 2px 0 0 var(--primary)" : "none" }}
          >
            <NavIcon name={item.icon} size={19} />
            {item.label}
          </button>
        )
      })}
      <button
        onClick={onAdd}
        aria-label="Add transaction"
        className="bg-[#123945] border-[#4C93A6] text-[#C4E7F0] ml-1 grid size-11 flex-none place-items-center border text-2xl leading-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
      >
        +
      </button>
    </nav>
  )
}
