import { useCallback, useEffect, useState, type FormEvent } from "react"
import { useAuth } from "@/auth/AuthContext"
import {
  calculateSalary,
  createIncomeSource,
  createSavings,
  deleteIncomeSource,
  deleteSavings,
  formatRon,
  getNetWorth,
  listIncomeSources,
  listSavings,
  updateSavings,
  type IncomeSource,
  type NetWorth,
  type SalaryBreakdown,
  type SavingsAccount,
} from "@/lib/api"
import { useToast } from "@/components/Toast"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const GOOD = "#0ca30c"
const DANGER = "#ff4500"

const today = () => new Date().toISOString().slice(0, 10)
const toBani = (v: FormDataEntryValue | null) => Math.round(parseFloat(String(v)) * 100)

const RECURRENCE_LABEL: Record<IncomeSource["recurrence"], string> = {
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
  ONE_OFF: "One-off",
}

function SalaryCalculatorCard() {
  const [mode, setMode] = useState<"GROSS_TO_NET" | "NET_TO_GROSS">("GROSS_TO_NET")
  const [result, setResult] = useState<SalaryBreakdown | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const data = new FormData(e.currentTarget)
    try {
      setResult(await calculateSalary(mode, toBani(data.get("salary"))))
    } catch (err) {
      setError(err instanceof Error ? err.message : "Calculation failed")
    }
  }

  const row = "flex justify-between border-b py-2 text-[13px]"

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex flex-wrap items-center justify-between gap-2 text-base">
          Salary calculator
          <span className="bg-secondary/60 flex gap-0.5 rounded-lg p-[3px]">
            {(["GROSS_TO_NET", "NET_TO_GROSS"] as const).map((m) => (
              <button
                key={m}
                onClick={() => setMode(m)}
                className={`cursor-pointer rounded-md border-none px-3 py-1 text-xs font-medium ${
                  mode === m ? "bg-primary text-primary-foreground" : "text-muted-foreground bg-transparent"
                }`}
              >
                {m === "GROSS_TO_NET" ? "Gross → Net" : "Net → Gross"}
              </button>
            ))}
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <form className="flex gap-2" onSubmit={submit}>
          <Input
            name="salary"
            type="number"
            step="0.01"
            min="1"
            required
            className="h-11 font-mono text-base"
            placeholder={mode === "GROSS_TO_NET" ? "Gross salary (RON)" : "Desired net salary (RON)"}
          />
          <Button type="submit" className="h-11">
            Calculate
          </Button>
        </form>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {result && (
          <div className="rounded-xl border bg-white/[.03] px-4 py-2">
            <div className={row}>
              <span>Gross salary</span>
              <span className="font-mono">{formatRon(result.gross)}</span>
            </div>
            <div className={row}>
              <span>CAS — pension (25%)</span>
              <span className="font-mono" style={{ color: DANGER }}>
                −{formatRon(result.cas)}
              </span>
            </div>
            <div className={row}>
              <span>CASS — health (10%)</span>
              <span className="font-mono" style={{ color: DANGER }}>
                −{formatRon(result.cass)}
              </span>
            </div>
            <div className={row}>
              <span>Income tax (10%)</span>
              <span className="font-mono" style={{ color: DANGER }}>
                −{formatRon(result.incomeTax)}
              </span>
            </div>
            <div className="flex justify-between py-2.5 text-[15px] font-semibold">
              <span>Net salary</span>
              <span className="font-mono" style={{ color: GOOD }}>
                {formatRon(result.net)}
              </span>
            </div>
          </div>
        )}
        <p className="text-muted-foreground text-[11px] leading-relaxed">
          Rates are versioned config ({result?.rulesValidFrom?.slice(0, 4) ?? "2026"}). Personal
          deduction not applied in this quick view.
        </p>
      </CardContent>
    </Card>
  )
}

export default function ProfileTab() {
  const { user, logout } = useAuth()
  const toast = useToast()
  const [income, setIncome] = useState<IncomeSource[]>([])
  const [savings, setSavings] = useState<SavingsAccount[]>([])
  const [netWorth, setNetWorth] = useState<NetWorth | null>(null)
  const [recurrence, setRecurrence] = useState<IncomeSource["recurrence"]>("MONTHLY")
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(() => {
    Promise.all([listIncomeSources(), listSavings(), getNetWorth()])
      .then(([i, s, n]) => {
        setIncome(i)
        setSavings(s)
        setNetWorth(n)
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Failed to load"))
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  async function addIncome(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget
    const data = new FormData(form)
    try {
      await createIncomeSource({
        name: String(data.get("name")),
        amount: toBani(data.get("amount")),
        recurrence,
        startDate: String(data.get("startDate")),
        endDate: null,
      })
      form.reset()
      toast("✓", "Income source added")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  async function addSavings(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget
    const data = new FormData(form)
    try {
      await createSavings(String(data.get("name")), toBani(data.get("balance")))
      form.reset()
      toast("🐷", "Savings pot added")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    }
  }

  async function adjustBalance(account: SavingsAccount) {
    const value = window.prompt(`New balance for "${account.name}" (RON):`, String(account.balance / 100))
    if (value === null) return
    const balance = Math.round(parseFloat(value) * 100)
    if (Number.isNaN(balance) || balance < 0) {
      setError("Enter a valid non-negative amount")
      return
    }
    try {
      await updateSavings(account.id, account.name, balance)
      toast("✓", "Balance updated")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed")
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-[22px] font-semibold tracking-tight">Profile &amp; Money</h1>
        <Button variant="outline" size="sm" onClick={logout}>
          Sign out →
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid items-start gap-6 lg:grid-cols-2">
        <div className="space-y-5">
          <Card>
            <CardContent className="flex items-center gap-3.5 pt-5">
              <span className="bg-secondary grid size-11 place-items-center rounded-full text-base font-semibold">
                {(user?.displayName ?? "?").charAt(0).toUpperCase()}
              </span>
              <div>
                <p className="text-[15px] font-semibold">{user?.displayName}</p>
                <p className="text-muted-foreground text-xs">{user?.email}</p>
              </div>
              <div className="ml-auto text-right">
                <p className="ledger-label text-[10px]">Net worth</p>
                <p className="mt-0.5 font-mono text-lg font-semibold">
                  {netWorth ? formatRon(netWorth.total) : "—"}
                </p>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="ledger-label font-normal">Income sources</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <form className="space-y-2" onSubmit={addIncome}>
                <div className="grid grid-cols-2 gap-2">
                  <Input name="name" required placeholder="Salary" aria-label="Income name" />
                  <Input
                    name="amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    required
                    placeholder="Amount (RON)"
                    aria-label="Amount"
                    className="font-mono"
                  />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <Select
                    value={recurrence}
                    onValueChange={(v) => setRecurrence(v as IncomeSource["recurrence"])}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MONTHLY">Monthly</SelectItem>
                      <SelectItem value="YEARLY">Yearly</SelectItem>
                      <SelectItem value="ONE_OFF">One-off</SelectItem>
                    </SelectContent>
                  </Select>
                  <Input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" className="font-mono" />
                </div>
                <Button type="submit" size="sm">
                  Add income source
                </Button>
              </form>
              <div>
                {income.map((source) => (
                  <div
                    key={source.id}
                    className="flex items-center gap-3 border-b py-2.5 last:border-b-0"
                  >
                    <span className="text-sm">💼</span>
                    <div className="flex-1">
                      <p className="text-[13px] font-medium">{source.name}</p>
                      <p className="text-muted-foreground text-[11px]">
                        {RECURRENCE_LABEL[source.recurrence]}
                      </p>
                    </div>
                    <span className="font-mono text-[13px] font-medium" style={{ color: GOOD }}>
                      {formatRon(source.amount)}
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteIncomeSource(source.id).then(reload)}
                    >
                      ✕
                    </Button>
                  </div>
                ))}
                {income.length === 0 && (
                  <p className="text-muted-foreground py-2 text-sm">No income sources yet.</p>
                )}
              </div>
              {netWorth && (
                <div className="flex justify-between border-t pt-3 text-[13.5px] font-semibold">
                  <span>Total monthly income</span>
                  <span className="font-mono">{formatRon(netWorth.monthlyIncome)}</span>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="ledger-label font-normal">Savings pots</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <form className="flex gap-2" onSubmit={addSavings}>
                <Input name="name" required placeholder="Emergency fund" aria-label="Savings name" />
                <Input
                  name="balance"
                  type="number"
                  step="0.01"
                  min="0"
                  required
                  placeholder="Balance (RON)"
                  aria-label="Balance"
                  className="max-w-36 font-mono"
                />
                <Button type="submit" size="sm">
                  Add
                </Button>
              </form>
              <ul className="space-y-1.5">
                {savings.map((account) => (
                  <li key={account.id} className="flex items-center justify-between gap-2 text-sm">
                    <span className="flex items-center gap-2">
                      <span className="text-sm">🐷</span>
                      {account.name}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="font-mono font-medium">{formatRon(account.balance)}</span>
                      <Button variant="ghost" size="sm" onClick={() => adjustBalance(account)}>
                        Adjust
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => deleteSavings(account.id).then(reload)}
                      >
                        ✕
                      </Button>
                    </span>
                  </li>
                ))}
                {savings.length === 0 && (
                  <p className="text-muted-foreground py-2 text-sm">No savings pots yet.</p>
                )}
              </ul>
            </CardContent>
          </Card>
        </div>

        <SalaryCalculatorCard />
      </div>
    </div>
  )
}
