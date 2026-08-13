import { useCallback, useEffect, useMemo, useState } from "react"
import {
  formatRon,
  getCalendar,
  getDashboard,
  getDashboardLayout,
  getNetWorth,
  listExpenses,
  listOaths,
  listQuests,
  listSavings,
  saveDashboardLayout,
  type Category,
  type Dashboard,
  type Expense,
  type GoalCalendar,
  type NetWorth,
  type Oath,
  type Quest,
  type SavingsAccount,
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
  const [savings, setSavings] = useState<SavingsAccount[]>([])
  const [oaths, setOaths] = useState<Oath[]>([])
  const [error, setError] = useState<string | null>(null)
  const [slowLoad, setSlowLoad] = useState(false)

  const [layout, setLayout] = useState<Layout>(DEFAULT_LAYOUT)
  const [arranging, setArranging] = useState(false)
  const [layoutError, setLayoutError] = useState<string | null>(null)
  const [dragging, setDragging] = useState<{ column: Column; index: number } | null>(null)

  const loadDashboard = useCallback(() => {
    setError(null)
    getDashboard(month)
      .then((d) => {
        setData(d)
        setError(null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [month])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  // "Still fetching?" affordance — a plain retry, not a spinner that runs forever.
  useEffect(() => {
    if (data) return
    setSlowLoad(false)
    const t = setTimeout(() => setSlowLoad(true), 6000)
    return () => clearTimeout(t)
  }, [data, month])

  useEffect(() => {
    getNetWorth().then(setNetWorth).catch(() => setNetWorth(null))
    listQuests().then(setQuests).catch(() => setQuests([]))
    getCalendar().then(setCalendar).catch(() => setCalendar(null))
    listExpenses({}).then((p) => setRecent(p.items.slice(0, 4))).catch(() => setRecent([]))
    listSavings().then(setSavings).catch(() => setSavings([]))
    listOaths().then(setOaths).catch(() => setOaths([]))
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

  if (error)
    return (
      <div className="flex flex-col items-center gap-3 py-8 text-center">
        <p className="text-destructive text-sm">We could not read your ledger — {error}</p>
        <button
          onClick={loadDashboard}
          className="text-primary min-h-11 cursor-pointer px-3 text-[13.5px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
        >
          Try again
        </button>
      </div>
    )
  if (!data) return <DashSkeletonNote slow={slowLoad} onRetry={loadDashboard} />

  const widgets: Record<WidgetId, React.ReactNode> = {
    balance: <BalanceWidget month={month} data={data} netWorth={netWorth} calendar={calendar} />,
    breakdown: (
      <BreakdownWidget
        data={data}
        slices={slices}
        categories={categories}
        recent={recent}
        onNavigate={onNavigate}
      />
    ),
    savings: <SavingsWidget netWorth={netWorth} savings={savings} />,
    streak: <StreakWidget greenDays={greenDays} />,
    quests: (
      <QuestsWidget activeQuests={activeQuests} oaths={oaths} onNavigate={onNavigate} />
    ),
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
  calendar,
}: {
  month: string
  data: Dashboard
  netWorth: NetWorth | null
  calendar: GoalCalendar | null
}) {
  const delta = data.totalSpent - data.previousMonthTotal

  // Flow row — came in / went out / left to spend — mirrors the ledger's own
  // three-line summary rather than restating totalSpent a second way.
  const income = netWorth?.monthlyIncome ?? null
  const leftToSpend = income !== null ? income - data.totalSpent : null

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
              {netWorth ? Math.floor(netWorth.total / 100).toLocaleString("ro-RO") : "—"}
            </span>
            <span className="figure text-[24px] text-[#C7CDD0]">
              {netWorth ? "." + String(netWorth.total % 100).padStart(2, "0") : ""}
            </span>
            <span className="text-muted-foreground mb-1 font-mono text-[14px]">RON</span>
          </div>

          {income !== null && (
            <div className="mt-5 flex gap-8">
              <div>
                <div className="ledger-label">came in</div>
                <div className="figure text-good mt-1.5 text-[21px]">
                  {formatRon(income).replace(/\s?RON$/, "")}
                </div>
              </div>
              <div>
                <div className="ledger-label">went out</div>
                <div className="figure mt-1.5 text-[21px]">
                  {formatRon(data.totalSpent).replace(/\s?RON$/, "")}
                </div>
              </div>
              {leftToSpend !== null && (
                <div>
                  <div className="ledger-label">left to spend</div>
                  <div className="figure mt-1.5 text-[21px] text-[#C7CDD0]">
                    {formatRon(Math.abs(leftToSpend)).replace(/\s?RON$/, "")}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="text-muted-foreground mt-4 text-[13.5px]">
            {delta <= 0 ? "Down" : "Up"}{" "}
            <span className={delta <= 0 ? "text-good" : "text-destructive"}>
              {formatRon(Math.abs(delta)).replace(/\s?RON$/, "")} RON
            </span>{" "}
            on spend against last month
          </div>
        </div>
        <div className="ruled min-w-[210px] flex-1 basis-[240px] text-[14px]">
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
      <Tally data={data} month={month} calendar={calendar} />
    </section>
  )
}

const DAY_OK = "#9AD4E3"
const DAY_OVER = "#E09880"
const DAY_EMPTY = "#2A3033"

/**
 * Tally — a day-by-day bar chart of this month's spend, colored by the calendar's
 * own MET/MISSED read of that day, with the F3 ghost flock's daily figures ruled
 * in beneath as a faint comparison row. Replaces a cumulative line with the
 * ledger's own notch language: one mark per day, not a trend to read tea leaves in.
 */
function Tally({
  data,
  month,
  calendar,
}: {
  data: Dashboard
  month: string
  calendar: GoalCalendar | null
}) {
  const [selectedDay, setSelectedDay] = useState<string | null>(null)

  const days = data.byDay.length
  if (days === 0) return null

  const statusByDate = new Map((calendar?.days ?? []).map((d) => [d.date, d.status]))
  const ghostByDate = new Map((data.ghostByDay ?? []).map((d) => [d.date, d.amount]))

  const peak = Math.max(...data.byDay.map((d) => d.amount), ...(data.ghostByDay?.map((d) => d.amount) ?? [0]), 1)
  const daysEntered = data.byDay.filter((d) => d.amount > 0).length
  const hasGhost = data.ghostByDay !== null

  const spent = formatRon(data.totalSpent).replace(/\s?RON$/, "")
  const baseline = formatRon(data.ghostMonthTotal ?? 0).replace(/\s?RON$/, "")
  const selectedEntry = data.byDay.find((d) => d.date === selectedDay) ?? null

  return (
    <div className="mt-7">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3">
        <div className="flex items-baseline gap-3.5">
          <span className="ledger-label">Tally · {monthLabel(month).split(" ")[0]}</span>
          <span className="text-muted-foreground text-[13px]">{daysEntered} days recorded</span>
        </div>
        <div className="flex gap-4">
          <TallyLegendItem color={DAY_OK} label="on pace" />
          <TallyLegendItem color={DAY_OVER} label="over" />
          <TallyLegendItem color={DAY_EMPTY} label="not yet" />
        </div>
      </div>

      <p className="sr-only">
        {hasGhost
          ? `Recorded spend of ${spent} RON so far this month, against a ghost flock baseline of ${baseline} RON by month end`
          : `Recorded spend of ${spent} RON so far this month, across ${daysEntered} recorded days`}
      </p>
      <div className="flex items-center gap-3.5">
        <div className="w-[74px] flex-none">
          <div className="text-[12.5px]">this month</div>
          <div className="text-muted-foreground mt-0.5 font-mono text-[10.5px]">{daysEntered} days</div>
        </div>
        <div className="border-border flex h-16 flex-1 items-end gap-0 border-b">
          {data.byDay.map((d) => {
            const status = statusByDate.get(d.date)
            const color = d.amount === 0 ? DAY_EMPTY : status === "MISSED" ? DAY_OVER : DAY_OK
            const heightPct = d.amount === 0 ? 8 : Math.round(22 + (d.amount / peak) * 68)
            const isSelected = selectedDay === d.date
            return (
              <button
                key={d.date}
                type="button"
                aria-pressed={isSelected}
                aria-label={`${shortDate(d.date)} · ${formatRon(d.amount)}${status === "MISSED" ? " · over" : status === "MET" ? " · on pace" : ""}`}
                title={`${d.date} · ${formatRon(d.amount)}`}
                onClick={() => setSelectedDay(isSelected ? null : d.date)}
                className="flex h-full flex-1 cursor-pointer items-end justify-center border-none bg-transparent p-0 focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
                style={{ boxShadow: isSelected ? "inset 0 -2px 0 0 var(--primary)" : "none" }}
              >
                <span
                  className="block w-[70%]"
                  style={{ height: `${heightPct}%`, background: color, opacity: isSelected ? 1 : 0.92 }}
                />
              </button>
            )
          })}
        </div>
      </div>
      {selectedEntry && (
        <p className="mt-2 pl-[86px] text-[12.5px]">
          <span className="text-foreground font-mono">{shortDate(selectedEntry.date)}</span>
          <span className="text-muted-foreground">
            {" "}
            · {formatRon(selectedEntry.amount)}
            {(() => {
              const st = statusByDate.get(selectedEntry.date)
              return st === "MISSED" ? " · over" : st === "MET" ? " · on pace" : ""
            })()}
          </span>
        </p>
      )}

      {hasGhost && (
        <div className="mt-1.5 flex items-center gap-3.5">
          <div className="w-[74px] flex-none">
            <div className="text-[12.5px] text-[#C7CDD0]">ghost flock</div>
          </div>
          <div className="border-border flex h-8 flex-1 items-end gap-0 border-b" aria-hidden="true">
            {data.byDay.map((d) => {
              const g = ghostByDate.get(d.date) ?? 0
              const heightPct = g === 0 ? 10 : Math.round(14 + (g / peak) * 60)
              return (
                <div key={d.date} className="flex h-full flex-1 items-end justify-center">
                  <span className="text-muted-foreground block w-[70%]" style={{ height: `${heightPct}%`, background: "currentColor", opacity: 0.55 }} />
                </div>
              )
            })}
          </div>
        </div>
      )}

      <div className="mt-1.5 flex pl-[86px]">
        {data.byDay.map((d) => {
          const dayNum = Number(d.date.slice(-2))
          const show = dayNum === 1 || dayNum % 5 === 0
          return (
            <div key={d.date} className="text-muted-foreground flex-1 text-center font-mono text-[9px]">
              {show ? dayNum : ""}
            </div>
          )
        })}
      </div>

      <p className="text-muted-foreground mt-3 text-[12.5px] leading-relaxed">
        {hasGhost
          ? `The ghost flock is this same month with your non-mandatory spending held at its trailing 3-month median — ${baseline} RON by month end.`
          : "The ghost flock — this month with your non-mandatory spending held at its 3-month median — unlocks once you have three months of history."}
      </p>
    </div>
  )
}

function TallyLegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className="text-muted-foreground flex items-center gap-1.5 font-mono text-[10.5px] tracking-[0.1em] uppercase">
      <span className="h-[11px] w-px flex-none" style={{ background: color }} />
      {label}
    </span>
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
                  <button
                    key={s.category}
                    type="button"
                    aria-pressed={sel}
                    onClick={() => setSlice(sel ? null : s.category)}
                    onMouseEnter={() => setHover(s.category)}
                    onMouseLeave={() => setHover(null)}
                    className="w-full cursor-pointer border-none bg-transparent p-0 text-left focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
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
                  </button>
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
          {recent.length === 0 ? (
            <p className="text-muted-foreground py-4 text-sm">Nothing yet — add your first expense.</p>
          ) : (
            <div className="flex flex-col">
              <div className="flex items-baseline justify-between pb-1">
                <span className="ledger-label !text-[10px]">Entry</span>
                <span className="ledger-label !text-[10px]">Amount</span>
              </div>
              {recent.map((t, i) => {
                const label = t.note || topName(categories, t.categoryId)
                return (
                  <button
                    key={t.id}
                    onClick={() => onNavigate?.("expenses")}
                    className="border-border flex h-11 min-h-11 w-full cursor-pointer items-baseline gap-3.5 border-b text-left transition-colors last:border-b-0 hover:bg-popover"
                  >
                    <span className="text-muted-foreground w-6 flex-none font-mono text-[11px]">
                      {String(recent.length - i).padStart(2, "0")}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-[14.5px]">{label}</span>
                    <span className="text-muted-foreground hidden flex-none font-mono text-[11px] tracking-[0.08em] uppercase sm:inline">
                      {categoryLabel(categories, t.categoryId)}
                    </span>
                    <span className="text-muted-foreground w-14 flex-none text-right font-mono text-[11px]">
                      {shortDate(t.expenseDate)}
                    </span>
                    <span className="figure text-foreground w-24 flex-none text-right text-[14.5px]">
                      −{formatRon(t.amount).replace(/\s?RON$/, "")}
                    </span>
                  </button>
                )
              })}
              <div className="mt-1 flex h-11 items-baseline gap-3.5 pt-1">
                <span className="w-6 flex-none" />
                <span className="ledger-label flex-1">Shown here</span>
                <span className="figure w-24 flex-none text-right text-[14.5px]">
                  −{formatRon(recent.reduce((sum, t) => sum + t.amount, 0)).replace(/\s?RON$/, "")}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}

function SavingsWidget({ netWorth, savings }: { netWorth: NetWorth | null; savings: SavingsAccount[] }) {
  return (
    <section className="ledger-card p-6">
      <div className="flex items-center gap-2.5">
        <HornGlyph className="text-primary size-5" />
        <h2 className="text-[17px] font-semibold">Net worth · recorded</h2>
      </div>
      <div className="mt-4 flex items-baseline gap-2.5">
        <span className="figure text-[36px]">
          {netWorth ? formatRon(netWorth.total).replace(/\s?RON$/, "") : "—"}
        </span>
        <span className="text-muted-foreground text-[14.5px]">RON</span>
      </div>
      {savings.length > 0 ? (
        <div className="ruled mt-4">
          {savings.map((s) => (
            <div key={s.id} className="flex items-center justify-between gap-3 py-2.5">
              <span className="min-w-0 truncate text-[14px] text-[#C7CDD0]">{s.name}</span>
              <span className="figure flex-none text-[14px]">{formatRon(s.balance).replace(/\s?RON$/, "")}</span>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-muted-foreground mt-4 text-[13.5px]">No savings pots recorded yet.</p>
      )}
      <p className="text-muted-foreground mt-4 text-[13px]">
        Figures you keep yourself. Argali never touches the money.
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

/** A row of notches filled to `pct` — the ledger's own texture for "how far along," in place of a smooth bar. */
function Notches({ pct, hot }: { pct: number; hot: boolean }) {
  const total = 18
  const filled = Math.round((pct / 100) * total)
  return (
    <div className="mt-2.5 flex h-6 items-end gap-[2px]" aria-hidden="true">
      {Array.from({ length: total }, (_, i) => (
        <span
          key={i}
          className="flex-1"
          style={{
            height: `${28 + (i % 3) * 10}%`,
            background: i < filled ? (hot ? "var(--destructive)" : "var(--primary)") : "var(--border)",
          }}
        />
      ))}
    </div>
  )
}

function QuestsWidget({
  activeQuests,
  oaths,
  onNavigate,
}: {
  activeQuests: Quest[]
  oaths: Oath[]
  onNavigate?: (page: string, categoryName?: string) => void
}) {
  const openOaths = oaths.filter((o) => o.status === "OPEN").slice(0, 3)

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
      <div className="flex flex-col gap-5">
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
                <Notches pct={pct} hot={hot} />
              </div>
            )
          })
        )}
      </div>

      {openOaths.length > 0 && (
        <>
          <div className="bg-border my-5 h-px" />
          <div className="mb-3.5 flex items-baseline justify-between">
            <span className="ledger-label">Open oaths</span>
            <span className="text-muted-foreground text-[11px]">{openOaths.length} · pending</span>
          </div>
          <div className="flex flex-col gap-3.5">
            {openOaths.map((o) => {
              const msLeft = new Date(o.expiresAt).getTime() - Date.now()
              const totalMs = new Date(o.expiresAt).getTime() - new Date(o.createdAt).getTime()
              const urgent = msLeft <= 6 * 3_600_000
              const hoursLeft = Math.max(0, Math.floor(msLeft / 3_600_000))
              const daysLeft = Math.floor(hoursLeft / 24)
              const countdown = msLeft <= 0 ? "closing" : daysLeft > 0 ? `${daysLeft}d left` : `${hoursLeft}h left`
              const pctLeft = totalMs > 0 ? Math.max(4, Math.min(100, Math.round((msLeft / totalMs) * 100))) : 4
              const barColor = urgent ? "var(--destructive)" : "var(--primary)"
              return (
                <div
                  key={o.id}
                  className="pb-3.5 pl-3 last:pb-0"
                  style={{ boxShadow: `inset 2px 0 0 0 ${barColor}` }}
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="text-[14px]">
                      Under {formatRon(o.pledgedAmount).replace(/\s?RON$/, "")} RON on {o.categoryName}
                    </span>
                    <span
                      className="flex-none font-mono text-[10.5px] tracking-[0.1em] uppercase"
                      style={{ color: barColor, animation: urgent ? "shimmer 1.6s ease-in-out infinite" : "none" }}
                    >
                      {countdown}
                    </span>
                  </div>
                  <div className="mt-2.5 h-px" style={{ background: "color-mix(in srgb, var(--foreground) 8%, transparent)" }}>
                    <div className="h-px" style={{ width: `${pctLeft}%`, background: barColor }} />
                  </div>
                </div>
              )
            })}
          </div>
        </>
      )}
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

/** Notch heights standing in for a count that never climbs from zero — "a figure that climbs is a figure that lies." */
const BOOT_NOTCHES = [
  { h: "100%", color: "#9AD4E3" },
  { h: "72%", color: "#9AD4E3" },
  { h: "88%", color: "#9AD4E3" },
  { h: "60%", color: "#4C93A6" },
  { h: "80%", color: "#4C93A6" },
  { h: "40%", color: "#62696D" },
  { h: "40%", color: "#62696D" },
]

function DashSkeletonNote({ slow, onRetry }: { slow: boolean; onRetry: () => void }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex flex-col items-center justify-center gap-[18px] py-24"
    >
      <div className="text-primary size-14">
        <svg viewBox="-8 -13 116 116" fill="none" stroke="currentColor" strokeWidth={7} strokeLinecap="round" strokeLinejoin="round" className="size-full" aria-hidden="true">
          <path d="M86 78H38L10 60V50L48 12A24 24 0 1 1 65 53A14 14 0 0 1 65 25A7 7 0 0 1 65 39" />
          <circle cx="32" cy="52" r="4" fill="currentColor" stroke="none" />
        </svg>
      </div>
      <div className="flex h-[22px] items-end gap-[3px]" aria-hidden="true">
        {BOOT_NOTCHES.map((n, i) => (
          <span key={i} className="w-[2px]" style={{ height: n.h, background: n.color }} />
        ))}
      </div>
      <div className="ledger-label" style={{ animation: "shimmer 2.4s ease-in-out infinite" }}>
        Counting your notches
      </div>
      {slow && (
        <div className="mt-1 flex flex-col items-center gap-2">
          <p className="text-muted-foreground text-[13px]">Taking longer than usual.</p>
          <button
            onClick={onRetry}
            className="text-primary min-h-11 cursor-pointer px-3 text-[13.5px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          >
            Try again
          </button>
        </div>
      )}
    </div>
  )
}
