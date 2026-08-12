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

const SLICE_COLORS = ["#9AD4E3", "#4C93A6", "#8FC7A6", "#E09880", "#8A9399", "#6FA8B8"]

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

  // Spend-by-category, largest first, capped to the palette.
  const slices = useMemo(() => {
    if (!data) return []
    const sorted = [...data.byCategory].sort((a, b) => b.amount - a.amount)
    if (sorted.length <= SLICE_COLORS.length) return sorted.map((s, i) => ({ ...s, color: SLICE_COLORS[i] }))
    const keep = sorted.slice(0, SLICE_COLORS.length - 1)
    const other = sorted.slice(SLICE_COLORS.length - 1).reduce((s, x) => s + x.amount, 0)
    return [
      ...keep.map((s, i) => ({ ...s, color: SLICE_COLORS[i] })),
      { category: "Other", amount: other, color: SLICE_COLORS[SLICE_COLORS.length - 1] },
    ]
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
          className={`inline-flex min-h-11 cursor-pointer items-center gap-2 border px-4 text-[13.5px] font-medium transition-colors ${
            arranging
              ? "border-primary/60 bg-primary/15 text-primary"
              : "text-muted-foreground border-border hover:bg-popover"
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
      className="border-primary/40 border border-dashed p-2 transition-opacity"
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
      className="text-muted-foreground hover:text-foreground grid size-11 flex-none cursor-pointer place-items-center border border-border transition-colors hover:bg-popover disabled:cursor-not-allowed disabled:opacity-30"
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
      className="text-muted-foreground border-border grid min-h-16 place-items-center border border-dashed text-[12.5px] transition-colors"
      style={{ opacity: active ? 1 : 0.45 }}
    >
      Drop here to put a widget at the end of {COLUMN_NAMES[column]}
    </div>
  )
}

/** "munte" -> "Munte". Pastoral calendar terms arrive lower-case. */
function seasonLabel(season: string): string {
  return season.charAt(0).toUpperCase() + season.slice(1).toLowerCase()
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

  // Ghost balance — a hypothetical: what this month would look like if spend had
  // continued at last month's daily rate. Real numbers (data.previousMonthTotal),
  // prorated to today's day-of-month; not fabricated.
  const dayOfMonth = new Date().getDate()
  const daysInPrevMonth = new Date(new Date().getFullYear(), new Date().getMonth(), 0).getDate()
  const ghostSpendToDate =
    data.previousMonthTotal > 0 ? Math.round((data.previousMonthTotal / daysInPrevMonth) * dayOfMonth) : null
  const paceDelta = ghostSpendToDate === null ? null : data.totalSpent - ghostSpendToDate
  const ghostBalance = netWorth && ghostSpendToDate !== null ? netWorth.total - paceDelta! : null

  // The seasonal figure is only explainable relative to the flat projection it sits beside.
  const seasonalDiffPct =
    data.projectedMonthEndSeasonal !== null && data.projectedMonthEnd
      ? Math.round(((data.projectedMonthEndSeasonal - data.projectedMonthEnd) / data.projectedMonthEnd) * 100)
      : null

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
            <span className="figure text-[52px] leading-none sm:text-[60px]">
              {netWorth ? Math.floor(netWorth.total / 100).toLocaleString("en-US") : "—"}
            </span>
            <span className="figure text-[24px] text-[#C7CDD0]">
              {netWorth ? "." + String(netWorth.total % 100).padStart(2, "0") : ""}
            </span>
            <span className="text-muted-foreground mb-1 font-mono text-[14px]">RON</span>
          </div>
          <div className="text-muted-foreground mt-4 text-[13.5px]">
            {delta <= 0 ? "Down" : "Up"}{" "}
            <span className={delta <= 0 ? "text-good" : "text-destructive"}>
              {formatRon(Math.abs(delta)).replace(/\s?RON$/, "")} RON
            </span>{" "}
            on spend against last month
          </div>
        </div>
        <div className="ruled min-w-[210px] flex-1 basis-[240px] text-[14px]">
          <div className="flex items-baseline justify-between py-2.5">
            <span className="text-muted-foreground">Spent · {monthLabel(month).split(" ")[0]}</span>
            <span className="tnum text-destructive font-mono">
              −{formatRon(data.totalSpent).replace(/\s?RON$/, "")}
            </span>
          </div>
          {data.projectedMonthEnd !== null && (
            <div className="flex items-baseline justify-between py-2.5">
              <span className="text-muted-foreground">Projected month-end</span>
              <span className="tnum font-mono">{formatRon(data.projectedMonthEnd).replace(/\s?RON$/, "")}</span>
            </div>
          )}
          {data.projectedMonthEndSeasonal !== null && (
            <div className="flex items-baseline justify-between py-2.5">
              <span className="text-muted-foreground flex items-center gap-2">
                Seasonally adjusted
                {data.macroStale && <span className="status-tag edge-mark-accent text-primary py-0.5 pl-2">stale</span>}
              </span>
              <span className="tnum font-mono">
                {formatRon(data.projectedMonthEndSeasonal).replace(/\s?RON$/, "")}
              </span>
            </div>
          )}
          <div className="flex items-baseline justify-between py-2.5">
            <span className="text-muted-foreground">Entries logged</span>
            <span className="tnum font-mono">{data.expenseCount}</span>
          </div>
        </div>

        {ghostBalance !== null && paceDelta !== null && (
          <>
            <div className="hidden self-stretch sm:block" style={{ width: 1, background: "var(--border)" }} />
            <div className="max-w-[360px] min-w-[220px] flex-1 basis-[260px] self-start">
              <div className="flex items-center gap-2">
                <span className="bg-primary size-1.5 flex-none" />
                <span className="ledger-label !text-primary">Ghost balance</span>
              </div>
              <div className="figure mt-3 text-[28px] text-[#C7CDD0]">
                {formatRon(ghostBalance).replace(/\s?RON$/, "")}{" "}
                <span className="text-muted-foreground font-mono text-[13px]">RON</span>
              </div>
              <p className="text-muted-foreground mt-3 max-w-[320px] text-[13px] text-pretty">
                Where you'd stand if this month had kept last month's pace.
              </p>
              <div className="mt-3.5 flex items-center gap-2.5">
                <span className={`font-mono text-[13.5px] ${paceDelta <= 0 ? "text-good" : "text-destructive"}`}>
                  {formatRon(Math.abs(paceDelta)).replace(/\s?RON$/, "")} RON
                </span>
                <span className="text-muted-foreground text-[13px]">
                  {paceDelta <= 0 ? "under last month's pace" : "above last month's pace"}
                </span>
              </div>
            </div>
          </>
        )}
      </div>
      {data.projectedMonthEndSeasonal !== null && data.macroSeason && (
        <p className="text-muted-foreground mt-4 text-[12.5px] leading-relaxed">
          Seasonally adjusted {seasonalDiffPct !== null && (seasonalDiffPct >= 0 ? `+${seasonalDiffPct}` : seasonalDiffPct)}
          {seasonalDiffPct !== null && "% "}for {seasonLabel(data.macroSeason)}
          {data.macroSource && ` — via ${data.macroSource}`}
          {data.macroAsOfDate && `, as of ${data.macroAsOfDate}`}
          {data.macroStale && " (last known good — not refreshed recently)"}
        </p>
      )}
      <SpendTrajectory data={data} month={month} />
    </section>
  )
}

const GHOST_LINE = "rgba(154,212,227,0.35)"
const GHOST_GUIDE = "rgba(154,212,227,0.14)"

const runningTotals = (series: { amount: number }[]) => {
  let sum = 0
  return series.map((point) => (sum += point.amount))
}

/**
 * Cumulative spend for the month against the F3 ghost flock — the same month with
 * non-mandatory spend held at its trailing 3-month median. The ghost borrows the
 * ledger's faint guide-mark language so it reads as a reference the eye can dismiss,
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
        <line x1={padX} y1={H - padBottom} x2={VW - padX} y2={H - padBottom} stroke="var(--border)" strokeWidth="1" />

        {ghost && (
          <>
            {/* Guide marks every fifth day — the ledger bundles its notches in fives. */}
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

        {/* The recorded ledger: a dark cut with a lit edge, as the notches are carved. */}
        <polyline
          points={path(real, realThrough)}
          fill="none"
          stroke="#0E1113"
          strokeWidth="3.4"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <polyline
          points={path(real, realThrough)}
          fill="none"
          stroke="#9AD4E3"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>

      <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1.5">
        <span className="text-muted-foreground inline-flex items-center gap-2 text-[12.5px]">
          <svg width="18" height="6" aria-hidden="true">
            <line x1="0" y1="3" x2="18" y2="3" stroke="#9AD4E3" strokeWidth="2" strokeLinecap="round" />
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
  const maxSlice = Math.max(1, ...slices.map((s) => s.amount))

  return (
    <section className="ledger-card p-6">
      <div className="mb-5.5 flex items-center gap-2.5">
        <HornGlyph className="text-primary size-5.5" />
        <h2 className="text-[18px] font-semibold">Where your money goes</h2>
      </div>
      <div className="grid gap-14 [grid-template-columns:repeat(auto-fit,minmax(300px,1fr))]">
        {/* Category share bars */}
        <div className="min-w-0">
          <div className="ledger-label mb-4.5">Spend by category</div>
          {slices.length === 0 ? (
            <p className="text-muted-foreground py-8 text-sm">No spending recorded this month.</p>
          ) : (
            <div className="flex flex-col gap-4">
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
                    className="cursor-pointer"
                  >
                    <div className="flex items-center justify-between text-[14.5px]">
                      <span className={`flex items-center gap-2 font-medium ${sel ? "text-primary" : ""}`}>
                        <span className="size-1.5 flex-none" style={{ background: s.color }} />
                        {s.category}
                      </span>
                      <span className="flex items-baseline gap-2">
                        <span className="figure">{formatRon(s.amount).replace(/\s?RON$/, "")}</span>
                        <span className="tnum text-muted-foreground w-8 text-right font-mono text-[12px]">{pct}%</span>
                      </span>
                    </div>
                    <div className="border-border mt-2 h-1.5 border">
                      <div
                        className="h-full transition-[width]"
                        style={{
                          width: `${Math.max(4, Math.round((s.amount / maxSlice) * 100))}%`,
                          background: s.color,
                          opacity: active && !sel ? 0.5 : 1,
                        }}
                      />
                    </div>
                  </div>
                )
              })}
              <p className="text-muted-foreground mt-0.5 text-[13px]">
                Click a category to open it in Expenses
              </p>
            </div>
          )}
          {slice && activeSlice && (
            <div className="ledger-card mt-4 p-5" style={{ animation: "riseIn .16s ease" }}>
              <div className="flex items-center justify-between">
                <div className="text-[15.5px] font-semibold">{activeSlice.category}</div>
                <button
                  onClick={() => setSlice(null)}
                  aria-label={`Close ${activeSlice.category} details`}
                  className="text-muted-foreground grid size-11 cursor-pointer place-items-center text-lg leading-none hover:bg-popover"
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
                className="mt-4 min-h-11 w-full cursor-pointer border border-[#4C93A6] bg-[#123945] py-2.5 text-[13.5px] font-medium text-[#C4E7F0] hover:bg-[#174756]"
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
                    className="-mx-3 flex min-h-11 items-center gap-3.5 px-3 py-2.75 text-left transition-colors hover:bg-popover"
                  >
                    <div className="text-muted-foreground bg-popover grid size-[38px] flex-none place-items-center font-mono text-[14px]">
                      {monogram(label)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[14.5px] font-medium">{label}</div>
                      <div className="text-muted-foreground font-mono text-[12px]">
                        {categoryLabel(categories, t.categoryId)} · {shortDate(t.expenseDate)}
                      </div>
                    </div>
                    <span className="figure text-foreground text-[14px]">−{formatRon(t.amount).replace(/\s?RON$/, "")}</span>
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
        <span className="figure text-[36px]">
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

/** A tally: one notch per on-budget day. */
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
            const colorClass = hot ? "text-destructive" : "text-primary"
            return (
              <div key={q.id}>
                <div className="flex items-baseline justify-between gap-2.5">
                  <span className={`text-[14.5px] font-medium ${hot ? colorClass : ""}`}>{q.title}</span>
                  <span className={`tnum font-mono text-[13px] ${hot ? colorClass : ""}`}>
                    {q.kind === "DAYS"
                      ? `${q.progress}/${q.target}`
                      : `${Math.round(q.progress / 100)} / ${Math.round(q.target / 100)}`}
                  </span>
                </div>
                <div className="border-border mt-2.5 h-1.5 border">
                  <div
                    className={`h-full transition-[width] ${hot ? "bg-destructive" : "bg-primary"}`}
                    style={{ width: `${pct}%` }}
                  />
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
      stroke="#9AD4E3"
      strokeWidth={7}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-full"
      aria-hidden="true"
    >
      <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
      <circle cx="32" cy="52" r="4" fill="#9AD4E3" stroke="none" />
    </svg>
  )
}

function DashSkeletonNote() {
  return (
    <div className="flex flex-col items-center justify-center gap-4 py-24">
      <div className="text-primary size-14" style={{ animation: "shimmer 1.6s ease-in-out infinite" }}>
        <svg viewBox="-8 -13 116 116" fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" className="size-full" aria-hidden="true">
          <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
          <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div className="ledger-label">Loading your money</div>
    </div>
  )
}
