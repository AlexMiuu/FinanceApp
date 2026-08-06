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

const CATEGORY_BAR = ["#c79a5b", "#e8d3b4", "#b07e52", "#8a6440", "#9cb37a", "#c98a3c"]

export default function ReportsTab({ categories }: { categories: Category[] }) {
  const toast = useToast()
  const [reports, setReports] = useState<Report[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [results, setResults] = useState<Record<string, ReportResult>>({})
  const [creating, setCreating] = useState(false)
  const [filterCategory, setFilterCategory] = useState<string>()
  const [hoverPoint, setHoverPoint] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(() => {
    listReports()
      .then((list) => {
        setReports(list)
        setResults(
          Object.fromEntries(
            list.filter((r) => r.cachedResult).map((r) => [r.id, r.cachedResult as ReportResult])
          )
        )
        setSelected((prev) => prev ?? list[0]?.id ?? null)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load reports"))
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
            <form className="mb-3 space-y-2.5 rounded-xl border border-white/10 bg-[#241C17] p-4" onSubmit={submit}>
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
                className="bg-primary text-primary-foreground w-full cursor-pointer rounded-xl border-none py-2.5 text-[13.5px] font-semibold hover:bg-[#D8B27A]"
              >
                Save report
              </button>
            </form>
          )}

          {reports.length === 0 && !creating && (
            <p className="text-muted-foreground py-4 text-sm">No saved reports yet — create one with “+ New”.</p>
          )}
          <div className="flex flex-col gap-1">
            {reports.map((r) => {
              const sel = selected === r.id
              return (
                <button
                  key={r.id}
                  onClick={() => setSelected(r.id)}
                  className={`cursor-pointer rounded-xl p-3.5 text-left transition-colors ${
                    sel ? "bg-white/[0.06]" : "hover:bg-white/[0.03]"
                  }`}
                  style={sel ? { boxShadow: "inset 3px 0 0 0 #C79A5B" } : undefined}
                >
                  <p className="text-[13.5px] font-medium">{r.name}</p>
                  <p className="text-muted-foreground mt-0.5 text-[12.5px]">{describeFilters(r)}</p>
                </button>
              )
            })}
          </div>
        </section>

        {/* Selected report analytics */}
        <div className="flex flex-col gap-[22px]">
          {!report ? (
            <section className={card}>
              <p className="text-muted-foreground py-10 text-center text-sm">
                Select or create a report on the left.
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
                      className="text-foreground/85 cursor-pointer rounded-xl border border-white/15 bg-white/[0.05] px-3.5 py-2 text-[13px] hover:border-white/30"
                    >
                      Run
                    </button>
                    <button
                      onClick={() =>
                        downloadReportCsv(report.id, report.name)
                          .then(() => toast("CSV exported — check your downloads"))
                          .catch((e) => setError(e instanceof Error ? e.message : "Export failed"))
                      }
                      className="text-foreground/85 cursor-pointer rounded-xl border border-white/15 bg-white/[0.05] px-3.5 py-2 text-[13px] hover:border-white/30"
                    >
                      Export CSV
                    </button>
                    <button
                      onClick={() => deleteReport(report.id).then(() => { setSelected(null); reload() })}
                      className="text-destructive cursor-pointer rounded-xl border border-[#c96a4e]/40 bg-[#c96a4e]/10 px-3.5 py-2 text-[13px] hover:bg-[#c96a4e]/20"
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
                      { label: "Total spent", value: formatRon(result.totalSpent).replace(/\s?RON$/, ""), sub: "RON", tone: "#C3B4A2" },
                      { label: "Expenses counted", value: String(result.expenseCount), sub: "transactions", tone: "#B2C58F" },
                      { label: "Biggest category", value: biggest?.name ?? "—", sub: biggest ? `${biggest.pct}% of spend` : "", tone: "#C98A3C" },
                    ].map((k) => (
                      <section key={k.label} className="ledger-card min-w-[230px] flex-1 p-6">
                        <div className="text-muted-foreground text-[14.5px]">{k.label}</div>
                        <div className="font-heading tnum mt-2.5 text-[32px] font-semibold tracking-tight">{k.value}</div>
                        <div className="mt-2.5 font-mono text-[13px]" style={{ color: k.tone }}>{k.sub}</div>
                      </section>
                    ))}
                  </div>

                  {/* Spending trend */}
                  {monthPoints.length > 1 && (
                    <section className={card}>
                      <h2 className="mb-1 text-[18px] font-semibold">Spending trend</h2>
                      <p className="text-muted-foreground mb-3 text-[13px]">Last {monthPoints.length} months</p>
                      <TrendChart points={monthPoints} hover={hoverPoint} onHover={setHoverPoint} />
                    </section>
                  )}

                  {/* By category */}
                  <section className={card}>
                    <h2 className="mb-5.5 text-[18px] font-semibold">By category</h2>
                    <div className="flex flex-col gap-4.5">
                      {categoryRows.map((row) => (
                        <div key={row.name}>
                          <div className="flex justify-between text-[14.5px]">
                            <span className="font-medium">{row.name}</span>
                            <span className="tnum font-mono">{formatRon(row.amount).replace(/\s?RON$/, "")}</span>
                          </div>
                          <div className="mt-2.5 h-2 overflow-hidden rounded-full bg-white/[0.09]">
                            <div className="h-full rounded-full" style={{ width: `${row.pct}%`, background: row.color }} />
                          </div>
                        </div>
                      ))}
                      <div className="mt-1 flex justify-between border-t border-white/[0.07] pt-4 text-[14px] font-semibold">
                        <span>Total · {result.expenseCount} expenses</span>
                        <span className="tnum font-mono">{formatRon(result.totalSpent)}</span>
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

/** Espresso line chart — area fill, tan line, hoverable points with tooltip. */
function TrendChart({
  points,
  hover,
  onHover,
}: {
  points: { month: string; amount: number }[]
  hover: number | null
  onHover: (i: number | null) => void
}) {
  const W = 1000
  const H = 240
  const pad = 46
  const values = points.map((p) => p.amount)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const lo = Math.max(0, min - (max - min) * 0.2)
  const hi = max + (max - min) * 0.2 || max + 1
  const n = points.length
  const xy = points.map((p, i) => [
    pad + (i * (W - pad * 2)) / (n - 1),
    H - 24 - ((p.amount - lo) / (hi - lo || 1)) * (H - 60),
  ])
  const gridVals = [0, 0.25, 0.5, 0.75, 1].map((t) => lo + t * (hi - lo))

  return (
    <div className="relative h-[280px]">
      <svg viewBox={`0 0 ${W} ${H + 30}`} preserveAspectRatio="none" className="h-full w-full overflow-visible">
        {gridVals.map((g, i) => {
          const y = H - 24 - ((g - lo) / (hi - lo || 1)) * (H - 60)
          return (
            <g key={i}>
              <line x1={pad} x2={W - pad} y1={y} y2={y} stroke="rgba(255,255,255,0.07)" strokeWidth={1} />
              <text x={8} y={y + 4} fill="#C0B1A0" fontSize={13} fontFamily="var(--font-mono)">
                {(g / 1000).toFixed(1)}k
              </text>
            </g>
          )
        })}
        <polygon
          points={`${pad},${H - 24} ${xy.map((p) => p.join(",")).join(" ")} ${W - pad},${H - 24}`}
          fill="rgba(217,169,122,0.16)"
        />
        <polyline
          points={xy.map((p) => p.join(",")).join(" ")}
          fill="none"
          stroke="#C79A5B"
          strokeWidth={2.6}
          strokeLinejoin="round"
          strokeLinecap="round"
        />
        {xy.map((p, i) => (
          <g key={i}>
            <circle
              cx={p[0]}
              cy={p[1]}
              r={hover === i ? 8 : 5.5}
              fill={hover === i ? "#fff" : "#C79A5B"}
              stroke="#C79A5B"
              strokeWidth={2.4}
              style={{ cursor: "pointer" }}
              onMouseEnter={() => onHover(i)}
              onMouseLeave={() => onHover(null)}
            />
            <text x={p[0]} y={H + 14} fill="#C3B4A2" fontSize={14} textAnchor="middle" fontFamily="var(--font-mono)">
              {points[i].month}
            </text>
          </g>
        ))}
        {hover !== null && (
          <g pointerEvents="none">
            <rect x={xy[hover][0] - 66} y={xy[hover][1] - 62} width={132} height={44} rx={9} fill="#2A211B" stroke="rgba(255,255,255,0.14)" />
            <text x={xy[hover][0]} y={xy[hover][1] - 42} fill="#fff" fontSize={17} fontWeight={700} textAnchor="middle" fontFamily="var(--font-mono)">
              {Math.round(points[hover].amount).toLocaleString()} RON
            </text>
            <text x={xy[hover][0]} y={xy[hover][1] - 26} fill="#C3B4A2" fontSize={13} textAnchor="middle" fontFamily="var(--font-sans)">
              {points[hover].month}
            </text>
          </g>
        )}
      </svg>
    </div>
  )
}
