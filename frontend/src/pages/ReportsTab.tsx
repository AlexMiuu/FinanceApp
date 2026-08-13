import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react"
import {
  createReport,
  deleteReport,
  downloadReportCsv,
  formatRon,
  listReports,
  runReport,
  type Category,
  type Report,
  type ReportResult,
} from "@/lib/api"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const CATEGORY_BAR = ["#9AD4E3", "#8FC7A6", "#4C93A6", "#E09880", "#8A9399", "#62696D"]

const formatTime = (d: Date) => d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })

export default function ReportsTab({ categories }: { categories: Category[] }) {
  const toast = useToast()
  const [reports, setReports] = useState<Report[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [results, setResults] = useState<Record<string, ReportResult>>({})
  const [creating, setCreating] = useState(false)
  const [filterCategory, setFilterCategory] = useState<string>()
  const [hoverPoint, setHoverPoint] = useState<number | null>(null)
  const [expandedCategory, setExpandedCategory] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [lastReadAt, setLastReadAt] = useState<Date | null>(null)

  const reload = useCallback(() => {
    setLoading(true)
    setLoadError(null)
    listReports()
      .then((list) => {
        setReports(list)
        setResults(
          Object.fromEntries(
            list.filter((r) => r.cachedResult).map((r) => [r.id, r.cachedResult as ReportResult])
          )
        )
        setSelected((prev) => prev ?? list[0]?.id ?? null)
        setLastReadAt(new Date())
      })
      .catch((e) =>
        setLoadError(
          e instanceof Error ? `We could not read your reports — ${e.message}` : "We could not read your reports"
        )
      )
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  const report = reports.find((r) => r.id === selected) ?? null
  const result = report ? results[report.id] : undefined

  const monthPoints = useMemo(() => {
    if (!result) return []
    return Object.entries(result.byMonth)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .slice(-7)
      .map(([month, amount]) => ({ month: month.slice(2), amount: amount / 100 }))
  }, [result])

  const categoryRows = useMemo(() => {
    if (!result) return []
    return Object.entries(result.byCategory)
      .sort((a, b) => b[1] - a[1])
      .map(([name, amount], i) => ({
        name,
        amount,
        pct: result.totalSpent > 0 ? Math.round((amount / result.totalSpent) * 100) : 0,
        color: CATEGORY_BAR[i % CATEGORY_BAR.length],
      }))
  }, [result])

  const biggest = categoryRows[0]

  // Naive linear projection from the last two shown months — same shape as the
  // dashboard's month-end projection, scoped to this report's own trend.
  const projectedNext = useMemo(() => {
    if (monthPoints.length < 2) return null
    const last = monthPoints[monthPoints.length - 1].amount
    const prev = monthPoints[monthPoints.length - 2].amount
    const delta = last - prev
    return { amount: Math.max(0, Math.round(last + delta)), delta: Math.round(delta) }
  }, [monthPoints])

  function describeFilters(r: Report): string {
    const parts: string[] = []
    if (r.filters.from) parts.push(`from ${r.filters.from}`)
    if (r.filters.to) parts.push(`to ${r.filters.to}`)
    if (r.filters.categoryIds?.length) {
      parts.push(
        r.filters.categoryIds.map((id) => categories.find((c) => c.id === id)?.name ?? "?").join(", ")
      )
    }
    return parts.length ? parts.join(" · ") : "all expenses"
  }

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = e.currentTarget
    const data = new FormData(form)
    try {
      const created = await createReport(String(data.get("name")), {
        from: String(data.get("from")) || null,
        to: String(data.get("to")) || null,
        categoryIds: filterCategory ? [filterCategory] : null,
      })
      form.reset()
      setFilterCategory(undefined)
      setCreating(false)
      setSelected(created.id)
      toast("Report saved")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed")
    }
  }

  async function run(id: string) {
    try {
      const r = await runReport(id)
      setResults((prev) => ({ ...prev, [id]: r }))
      toast("Report re-evaluated")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Run failed")
    }
  }

  async function exportRow(r: Report) {
    try {
      await downloadReportCsv(r.id, r.name)
      toast("CSV exported — check your downloads")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Export failed")
    }
  }

  const card = "ledger-card p-6"

  return (
    <div className="flex flex-col gap-[22px]">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid items-start gap-[22px] lg:grid-cols-[340px_1fr]">
        {/* Saved reports */}
        <section className={card}>
          <div className="mb-3 flex items-center justify-between">
            <span className="ledger-label">Saved reports</span>
            <button
              onClick={() => setCreating((v) => !v)}
              className="text-primary cursor-pointer text-[13px]"
            >
              {creating ? "Close" : "+ New"}
            </button>
          </div>

          {creating && (
            <form className="border-border bg-secondary mb-3 space-y-2.5 border p-4" onSubmit={submit}>
              <div className="space-y-1">
                <Label htmlFor="rname">Name</Label>
                <Input id="rname" name="name" required maxLength={100} placeholder="July food spending" />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <Input name="from" type="date" aria-label="From" className="font-mono" />
                <Input name="to" type="date" aria-label="To" className="font-mono" />
              </div>
              <CategorySelect categories={categories} value={filterCategory} onChange={setFilterCategory} allowAll />
              <button
                type="submit"
                className="w-full cursor-pointer border border-[#4C93A6] bg-[#123945] py-2.5 text-[13.5px] font-semibold text-[#C4E7F0] hover:bg-[#174756]"
              >
                Save report
              </button>
            </form>
          )}

          {loading && reports.length === 0 ? (
            <div role="status" aria-live="polite" className="flex flex-col gap-2 py-1">
              <span className="sr-only">Reading your reports</span>
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="flex items-center gap-3.5 p-3.5"
                  style={{ animation: `shimmer 1.6s ease-in-out ${(i * 0.22).toFixed(2)}s infinite` }}
                >
                  <span className="h-5 w-[3px] flex-none bg-white/15" aria-hidden />
                  <span className="h-px flex-1 bg-white/15" style={{ maxWidth: `${60 - i * 12}%` }} aria-hidden />
                  <span className="h-px w-10 flex-none bg-white/10" aria-hidden />
                </div>
              ))}
            </div>
          ) : loadError && reports.length === 0 ? (
            <div
              role="alert"
              className="my-2 flex flex-col gap-3 border border-destructive/50 bg-white/[0.02] px-4 py-5 shadow-[inset_2px_0_0_0_var(--destructive)]"
            >
              <p className="text-[14.5px] font-semibold">We could not read your reports</p>
              <p className="text-muted-foreground text-[12.5px] text-pretty">
                Nothing was lost — this is a reading problem, not a money problem.
                {lastReadAt ? ` Last read ${formatTime(lastReadAt)}.` : ""}
              </p>
              <button
                onClick={() => reload()}
                className="cursor-pointer self-start border border-[#4C93A6] bg-[#123945] px-4 py-2 text-[13px] font-medium text-[#C4E7F0] hover:bg-[#174756]"
              >
                Try again
              </button>
            </div>
          ) : (
            <>
              {reports.length === 0 && !creating && (
                <p className="text-muted-foreground py-4 text-sm text-pretty">
                  No saved reports yet. Set a range and a category below, then save the view.
                </p>
              )}
              <div className="flex flex-col gap-1">
                {reports.map((r) => {
                  const sel = selected === r.id
                  const cached = results[r.id]
                  const barColor = sel ? "#9AD4E3" : "#62696D"
                  return (
                    <div
                      key={r.id}
                      className={`flex items-center gap-2 pr-2 transition-colors ${
                        sel ? "bg-white/[0.05] edge-mark-accent" : "hover:bg-white/[0.03]"
                      }`}
                    >
                      <button
                        onClick={() => setSelected(r.id)}
                        className="flex min-w-0 flex-1 cursor-pointer items-center gap-3.5 p-3.5 text-left"
                      >
                        <span className="flex flex-none items-end gap-[3px]" aria-hidden="true">
                          <span className="block h-2 w-[3px]" style={{ background: barColor }} />
                          <span className="block h-3.5 w-[3px]" style={{ background: barColor }} />
                          <span className="block h-5 w-[3px]" style={{ background: barColor }} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <p className="truncate text-[13.5px] font-medium">{r.name}</p>
                          <p className="text-muted-foreground mt-0.5 truncate text-[12.5px]">{describeFilters(r)}</p>
                        </span>
                        <span className="flex-none text-right">
                          <span className="figure block text-[13px]">
                            {cached ? formatRon(cached.totalSpent).replace(/\s?RON$/, "") : "not run"}
                          </span>
                          {sel && <span className="ledger-label text-primary mt-0.5 block">Viewing</span>}
                        </span>
                      </button>
                      <button
                        onClick={() => exportRow(r)}
                        aria-label={`Export ${r.name}`}
                        className="status-tag text-muted-foreground hover:text-foreground min-h-[36px] flex-none cursor-pointer px-2"
                      >
                        Export
                      </button>
                    </div>
                  )
                })}
              </div>
            </>
          )}
        </section>

        {/* Selected report analytics */}
        <div className="flex flex-col gap-[22px]">
          {!report ? (
            <section className={card}>
              <p className="text-muted-foreground py-10 text-center text-sm" aria-busy={loading}>
                {loading ? "Reading your reports…" : "Select or create a report on the left."}
              </p>
            </section>
          ) : (
            <>
              <section className={card}>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <h2 className="text-[18px] font-semibold">{report.name}</h2>
                    <p className="text-muted-foreground mt-0.5 text-xs">
                      {describeFilters(report)}
                      {report.lastRunAt && ` · last run ${new Date(report.lastRunAt).toLocaleDateString("en-GB")}`}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => run(report.id)}
                      className="border-border text-foreground/85 hover:text-foreground cursor-pointer border bg-transparent px-3.5 py-2 text-[13px] hover:border-[#4C93A6]"
                    >
                      Run
                    </button>
                    <button
                      onClick={() =>
                        downloadReportCsv(report.id, report.name)
                          .then(() => toast("CSV exported — check your downloads"))
                          .catch((e) => setError(e instanceof Error ? e.message : "Export failed"))
                      }
                      className="border-border text-foreground/85 hover:text-foreground cursor-pointer border bg-transparent px-3.5 py-2 text-[13px] hover:border-[#4C93A6]"
                    >
                      Export CSV
                    </button>
                    <button
                      onClick={() => deleteReport(report.id).then(() => { setSelected(null); reload() })}
                      className="text-destructive border-destructive/40 hover:border-destructive hover:bg-destructive/10 cursor-pointer border bg-transparent px-3.5 py-2 text-[13px]"
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </section>

              {!result ? (
                <section className={card}>
                  <p className="text-muted-foreground py-10 text-center text-sm">
                    Hit <span className="text-foreground font-medium">Run</span> to evaluate this report.
                  </p>
                </section>
              ) : (
                <>
                  {/* KPI tiles */}
                  <div className="flex flex-wrap gap-[22px]">
                    {[
                      { label: "Total spent", value: formatRon(result.totalSpent).replace(/\s?RON$/, ""), sub: "RON", tone: "#9AA3A8" },
                      { label: "Expenses counted", value: String(result.expenseCount), sub: "transactions", tone: "#8FC7A6" },
                      { label: "Biggest category", value: biggest?.name ?? "—", sub: biggest ? `${biggest.pct}% of spend` : "", tone: "#9AD4E3" },
                    ].map((k) => (
                      <section key={k.label} className="ledger-card min-w-[230px] flex-1 p-6">
                        <div className="ledger-label">{k.label}</div>
                        <div className="figure mt-2.5 text-[32px]">{k.value}</div>
                        <div className="mt-2.5 font-mono text-[13px]" style={{ color: k.tone }}>{k.sub}</div>
                      </section>
                    ))}
                  </div>

                  {/* Month against month */}
                  {monthPoints.length > 1 && (
                    <section className={card}>
                      <h2 className="mb-1 text-[18px] font-semibold">Month against month</h2>
                      <p className="text-muted-foreground mb-3 text-[13px]">Last {monthPoints.length} months</p>
                      <MonthBars points={monthPoints} hover={hoverPoint} onHover={setHoverPoint} />
                      {projectedNext && (
                        <div className="border-border mt-6 border-t pt-5">
                          <div className="ledger-label">Projected next month</div>
                          <div className="figure mt-2.5 text-[22px]">
                            {formatRon(projectedNext.amount).replace(/\s?RON$/, "")}
                          </div>
                          <p className="text-muted-foreground mt-2 text-[12.5px] text-pretty">
                            {projectedNext.delta === 0
                              ? "Flat on this month's pace."
                              : `${projectedNext.delta > 0 ? "Up" : "Down"} ${formatRon(
                                  Math.abs(projectedNext.delta)
                                ).replace(/\s?RON$/, "")} on this month's pace.`}
                          </p>
                        </div>
                      )}
                    </section>
                  )}

                  {/* By category */}
                  <section className={card}>
                    <h2 className="mb-5.5 text-[18px] font-semibold">By category</h2>
                    <div className="flex flex-col gap-4.5">
                      {categoryRows.map((row, i) => {
                        const isOpen = expandedCategory === row.name
                        return (
                          <div key={row.name}>
                            <button
                              type="button"
                              onClick={() => setExpandedCategory(isOpen ? null : row.name)}
                              aria-expanded={isOpen}
                              className="w-full cursor-pointer text-left"
                            >
                              <div className="flex items-center justify-between text-[14.5px]">
                                <span className="flex items-center gap-2.5 font-medium">
                                  <span
                                    className="inline-block size-2.5 flex-none"
                                    style={{ background: row.color }}
                                  />
                                  {row.name}
                                </span>
                                <span className="figure">{formatRon(row.amount).replace(/\s?RON$/, "")}</span>
                              </div>
                              <div className="bg-border mt-2.5 h-px w-full">
                                <div className="h-px" style={{ width: `${row.pct}%`, background: row.color }} />
                              </div>
                            </button>
                            {isOpen && (
                              <div
                                className="ledger-card mt-2.5 flex flex-wrap gap-6 p-4"
                                style={{ animation: "riseIn .16s ease" }}
                              >
                                <div className="min-w-[100px]">
                                  <div className="ledger-label">Share of total</div>
                                  <div className="tnum mt-1.5 text-[14.5px]">{row.pct}%</div>
                                </div>
                                <div className="min-w-[100px]">
                                  <div className="ledger-label">Rank</div>
                                  <div className="tnum mt-1.5 text-[14.5px]">
                                    #{i + 1} of {categoryRows.length}
                                  </div>
                                </div>
                                <p className="text-muted-foreground w-full text-[13px] text-pretty">
                                  {row.name} accounts for {row.pct}% of the{" "}
                                  {formatRon(result.totalSpent)} covered by this report.
                                </p>
                              </div>
                            )}
                          </div>
                        )
                      })}
                      <div className="border-border mt-1 flex justify-between border-t pt-4 text-[14px] font-semibold">
                        <span>Total · {result.expenseCount} expenses</span>
                        <span className="figure">{formatRon(result.totalSpent)}</span>
                      </div>
                    </div>
                  </section>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}

/** Month-against-month bar chart — hover a bar to pick out its value and month. */
function MonthBars({
  points,
  hover,
  onHover,
}: {
  points: { month: string; amount: number }[]
  hover: number | null
  onHover: (i: number | null) => void
}) {
  const max = Math.max(...points.map((p) => p.amount), 1)
  return (
    <div>
      <div className="border-border flex items-end gap-4 border-b" style={{ height: 148 }}>
        {points.map((p, i) => {
          const hot = hover === i
          return (
            <div
              key={i}
              className="flex h-full flex-1 cursor-pointer flex-col items-center justify-end"
              onMouseEnter={() => onHover(i)}
              onMouseLeave={() => onHover(null)}
            >
              <span className={`tnum mb-2 text-[12px] ${hot ? "text-foreground" : "text-muted-foreground"}`}>
                {Math.round(p.amount).toLocaleString()}
              </span>
              <div
                className="w-full rounded-t-sm transition-colors"
                style={{
                  height: `${Math.max(4, Math.round((p.amount / max) * 100))}%`,
                  background: hot ? "#9AD4E3" : "#4C93A6",
                }}
              />
            </div>
          )
        })}
      </div>
      <div className="mt-2.5 flex gap-4">
        {points.map((p, i) => (
          <div
            key={i}
            className={`ledger-label flex-1 text-center ${hover === i ? "text-foreground" : ""}`}
          >
            {p.month}
          </div>
        ))}
      </div>
    </div>
  )
}
