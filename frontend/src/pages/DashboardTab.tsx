import { useCallback, useEffect, useMemo, useState } from "react"
import {
  formatRon,
  getCalendar,
  getDashboard,
  getDashboardLayout,
  getNetWorth,
  listExpenses,
  listQuests,
  saveDashboardLayout,
  type Category,
  type Dashboard,
  type Expense,
  type GoalCalendar,
  type NetWorth,
  type Quest,
  type WidgetId,
} from "@/lib/api"
import { ChevronIcon, HornGlyph } from "@/components/brand"
import { RabojStreak } from "@/components/raboj"
import {
  COLUMN_NAMES,
  DEFAULT_LAYOUT,
  OTHER,
  WIDGET_NAMES,
  dropAt,
  moveToOtherColumn,
  reorder,
  type Column,
  type Layout,
} from "@/lib/dashboardLayout"

const DONUT = ["#c79a5b", "#e8d3b4", "#b07e52", "#8a6440", "#9cb37a", "#c98a3c"]

const thisMonth = () => new Date().toISOString().slice(0, 7)
const monthLabel = (m: string) =>
  new Date(m + "-01T00:00:00").toLocaleDateString("en-GB", { month: "long", year: "numeric" })

function categoryLabel(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "—"
  if (!category.parentId) return category.name
  const parent = categories.find((c) => c.id === category.parentId)
  return parent ? `${parent.name} › ${category.name}` : category.name
}
function topName(categories: Category[], id: string): string {
  const c = categories.find((x) => x.id === id)
  if (!c) return "Other"
  return c.parentId ? categories.find((x) => x.id === c.parentId)?.name ?? "Other" : c.name
}
function monogram(label: string): string {
  const w = label.trim().split(/\s+/).filter(Boolean)
  if (!w.length) return "··"
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase()
}
const shortDate = (iso: string) =>
  new Date(iso + "T00:00:00").toLocaleDateString("en-GB", { day: "numeric", month: "short" })


export default function DashboardTab({
  categories = [],
  onNavigate,
}: {
  categories?: Category[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const [month] = useState(thisMonth())
  const [data, setData] = useState<Dashboard | null>(null)
  const [netWorth, setNetWorth] = useState<NetWorth | null>(null)
  const [quests, setQuests] = useState<Quest[]>([])
  const [calendar, setCalendar] = useState<GoalCalendar | null>(null)
  const [recent, setRecent] = useState<Expense[]>([])
  const [error, setError] = useState<string | null>(null)

  const [layout, setLayout] = useState<Layout>(DEFAULT_LAYOUT)
  const [arranging, setArranging] = useState(false)
  const [layoutError, setLayoutError] = useState<string | null>(null)
  const [dragging, setDragging] = useState<{ column: Column; index: number } | null>(null)

  useEffect(() => {
    getDashboard(month)
      .then((d) => {
        setData(d)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    getNetWorth().then(setNetWorth).catch(() => setNetWorth(null))
    listQuests().then(setQuests).catch(() => setQuests([]))
    getCalendar().then(setCalendar).catch(() => setCalendar(null))
    listExpenses({}).then((p) => setRecent(p.items.slice(0, 4))).catch(() => setRecent([]))
    // The arrangement is per-user server state (D9), so it survives this browser.
    getDashboardLayout()
      .then((saved) => setLayout({ main: saved.main, side: saved.side }))
      .catch(() => setLayout(DEFAULT_LAYOUT))
  }, [])

  // Optimistic: the move lands immediately and only a failed save is surfaced.
  const applyLayout = useCallback((next: Layout) => {
    setLayout(next)
    setLayoutError(null)
    saveDashboardLayout(next.main, next.side).catch(() =>
      setLayoutError("Couldn't save your arrangement — it will reset on reload.")
    )
  }, [])

  // Spend-by-category, largest first, capped to the donut palette.
  const slices = useMemo(() => {
    if (!data) return []
    const sorted = [...data.byCategory].sort((a, b) => b.amount - a.amount)
    if (sorted.length <= DONUT.length) return sorted.map((s, i) => ({ ...s, color: DONUT[i] }))
    const keep = sorted.slice(0, DONUT.length - 1)
    const other = sorted.slice(DONUT.length - 1).reduce((s, x) => s + x.amount, 0)
    return [...keep.map((s, i) => ({ ...s, color: DONUT[i] })), { category: "Other", amount: other, color: DONUT[DONUT.length - 1] }]
  }, [data])

  const greenDays = calendar?.days.filter((d) => d.status === "MET").length ?? 0
  const activeQuests = quests.filter((q) => q.status === "ACTIVE").slice(0, 3)

  if (error) return <p className="text-destructive py-8 text-center text-sm">{error}</p>
  if (!data) return <DashSkeletonNote />

  const widgets: Record<WidgetId, React.ReactNode> = {
    balance: <BalanceWidget month={month} data={data} netWorth={netWorth} />,
    breakdown: (
      <BreakdownWidget
        data={data}
        slices={slices}
        categories={categories}
        recent={recent}
        onNavigate={onNavigate}
      />
    ),
    savings: <SavingsWidget netWorth={netWorth} />,
    streak: <StreakWidget greenDays={greenDays} />,
    quests: <QuestsWidget activeQuests={activeQuests} onNavigate={onNavigate} />,
  }

  function renderColumn(column: Column, className: string) {
    return (
      <div className={className}>
        {layout[column].map((widget, index) => (
          <ArrangeableWidget
            key={widget}
            widget={widget}
            column={column}
            index={index}
            total={layout[column].length}
            arranging={arranging}
            isDragging={dragging?.column === column && dragging.index === index}
            onMove={(delta) => applyLayout(reorder(layout, column, index, delta))}
            onSwapColumn={() => applyLayout(moveToOtherColumn(layout, column, index))}
            onDragStart={() => setDragging({ column, index })}
            onDragEnd={() => setDragging(null)}
            onDrop={() => {
              if (dragging) applyLayout(dropAt(layout, dragging, column, index))
              setDragging(null)
            }}
          >
            {widgets[widget]}
          </ArrangeableWidget>
        ))}
        {arranging && (
          <ColumnEndDropZone
            column={column}
            active={dragging !== null}
            onDrop={() => {
              if (dragging) applyLayout(dropAt(layout, dragging, column, layout[column].length))
              setDragging(null)
            }}
          />
        )}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button
          onClick={() => setArranging((v) => !v)}
          aria-pressed={arranging}
          className={`inline-flex min-h-11 cursor-pointer items-center gap-2 rounded-xl border px-4 text-[13.5px] font-medium transition-colors ${
            arranging
              ? "border-primary/60 bg-primary/15 text-primary"
              : "text-muted-foreground border-white/10 hover:bg-white/[0.06]"
          }`}
        >
          {arranging ? "Done arranging" : "Arrange widgets"}
        </button>
        {arranging && (
          <p className="text-muted-foreground text-[13px]">
            Drag a widget, or use its arrows to move it.
          </p>
        )}
      </div>

      {layoutError && <p className="text-destructive text-[13px]">{layoutError}</p>}

      <div className="flex flex-wrap items-start gap-[22px]">
        {renderColumn("main", "flex min-w-[min(100%,440px)] flex-1 basis-[62%] flex-col gap-[22px]")}
        {renderColumn("side", "flex min-w-[min(100%,320px)] flex-1 basis-[30%] flex-col gap-[22px]")}
      </div>
    </div>
  )
}

/**
 * Wraps one widget with its arrange affordances. The arrow buttons are the primary
 * mechanism — HTML5 drag-and-drop does not fire on touch and is not reachable by
 * keyboard, so dragging is the enhancement, not the interface.
 */
function ArrangeableWidget({
  widget,
  column,
  index,
  total,
  arranging,
  isDragging,
  onMove,
  onSwapColumn,
  onDragStart,
  onDragEnd,
  onDrop,
  children,
}: {
  widget: WidgetId
  column: Column
  index: number
  total: number
  arranging: boolean
  isDragging: boolean
  onMove: (delta: number) => void
  onSwapColumn: () => void
  onDragStart: () => void
  onDragEnd: () => void
  onDrop: () => void
  children: React.ReactNode
}) {
  if (!arranging) return <>{children}</>

  const name = WIDGET_NAMES[widget]

  return (
    <div
      draggable
      onDragStart={onDragStart}
      onDragEnd={onDragEnd}
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault()
        onDrop()
      }}
      className="border-primary/40 rounded-2xl border border-dashed p-2 transition-opacity"
      style={{ opacity: isDragging ? 0.4 : 1, cursor: "grab" }}
    >
      <div className="mb-2 flex items-center justify-between gap-2 px-1">
        <span className="ledger-label truncate">{name}</span>
        <div className="flex items-center gap-1">
          <ArrangeButton
            label={`Move ${name} up`}
            disabled={index === 0}
            onClick={() => onMove(-1)}
            rotation="-rotate-90"
          />
          <ArrangeButton
            label={`Move ${name} down`}
            disabled={index === total - 1}
            onClick={() => onMove(1)}
            rotation="rotate-90"
          />
          <ArrangeButton
            label={`Move ${name} to ${COLUMN_NAMES[OTHER[column]]}`}
            onClick={onSwapColumn}
            rotation={column === "main" ? "" : "rotate-180"}
          />
        </div>
      </div>
      {children}
    </div>
  )
}

/** 44×44 minimum, per the touch-target follow-up in docs/design.md. */
function ArrangeButton({
  label,
  onClick,
  rotation,
  disabled = false,
}: {
  label: string
  onClick: () => void
  /** Tailwind rotation utility turning the right-pointing chevron into the direction meant. */
  rotation: string
  disabled?: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="text-muted-foreground hover:text-foreground grid size-11 flex-none cursor-pointer place-items-center rounded-lg border border-white/10 transition-colors hover:bg-white/[0.07] disabled:cursor-not-allowed disabled:opacity-30"
    >
      <ChevronIcon size={16} className={rotation} />
    </button>
  )
}

/** Lets a widget be dropped past the last one in a column, including an empty column. */
function ColumnEndDropZone({
  column,
  active,
  onDrop,
}: {
  column: Column
  active: boolean
  onDrop: () => void
}) {
  return (
    <div
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault()
        onDrop()
      }}
      aria-hidden="true"
      className="text-muted-foreground grid min-h-16 place-items-center rounded-2xl border border-dashed border-white/12 text-[12.5px] transition-colors"
      style={{ opacity: active ? 1 : 0.45 }}
    >
      Drop here to put a widget at the end of {COLUMN_NAMES[column]}
    </div>
  )
}

function BalanceWidget({
  month,
  data,
  netWorth,
}: {
  month: string
  data: Dashboard
  netWorth: NetWorth | null
}) {
  const delta = data.totalSpent - data.previousMonthTotal

  return (
    <section className="ledger-paper relative overflow-hidden p-7">
      <div className="pointer-events-none absolute -right-10 -bottom-14 size-[240px] opacity-[0.06]">
        <HornGlyphFull />
      </div>
      <div className="relative flex flex-wrap items-end justify-between gap-x-10 gap-y-6">
        <div>
          <div className="text-muted-foreground text-[13.5px]">
            Balance carried forward · {monthLabel(month)}
          </div>
          <div className="ink-underline mt-3 inline-flex items-baseline gap-2">
            <span className="figure text-[52px] leading-none font-semibold sm:text-[60px]">
              {netWorth ? Math.floor(netWorth.total / 100).toLocaleString("en-US") : "—"}
            </span>
            <span className="figure text-[24px] font-medium text-[#D2C5B4]">
              {netWorth ? "." + String(netWorth.total % 100).padStart(2, "0") : ""}
            </span>
            <span className="text-muted-foreground mb-1 font-mono text-[14px]">RON</span>
          </div>
          <div className="text-muted-foreground mt-4 text-[13.5px]">
            {delta <= 0 ? "Down" : "Up"}{" "}
            <span style={{ color: delta <= 0 ? "#9cb37a" : "#c96a4e" }}>
              {formatRon(Math.abs(delta)).replace(/\s?RON$/, "")} RON
            </span>{" "}
            on spend against last month
          </div>
        </div>
        <div className="ruled min-w-[210px] flex-1 basis-[240px] text-[14px]">
          <div className="flex items-baseline justify-between py-2.5">
            <span className="text-muted-foreground">Spent · {monthLabel(month).split(" ")[0]}</span>
            <span className="tnum font-mono" style={{ color: "#c96a4e" }}>
              −{formatRon(data.totalSpent).replace(/\s?RON$/, "")}
            </span>
          </div>
          {data.projectedMonthEnd !== null && (
            <div className="flex items-baseline justify-between py-2.5">
              <span className="text-muted-foreground">Projected month-end</span>
              <span className="tnum font-mono">{formatRon(data.projectedMonthEnd).replace(/\s?RON$/, "")}</span>
            </div>
          )}
          <div className="flex items-baseline justify-between py-2.5">
            <span className="text-muted-foreground">Entries logged</span>
            <span className="tnum font-mono">{data.expenseCount}</span>
          </div>
        </div>
      </div>
      <SpendTrajectory data={data} month={month} />
    </section>
  )
}

const GHOST_LINE = "rgba(199,154,91,0.30)"
const GHOST_GUIDE = "rgba(199,154,91,0.11)"

const runningTotals = (series: { amount: number }[]) => {
  let sum = 0
  return series.map((point) => (sum += point.amount))
}

/**
 * Cumulative spend for the month against the F3 ghost flock — the same month with
 * non-mandatory spend held at its trailing 3-month median. The ghost borrows the
 * răboj's faint guide-mark language so it reads as a reference the eye can dismiss,
 * never as a second ledger competing with what was actually recorded.
 */
function SpendTrajectory({ data, month }: { data: Dashboard; month: string }) {
  const days = data.byDay.length
  if (days === 0) return null

  const real = runningTotals(data.byDay)
  const ghost = data.ghostByDay ? runningTotals(data.ghostByDay) : null

  // The recorded line stops at today: a flat run to month-end is missing data, not restraint.
  const now = new Date()
  const realThrough =
    month === now.toISOString().slice(0, 7) ? Math.min(now.getDate(), days) : days
  const peak = Math.max(...real.slice(0, realThrough), ...(ghost ?? [0]), 1)

  const VW = 340
  const H = 104
  const padX = 10
  const padTop = 10
  const padBottom = 18

  const x = (day: number) => padX + ((day - 1) / Math.max(days - 1, 1)) * (VW - padX * 2)
  const y = (value: number) => H - padBottom - (value / peak) * (H - padTop - padBottom)
  const path = (values: number[], through: number) =>
    values
      .slice(0, through)
      .map((value, i) => `${x(i + 1).toFixed(1)},${y(value).toFixed(1)}`)
      .join(" ")

  const spent = formatRon(real[realThrough - 1] ?? 0).replace(/\s?RON$/, "")
  const baseline = formatRon(data.ghostMonthTotal ?? 0).replace(/\s?RON$/, "")

  return (
    <div className="relative mt-7">
      <div className="ledger-label mb-3">The month so far</div>
      <svg
        viewBox={`0 0 ${VW} ${H}`}
        style={{ width: "100%", height: "auto", display: "block" }}
        role="img"
        aria-label={
          ghost
            ? `Recorded spend of ${spent} RON so far this month, against a ghost flock baseline of ${baseline} RON by month end`
            : `Recorded spend of ${spent} RON so far this month`
        }
      >
        <line
          x1={padX}
          y1={H - padBottom}
          x2={VW - padX}
          y2={H - padBottom}
          stroke="rgba(199,154,91,0.22)"
          strokeWidth="1"
        />

        {ghost && (
          <>
            {/* Guide marks every fifth day — the răboj bundles its notches in fives. */}
            {ghost.map((value, i) =>
              (i + 1) % 5 === 0 ? (
                <line
                  key={`guide-${i}`}
                  x1={x(i + 1)}
                  y1={y(value)}
                  x2={x(i + 1)}
                  y2={H - padBottom}
                  stroke={GHOST_GUIDE}
                  strokeWidth="2"
                  strokeLinecap="round"
                />
              ) : null
            )}
            <polyline
              points={path(ghost, days)}
              fill="none"
              stroke={GHOST_LINE}
              strokeWidth="1.25"
              strokeDasharray="3 3"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </>
        )}

        {/* The recorded ledger: a dark cut with a brass-lit edge, as the notches are carved. */}
        <polyline
          points={path(real, realThrough)}
          fill="none"
          stroke="#0e0a06"
          strokeWidth="3.4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <polyline
          points={path(real, realThrough)}
          fill="none"
          stroke="#c79a5b"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>

      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1.5">
        <span className="text-muted-foreground inline-flex items-center gap-2 text-[12.5px]">
          <svg width="18" height="6" aria-hidden="true">
            <line x1="0" y1="3" x2="18" y2="3" stroke="#c79a5b" strokeWidth="2" strokeLinecap="round" />
          </svg>
          Recorded
        </span>
        {ghost && (
          <span className="text-muted-foreground inline-flex items-center gap-2 text-[12.5px]">
            <svg width="18" height="6" aria-hidden="true">
              <line
                x1="0"
                y1="3"
                x2="18"
                y2="3"
                stroke={GHOST_LINE}
                strokeWidth="1.25"
                strokeDasharray="3 3"
                strokeLinecap="round"
              />
            </svg>
            Ghost flock
          </span>
        )}
      </div>

      <p className="text-muted-foreground mt-2 text-[12.5px] leading-relaxed">
        {ghost
          ? `The ghost flock is this same month with your non-mandatory spending held at its trailing 3-month median — ${baseline} RON by month end. The carved line is what you actually recorded.`
          : "The ghost flock — this month with your non-mandatory spending held at its 3-month median — unlocks once you have three months of history."}
      </p>
    </div>
  )
}

type Slice = { category: string; amount: number; color: string }

function BreakdownWidget({
  data,
  slices,
  categories,
  recent,
  onNavigate,
}: {
  data: Dashboard
  slices: Slice[]
  categories: Category[]
  recent: Expense[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const [slice, setSlice] = useState<string | null>(null)
  const [hover, setHover] = useState<string | null>(null)

  const totalSpent = data.totalSpent
  const active = hover ?? slice
  const activeSlice = slices.find((s) => s.category === active)
  const donutTotal = activeSlice ? activeSlice.amount : totalSpent

  // SVG donut geometry.
  const R = 62
  const C = 2 * Math.PI * R
  let offset = 0

  return (
    <section className="ledger-card p-6">
      <div className="mb-5.5 flex items-center gap-2.5">
        <HornGlyph className="text-primary size-5.5" />
        <h2 className="text-[18px] font-semibold">Where your money goes</h2>
      </div>
      <div className="grid gap-14 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
        {/* Donut + legend */}
        <div className="min-w-0">
          <div className="ledger-label mb-4.5">Spend by category</div>
          {slices.length === 0 ? (
            <p className="text-muted-foreground py-8 text-sm">No spending recorded this month.</p>
          ) : (
            <div className="flex items-center gap-5.5">
              <div className="relative size-[158px] flex-none">
                <svg viewBox="0 0 158 158" className="size-full -rotate-90">
                  {slices.map((s) => {
                    const frac = totalSpent > 0 ? s.amount / totalSpent : 0
                    const len = C * frac
                    const on = active === s.category
                    const dim = active && !on
                    const el = (
                      <circle
                        key={s.category}
                        cx={79}
                        cy={79}
                        r={R}
                        fill="none"
                        stroke={s.color}
                        strokeWidth={on ? 30 : 24}
                        strokeDasharray={`${Math.max(0, len - 3)} ${C - len + 3}`}
                        strokeDashoffset={-offset}
                        opacity={dim ? 0.32 : 1}
                        style={{ cursor: "pointer", transition: "stroke-width .15s ease, opacity .15s ease" }}
                        onMouseEnter={() => setHover(s.category)}
                        onMouseLeave={() => setHover(null)}
                        onClick={() => setSlice(slice === s.category ? null : s.category)}
                      />
                    )
                    offset += len
                    return el
                  })}
                </svg>
                <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
                  <div>
                    <div className="font-heading tnum text-[23px] font-semibold tracking-tight">
                      {formatRon(donutTotal).replace(/\s?RON$/, "")}
                    </div>
                    <div className="ledger-label !text-[11px]">
                      {activeSlice ? activeSlice.category : "RON spent"}
                    </div>
                  </div>
                </div>
              </div>
              <div className="flex min-w-0 flex-1 flex-col gap-3">
                {slices.map((s) => {
                  const pct = totalSpent > 0 ? Math.round((s.amount / totalSpent) * 100) : 0
                  const sel = slice === s.category
                  return (
                    <div
                      key={s.category}
                      role="button"
                      tabIndex={0}
                      onClick={() => setSlice(sel ? null : s.category)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault()
                          setSlice(sel ? null : s.category)
                        }
                      }}
                      onMouseEnter={() => setHover(s.category)}
                      onMouseLeave={() => setHover(null)}
                      className={`-mx-2 flex min-h-11 cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-[14.5px] transition-colors ${
                        sel ? "bg-white/[0.07]" : "hover:bg-white/[0.06]"
                      }`}
                    >
                      <span className="size-2.5 flex-none rounded-[3px]" style={{ background: s.color }} />
                      <span className="flex-1 truncate font-medium">{s.category}</span>
                      <span className="tnum font-mono">{formatRon(s.amount).replace(/\s?RON$/, "")}</span>
                      <span className="tnum text-muted-foreground w-9 text-right font-mono">{pct}%</span>
                    </div>
                  )
                })}
                <p className="text-muted-foreground mt-0.5 text-[13px]">
                  Click a category to open it in Expenses
                </p>
              </div>
            </div>
          )}
          {slice && activeSlice && (
            <div
              className="mt-4 rounded-2xl border border-white/[0.09] bg-[#241C17] p-5"
              style={{ animation: "riseIn .16s ease" }}
            >
              <div className="flex items-center justify-between">
                <div className="text-[15.5px] font-semibold">{activeSlice.category}</div>
                <button
                  onClick={() => setSlice(null)}
                  aria-label={`Close ${activeSlice.category} details`}
                  className="text-muted-foreground grid size-11 cursor-pointer place-items-center rounded-lg text-lg leading-none hover:bg-white/[0.06]"
                >
                  ×
                </button>
              </div>
              <div className="text-muted-foreground mt-1 font-mono text-[12px]">
                {formatRon(activeSlice.amount)} ·{" "}
                {totalSpent > 0 ? Math.round((activeSlice.amount / totalSpent) * 100) : 0}% of spend
              </div>
              <button
                onClick={() => onNavigate?.("expenses", activeSlice.category)}
                className="text-primary mt-4 min-h-11 w-full cursor-pointer rounded-xl border border-primary/50 bg-primary/15 py-2.5 text-[13.5px] font-semibold hover:bg-primary/25"
              >
                See all {activeSlice.category} transactions →
              </button>
            </div>
          )}
        </div>

        {/* Recent activity */}
        <div className="min-w-0">
          <div className="mb-4.5 flex items-baseline justify-between">
            <span className="ledger-label">Recent activity</span>
            <button
              onClick={() => onNavigate?.("expenses")}
              className="text-primary min-h-11 cursor-pointer px-2 text-[13px]"
            >
              See all
            </button>
          </div>
          <div className="flex flex-col">
            {recent.length === 0 ? (
              <p className="text-muted-foreground py-4 text-sm">Nothing yet — add your first expense.</p>
            ) : (
              recent.map((t) => {
                const label = t.note || topName(categories, t.categoryId)
                return (
                  <button
                    key={t.id}
                    onClick={() => onNavigate?.("expenses")}
                    className="-mx-3 flex min-h-11 items-center gap-3.5 rounded-xl px-3 py-2.75 text-left transition-colors hover:bg-white/[0.055]"
                  >
                    <div className="grid size-[38px] flex-none place-items-center rounded-xl bg-[#241C17] font-mono text-[14px] text-[#CFC1AE]">
                      {monogram(label)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[14.5px] font-medium">{label}</div>
                      <div className="text-muted-foreground font-mono text-[12px]">
                        {categoryLabel(categories, t.categoryId)} · {shortDate(t.expenseDate)}
                      </div>
                    </div>
                    <span className="tnum font-mono text-[14px] text-[#EFE9DC]">−{formatRon(t.amount).replace(/\s?RON$/, "")}</span>
                  </button>
                )
              })
            )}
          </div>
        </div>
      </div>
    </section>
  )
}

function SavingsWidget({ netWorth }: { netWorth: NetWorth | null }) {
  return (
    <section className="ledger-card p-6">
      <div className="flex items-center gap-2.5">
        <HornGlyph className="text-primary size-5" />
        <h2 className="text-[17px] font-semibold">Savings</h2>
      </div>
      <div className="mt-4 flex items-baseline gap-2.5">
        <span className="font-heading tnum text-[36px] font-semibold tracking-tight">
          {netWorth ? formatRon(netWorth.total).replace(/\s?RON$/, "") : "—"}
        </span>
        <span className="text-muted-foreground text-[14.5px]">RON saved</span>
      </div>
      <p className="text-muted-foreground mt-4 text-[13.5px]">
        {netWorth?.accounts ?? 0} savings account{(netWorth?.accounts ?? 0) === 1 ? "" : "s"} ·{" "}
        {netWorth ? formatRon(netWorth.monthlyIncome).replace(/\s?RON$/, "") : "—"} income / month
      </p>
    </section>
  )
}

/** A răboj tally: one notch per on-budget day. */
function StreakWidget({ greenDays }: { greenDays: number }) {
  return (
    <section className="ledger-card p-6">
      <h2 className="text-[17px] font-semibold">Streak</h2>
      <div className="mt-3.5 flex items-baseline gap-2.5">
        <span className="figure text-[36px] font-semibold">{greenDays}</span>
        <span className="text-muted-foreground text-[14.5px]">on-budget days this month</span>
      </div>
      <div className="mt-5">
        <RabojStreak count={greenDays} />
      </div>
    </section>
  )
}

function QuestsWidget({
  activeQuests,
  onNavigate,
}: {
  activeQuests: Quest[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  return (
    <section className="ledger-card p-6">
      <div className="mb-4.5 flex items-center justify-between">
        <h2 className="text-[17px] font-semibold">Active quests</h2>
        <button
          onClick={() => onNavigate?.("quests")}
          className="text-primary min-h-11 cursor-pointer px-2 text-[13.5px]"
        >
          All
        </button>
      </div>
      <div className="flex flex-col gap-4.5">
        {activeQuests.length === 0 ? (
          <p className="text-muted-foreground text-sm">No active quests — accept one on the Quests page.</p>
        ) : (
          activeQuests.map((q) => {
            const pct = q.target > 0 ? Math.min(100, (q.progress / q.target) * 100) : 0
            const hot = q.kind === "CAP" && pct > 85
            const color = hot ? "#C98A3C" : "#C79A5B"
            return (
              <div key={q.id}>
                <div className="flex items-baseline justify-between gap-2.5">
                  <span className="text-[14.5px] font-medium" style={{ color: hot ? "#C98A3C" : undefined }}>
                    {q.title}
                  </span>
                  <span className="tnum font-mono text-[13px]" style={{ color: hot ? "#C98A3C" : undefined }}>
                    {q.kind === "DAYS"
                      ? `${q.progress}/${q.target}`
                      : `${Math.round(q.progress / 100)} / ${Math.round(q.target / 100)}`}
                  </span>
                </div>
                <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-white/[0.09]">
                  <div className="h-full rounded-full" style={{ width: `${pct}%`, background: color }} />
                </div>
              </div>
            )
          })
        )}
      </div>
    </section>
  )
}

/* Full ram mark used as the watermark inside the hero. */
function HornGlyphFull() {
  return (
    <svg
      viewBox="-8 -13 116 116"
      fill="none"
      stroke="#C79A5B"
      strokeWidth={7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-full"
      aria-hidden="true"
    >
      <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
      <circle cx="32" cy="52" r="4" fill="#C79A5B" stroke="none" />
    </svg>
  )
}

function DashSkeletonNote() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24">
      <div className="text-foreground size-14">
        <svg viewBox="-8 -13 116 116" fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" className="size-full" aria-hidden="true">
          <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" stroke="#C79A5B" style={{ strokeDasharray: 370, animation: "coilUnroll 2.1s cubic-bezier(.5,0,.5,1) infinite" }} />
          <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div className="ledger-label" style={{ color: "#a89473" }}>Loading your money</div>
    </div>
  )
}
