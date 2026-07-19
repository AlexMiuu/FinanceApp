import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react"
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, Cell } from "recharts"
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
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

const PRIMARY = "#f97c0e"
const SERIES = ["#d95926", "#199e70", "#c98500", "#9085e9", "#008300", "#e66767"]
const MUTED = "#a89e90"
const GRID = "rgba(255,255,255,.06)"

export default function ReportsTab({ categories }: { categories: Category[] }) {
  const toast = useToast()
  const [reports, setReports] = useState<Report[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [results, setResults] = useState<Record<string, ReportResult>>({})
  const [creating, setCreating] = useState(false)
  const [filterCategory, setFilterCategory] = useState<string>()
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

  const monthBars = useMemo(() => {
    if (!result) return []
    return Object.entries(result.byMonth)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .slice(-6)
      .map(([month, amount]) => ({ month: month.slice(2), amount: amount / 100 }))
  }, [result])

  const categoryRows = useMemo(() => {
    if (!result) return []
    const entries = Object.entries(result.byCategory)
    return entries.map(([name, amount], i) => ({
      name,
      amount,
      pct: result.totalSpent > 0 ? Math.round((amount / result.totalSpent) * 100) : 0,
      color: SERIES[i % SERIES.length],
    }))
  }, [result])

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
      toast("✓", "Report saved")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed")
    }
  }

  async function run(id: string) {
    try {
      const r = await runReport(id)
      setResults((prev) => ({ ...prev, [id]: r }))
      toast("◔", "Report re-evaluated")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Run failed")
    }
  }

  return (
    <div className="space-y-5">
      <h1 className="text-[22px] font-semibold tracking-tight">Reports</h1>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-[340px_1fr]">
        <Card className="py-3">
          <CardContent className="px-3">
            <div className="flex items-center justify-between px-2 pb-2">
              <span className="ledger-label">Saved reports</span>
              <button
                className="text-primary cursor-pointer border-none bg-transparent p-0 text-[11.5px]"
                onClick={() => setCreating((v) => !v)}
              >
                {creating ? "Close" : "+ New"}
              </button>
            </div>

            {creating && (
              <form className="mb-2 space-y-2 rounded-lg border p-3" onSubmit={submit}>
                <div className="space-y-1">
                  <Label htmlFor="rname">Name</Label>
                  <Input id="rname" name="name" required maxLength={100} placeholder="July food spending" />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Input name="from" type="date" aria-label="From" className="font-mono" />
                  <Input name="to" type="date" aria-label="To" className="font-mono" />
                </div>
                <CategorySelect
                  categories={categories}
                  value={filterCategory}
                  onChange={setFilterCategory}
                  allowAll
                />
                <Button type="submit" size="sm" className="w-full">
                  Save report
                </Button>
              </form>
            )}

            {reports.length === 0 && !creating && (
              <p className="text-muted-foreground px-2 py-4 text-sm">
                No saved reports yet — create one with “+ New”.
              </p>
            )}
            {reports.map((r) => (
              <div
                key={r.id}
                onClick={() => setSelected(r.id)}
                className={`mb-1 cursor-pointer rounded-lg p-3 ${
                  selected === r.id
                    ? "bg-secondary/60 border-l-2 border-primary"
                    : "hover:bg-secondary/30 border-l-2 border-transparent"
                }`}
              >
                <p className="text-[13px] font-medium">{r.name}</p>
                <p className="text-muted-foreground mt-0.5 text-[11.5px]">{describeFilters(r)}</p>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="pt-5">
            {!report ? (
              <p className="text-muted-foreground py-10 text-center text-sm">
                Select or create a report on the left.
              </p>
            ) : (
              <>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-base font-semibold">{report.name}</p>
                    <p className="text-muted-foreground mt-0.5 text-xs">
                      {describeFilters(report)}
                      {report.lastRunAt &&
                        ` · last run ${new Date(report.lastRunAt).toLocaleDateString("en-GB")}`}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <Button variant="outline" size="sm" onClick={() => run(report.id)}>
                      Run
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        downloadReportCsv(report.id, report.name)
                          .then(() => toast("📄", "CSV exported — check your downloads"))
                          .catch((e) => setError(e instanceof Error ? e.message : "Export failed"))
                      }
                    >
                      Export CSV
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        deleteReport(report.id).then(() => {
                          setSelected(null)
                          reload()
                        })
                      }}
                    >
                      Delete
                    </Button>
                  </div>
                </div>

                {!result ? (
                  <p className="text-muted-foreground py-10 text-center text-sm">
                    Hit <span className="text-foreground font-medium">Run</span> to evaluate this
                    report.
                  </p>
                ) : (
                  <>
                    {monthBars.length > 0 && (
                      <ResponsiveContainer width="100%" height={190} className="mt-5">
                        <BarChart data={monthBars} margin={{ top: 16, right: 4, left: 4, bottom: 0 }}>
                          <CartesianGrid stroke={GRID} vertical={false} />
                          <XAxis
                            dataKey="month"
                            tickLine={false}
                            axisLine={false}
                            tick={{ fill: MUTED, fontSize: 10, fontFamily: "Geist Mono Variable" }}
                          />
                          <YAxis
                            tickLine={false}
                            axisLine={false}
                            tick={{ fill: MUTED, fontSize: 10, fontFamily: "Geist Mono Variable" }}
                            width={52}
                          />
                          <Tooltip
                            contentStyle={{ background: "#332a1d", border: "1px solid rgba(255,255,255,.12)", borderRadius: 10 }}
                            formatter={(value) => [formatRon(Math.round(Number(value) * 100)), "spent"]}
                          />
                          <Bar dataKey="amount" radius={[6, 6, 0, 0]} maxBarSize={56}>
                            {monthBars.map((bar, i) => (
                              <Cell
                                key={bar.month}
                                fill={PRIMARY}
                                fillOpacity={i === monthBars.length - 1 ? 1 : 0.55}
                              />
                            ))}
                          </Bar>
                        </BarChart>
                      </ResponsiveContainer>
                    )}

                    <div className="mt-5 border-t">
                      {categoryRows.map((row) => (
                        <div key={row.name} className="flex items-center gap-3 border-b py-3">
                          <span
                            className="size-2 flex-none rounded-full"
                            style={{ backgroundColor: row.color }}
                          />
                          <span className="w-32 truncate text-[13px]">{row.name}</span>
                          <div className="bg-secondary/40 h-[5px] flex-1 rounded-full">
                            <div
                              className="h-[5px] rounded-full"
                              style={{ width: `${row.pct}%`, backgroundColor: row.color }}
                            />
                          </div>
                          <span className="text-muted-foreground w-10 text-right font-mono text-xs">
                            {row.pct}%
                          </span>
                          <span className="w-28 text-right font-mono text-[13px] font-medium">
                            {formatRon(row.amount)}
                          </span>
                        </div>
                      ))}
                      <div className="flex justify-between pt-3.5 text-[13.5px] font-semibold">
                        <span>Total · {result.expenseCount} expenses</span>
                        <span className="font-mono">{formatRon(result.totalSpent)}</span>
                      </div>
                    </div>
                  </>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
