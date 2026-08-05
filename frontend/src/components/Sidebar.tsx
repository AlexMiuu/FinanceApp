import { useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { ArgaliMark, NavIcon, type NavIconName } from "@/components/brand"

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
      className="bg-sidebar border-sidebar-border sticky top-0 z-30 hidden h-svh flex-none flex-col items-stretch gap-2 border-r p-[20px_14px] transition-[width] duration-200 ease-out md:flex"
      style={{ width: expanded ? 236 : 78 }}
    >
      {/* Brand */}
      <div className="flex items-center gap-3 px-1.5 pb-5">
        <div className="text-foreground grid size-10 flex-none place-items-center">
          <ArgaliMark className="size-[34px]" strokeWidth={7} />
        </div>
        {expanded && (
          <span className="text-foreground text-[15px] font-semibold tracking-[0.2em]">ARGALI</span>
        )}
      </div>

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
              className={`flex w-full cursor-pointer items-center gap-3.5 rounded-xl border-none p-3 text-left transition-colors ${
                active
                  ? "bg-primary text-primary-foreground"
                  : "text-sidebar-foreground hover:bg-white/[0.07]"
              }`}
            >
              <span className="grid size-[22px] flex-none place-items-center">
                <NavIcon name={item.icon} />
              </span>
              {expanded && <span className="text-[14.5px] font-medium">{item.label}</span>}
            </button>
            {!expanded && hover === item.key && (
              <div
                className="bg-popover border-border pointer-events-none absolute top-1/2 left-[calc(100%+14px)] z-[60] -translate-y-1/2 rounded-lg border px-2.5 py-1.5 text-[13px] font-medium whitespace-nowrap shadow-[0_8px_24px_rgba(0,0,0,0.5)]"
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
        className="text-muted-foreground flex cursor-pointer items-center gap-3.5 rounded-xl border border-white/10 bg-transparent px-3 py-2.5 text-left transition-colors hover:bg-white/5 hover:text-foreground"
      >
        <span className="grid size-[22px] flex-none place-items-center font-mono text-[15px]">
          {expanded ? "«" : "»"}
        </span>
        {expanded && <span className="text-[13.5px]">Collapse</span>}
      </button>

      {/* Add */}
      <button
        onClick={onAdd}
        aria-label="Add transaction"
        className="bg-primary text-primary-foreground mt-1 flex cursor-pointer items-center gap-3.5 rounded-2xl border-none px-3 py-3 text-left font-semibold shadow-[0_8px_22px_rgba(217,169,122,0.38)] transition-all hover:bg-[#D8B27A] hover:shadow-[0_10px_28px_rgba(217,169,122,0.5)]"
      >
        <span className="grid size-[22px] flex-none place-items-center text-[21px] leading-none">+</span>
        {expanded && <span className="text-[14.5px]">Add</span>}
      </button>

      {/* User */}
      <button
        onClick={() => onNavigate("profile")}
        className="mt-1.5 flex cursor-pointer items-center gap-3 border-none border-t border-white/[0.07] bg-transparent px-1.5 pt-3.5 text-left"
      >
        <div
          className="grid size-[34px] flex-none place-items-center rounded-full text-sm font-semibold"
          style={{ background: "#6B4B35", color: "#F2EDE1" }}
        >
          {initial}
        </div>
        {expanded && (
          <div className="min-w-0">
            <div className="truncate text-[13.5px] font-medium">{user?.displayName}</div>
            <div className="text-[11.5px]" style={{ color: "#C0B1A0" }}>
              Level 4 · Saver
            </div>
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
            className={`flex flex-1 cursor-pointer flex-col items-center gap-0.5 rounded-lg border-none bg-transparent py-1.5 text-[10px] font-medium ${
              active ? "text-primary" : "text-sidebar-foreground"
            }`}
          >
            <NavIcon name={item.icon} size={20} />
            {item.label}
          </button>
        )
      })}
      <button
        onClick={onAdd}
        aria-label="Add transaction"
        className="bg-primary text-primary-foreground ml-1 grid size-11 flex-none place-items-center rounded-full border-none text-2xl leading-none shadow-[0_6px_18px_rgba(217,169,122,0.4)]"
      >
        +
      </button>
    </nav>
  )
}
