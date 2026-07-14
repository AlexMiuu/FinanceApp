import { useCallback, useEffect, useState, type FormEvent } from "react"
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
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"

const today = () => new Date().toISOString().slice(0, 10)
const toBani = (v: FormDataEntryValue | null) => Math.round(parseFloat(String(v)) * 100)

const RECURRENCE_LABEL: Record<IncomeSource["recurrence"], string> = {
  MONTHLY: "monthly",
  YEARLY: "yearly",
  ONE_OFF: "one-off",
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

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Salary calculator</CardTitle>
        <CardDescription>
          Romanian rules (CAS 25% · CASS 10% · income tax 10%), versioned in configuration.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <Tabs value={mode} onValueChange={(v) => setMode(v as typeof mode)}>
          <TabsList className="w-full">
            <TabsTrigger value="GROSS_TO_NET" className="flex-1">
              Gross → Net
            </TabsTrigger>
            <TabsTrigger value="NET_TO_GROSS" className="flex-1">
              Net → Gross
            </TabsTrigger>
          </TabsList>
        </Tabs>
        <form className="flex gap-2" onSubmit={submit}>
          <Input
            name="salary"
            type="number"
            step="0.01"
            min="1"
            required
            placeholder={mode === "GROSS_TO_NET" ? "Gross salary (RON)" : "Net salary (RON)"}
          />
          <Button type="submit">Calculate</Button>
        </form>
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {result && (
          <div className="bg-muted/50 space-y-1 rounded-md p-3 text-sm">
            <div className="flex justify-between font-medium">
              <span>Gross</span>
              <span>{formatRon(result.gross)}</span>
            </div>
            <div className="text-muted-foreground flex justify-between">
              <span>CAS (pension)</span>
              <span>−{formatRon(result.cas)}</span>
            </div>
            <div className="text-muted-foreground flex justify-between">
              <span>CASS (health)</span>
              <span>−{formatRon(result.cass)}</span>
            </div>
            <div className="text-muted-foreground flex justify-between">
              <span>Income tax</span>
              <span>−{formatRon(result.incomeTax)}</span>
            </div>
            <div className="border-border flex justify-between border-t pt-1 font-semibold">
              <span>Net</span>
              <span>{formatRon(result.net)}</span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default function ProfileTab() {
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
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed")
    }
  }

  return (
    <div className="space-y-6">
      {netWorth && (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-muted-foreground text-xs font-medium uppercase tracking-wide">
              Net worth
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-semibold">{formatRon(netWorth.total)}</p>
            <p className="text-muted-foreground pt-1 text-xs">
              across {netWorth.accounts} savings account{netWorth.accounts === 1 ? "" : "s"} ·
              recurring income {formatRon(netWorth.monthlyIncome)}/month
            </p>
          </CardContent>
        </Card>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Income sources</CardTitle>
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
                <Input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" />
              </div>
              <Button type="submit" size="sm">
                Add income source
              </Button>
            </form>
            <ul className="space-y-1.5">
              {income.map((source) => (
                <li key={source.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2">
                    {source.name}
                    <Badge variant="secondary">{RECURRENCE_LABEL[source.recurrence]}</Badge>
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="font-medium">{formatRon(source.amount)}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteIncomeSource(source.id).then(reload)}
                    >
                      Delete
                    </Button>
                  </span>
                </li>
              ))}
              {income.length === 0 && (
                <p className="text-muted-foreground text-sm">No income sources yet.</p>
              )}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Savings</CardTitle>
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
                className="max-w-36"
              />
              <Button type="submit" size="sm">
                Add
              </Button>
            </form>
            <ul className="space-y-1.5">
              {savings.map((account) => (
                <li key={account.id} className="flex items-center justify-between gap-2 text-sm">
                  <span>{account.name}</span>
                  <span className="flex items-center gap-1">
                    <span className="font-medium">{formatRon(account.balance)}</span>
                    <Button variant="ghost" size="sm" onClick={() => adjustBalance(account)}>
                      Adjust
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => deleteSavings(account.id).then(reload)}
                    >
                      Delete
                    </Button>
                  </span>
                </li>
              ))}
              {savings.length === 0 && (
                <p className="text-muted-foreground text-sm">No savings accounts yet.</p>
              )}
            </ul>
          </CardContent>
        </Card>
      </div>

      <SalaryCalculatorCard />
    </div>
  )
}
