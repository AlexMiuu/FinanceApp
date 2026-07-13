import { useCallback, useEffect, useState, type FormEvent } from "react"
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
import { CategorySelect } from "@/components/CategorySelect"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

function ResultSummary({ result }: { result: ReportResult }) {
  return (
    <div className="bg-muted/50 space-y-2 rounded-md p-3 text-sm">
      <p>
        <span className="font-medium">{formatRon(result.totalSpent)}</span>{" "}
        <span className="text-muted-foreground">across {result.expenseCount} expenses</span>
      </p>
      <ul className="space-y-0.5">
        {Object.entries(result.byCategory).map(([category, amount]) => (
          <li key={category} className="flex justify-between">
            <span>{category}</span>
            <span className="text-muted-foreground">{formatRon(amount)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default function ReportsTab({ categories }: { categories: Category[] }) {
  const [reports, setReports] = useState<Report[]>([])
  const [results, setResults] = useState<Record<string, ReportResult>>({})
  const [error, setError] = useState<string | null>(null)
  const [filterCategory, setFilterCategory] = useState<string>()

  const reload = useCallback(() => {
    listReports()
      .then((list) => {
        setReports(list)
        setResults(Object.fromEntries(
          list.filter((r) => r.cachedResult).map((r) => [r.id, r.cachedResult as ReportResult])
        ))
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load reports"))
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget
    const data = new FormData(form)
    try {
      await createReport(String(data.get("name")), {
        from: String(data.get("from")) || null,
        to: String(data.get("to")) || null,
        categoryIds: filterCategory ? [filterCategory] : null,
      })
      form.reset()
      setFilterCategory(undefined)
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed")
    }
  }

  async function run(report: Report) {
    setError(null)
    try {
      const result = await runReport(report.id)
      setResults((prev) => ({ ...prev, [report.id]: result }))
    } catch (err) {
      setError(err instanceof Error ? err.message : "Run failed")
    }
  }

  async function remove(id: string) {
    setError(null)
    try {
      await deleteReport(id)
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  function describeFilters(report: Report): string {
    const parts: string[] = []
    if (report.filters.from) parts.push(`from ${report.filters.from}`)
    if (report.filters.to) parts.push(`to ${report.filters.to}`)
    if (report.filters.categoryIds?.length) {
      const names = report.filters.categoryIds
        .map((id) => categories.find((c) => c.id === id)?.name ?? "?")
        .join(", ")
      parts.push(names)
    }
    return parts.length ? parts.join(" · ") : "all expenses"
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">New report</CardTitle>
          <CardDescription>
            A report is a saved set of filters, re-evaluated every time you run it.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-3" onSubmit={submit}>
            <div className="space-y-1.5">
              <Label htmlFor="report-name">Name</Label>
              <Input id="report-name" name="name" required maxLength={100} placeholder="July food spending" />
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <div className="space-y-1.5">
                <Label htmlFor="report-from">From</Label>
                <Input id="report-from" name="from" type="date" />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="report-to">To</Label>
                <Input id="report-to" name="to" type="date" />
              </div>
              <div className="space-y-1.5">
                <Label>Category</Label>
                <CategorySelect
                  categories={categories}
                  value={filterCategory}
                  onChange={setFilterCategory}
                  allowAll
                />
              </div>
            </div>
            <Button type="submit">Save report</Button>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {reports.length === 0 ? (
        <p className="text-muted-foreground py-4 text-center text-sm">No saved reports yet.</p>
      ) : (
        reports.map((report) => (
          <Card key={report.id}>
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-base">
                {report.name}
                <span className="space-x-1">
                  <Button variant="outline" size="sm" onClick={() => run(report)}>
                    Run
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => downloadReportCsv(report.id, report.name).catch(
                      (e) => setError(e instanceof Error ? e.message : "Export failed"))}
                  >
                    Export CSV
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => remove(report.id)}>
                    Delete
                  </Button>
                </span>
              </CardTitle>
              <CardDescription>{describeFilters(report)}</CardDescription>
            </CardHeader>
            {results[report.id] && (
              <CardContent>
                <ResultSummary result={results[report.id]} />
              </CardContent>
            )}
          </Card>
        ))
      )}
    </div>
  )
}
