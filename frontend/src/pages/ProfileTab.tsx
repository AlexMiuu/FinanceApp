import { useCallback, useEffect, useState, type FormEvent } from "react"
import { useAuth } from "@/auth/AuthContext"
import {
  calculateSalary,
  createIncomeSource,
  createSavings,
  deleteAccount,
  deleteIncomeSource,
  deleteSavings,
  exportMyData,
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
import { clearArgaliStorage } from "@/lib/storage"
import { useToast } from "@/components/Toast"
import { CloseIcon, HornGlyph } from "@/components/brand"
import { Alert, AlertDescription } from "@/components/ui/alert"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"

const GOOD = "#8FC7A6"
const OUT = "#E09880"

const today = () => new Date().toISOString().slice(0, 10)
const toBani = (v: FormDataEntryValue | null) => Math.round(parseFloat(String(v)) * 100)

const RECURRENCE_LABEL: Record<IncomeSource["recurrence"], string> = {
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
  ONE_OFF: "One-off",
}

const CARD = "ledger-card p-6"
const FIELD =
  "text-foreground w-full border border-border bg-transparent px-3.5 py-2.5 text-sm outline-none focus-visible:border-[#4C93A6]"
const CTA =
  "cursor-pointer border border-[#4C93A6] bg-[#123945] text-[#C4E7F0] hover:bg-[#174756]"
const OUTLINED =
  "cursor-pointer border border-border bg-transparent text-foreground/85 hover:border-[#4C93A6] hover:text-foreground"
const OUTLINED_DESTRUCTIVE =
  "cursor-pointer border border-destructive/40 bg-transparent text-destructive hover:border-destructive hover:bg-destructive/10"

function monogram(label: string): string {
  const w = label.trim().split(/\s+/).filter(Boolean)
  if (!w.length) return "··"
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase()
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

  const row = "flex items-baseline justify-between border-b border-[#2A3033] py-3.5 text-[15px]"

  return (
    <section className={CARD}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-[20px] font-semibold">Salary calculator</h2>
        <span className="border-border flex gap-0.5 border p-[3px]">
          {(["GROSS_TO_NET", "NET_TO_GROSS"] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`cursor-pointer border-none px-3 py-1 font-mono text-[12.5px] font-medium ${
                mode === m ? "bg-primary text-primary-foreground" : "text-muted-foreground bg-transparent"
              }`}
            >
              {m === "GROSS_TO_NET" ? "gross → net" : "net → gross"}
            </button>
          ))}
        </span>
      </div>

      <form className="mt-5.5" onSubmit={submit}>
        <label className="text-muted-foreground mb-2.5 block text-[13.5px]">
          {mode === "GROSS_TO_NET" ? "Gross salary (RON)" : "Desired net salary (RON)"}
        </label>
        <div className="flex gap-2.5">
          <input
            name="salary"
            type="number"
            step="0.01"
            min="1"
            required
            inputMode="decimal"
            placeholder="0.00"
            className={`${FIELD} figure !py-4 text-[22px]`}
          />
          <button type="submit" className={`${CTA} px-5 text-[14px] font-semibold`}>
            Go
          </button>
        </div>
      </form>

      {error && (
        <Alert variant="destructive" className="mt-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {result && (
        <div className="border-border bg-secondary mt-5 border p-5.5">
          <div className={row}>
            <span>Gross salary</span>
            <span className="figure">{formatRon(result.gross)}</span>
          </div>
          {[
            ["CAS — pension (25%)", result.cas],
            ["CASS — health (10%)", result.cass],
            ["Income tax (10%)", result.incomeTax],
          ].map(([label, value]) => (
            <div key={label as string} className={row}>
              <span>{label}</span>
              <span className="figure" style={{ color: OUT }}>
                −{formatRon(value as number)}
              </span>
            </div>
          ))}
          <div className="flex items-baseline justify-between pt-4 text-[17px] font-semibold">
            <span>Net salary</span>
            <span className="figure text-[22px]" style={{ color: GOOD }}>
              {formatRon(result.net)}
            </span>
          </div>
        </div>
      )}
      <p className="text-muted-foreground mt-4.5 text-[13px]">
        Rates are versioned config ({result?.rulesValidFrom?.slice(0, 4) ?? "2026"}). Personal
        deduction not applied in this quick view.
      </p>
    </section>
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
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteConfirmText, setDeleteConfirmText] = useState("")
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

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
      toast("Income source added")
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
      toast("Savings pot added")
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
      toast("Balance updated")
      reload()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed")
    }
  }

  async function handleExport() {
    setError(null)
    try {
      const { user: userData, expenses } = await exportMyData()
      const payload = { exportedAt: new Date().toISOString(), user: userData, expenses }
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url
      a.download = `argali-data-export-${today()}.json`
      a.click()
      URL.revokeObjectURL(url)
      toast("Data export downloaded")
    } catch (err) {
      setError(err instanceof Error ? err.message : "Export failed")
    }
  }

  function onDeleteDialogChange(open: boolean) {
    setDeleteOpen(open)
    setDeleteConfirmText("")
    setDeleteError(null)
  }

  async function handleDeleteAccount() {
    setDeleteError(null)
    setDeleting(true)
    try {
      await deleteAccount()
      // logout() clears Argali's browser storage too; this call covers the window
      // between the account going away and the sign-out request coming back.
      clearArgaliStorage()
      await logout()
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : "Deletion failed")
    } finally {
      setDeleting(false)
    }
  }

  const initial = (user?.displayName ?? "?").charAt(0).toUpperCase()

  return (
    <div className="flex flex-col gap-[22px]">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-wrap items-start gap-[22px]">
        {/* Left */}
        <div className="flex min-w-[min(100%,360px)] flex-1 basis-[46%] flex-col gap-[22px]">
          {/* Profile */}
          <section className="border-border relative overflow-hidden border p-6" style={{ background: "var(--card)" }}>
            <HornGlyph className="text-primary pointer-events-none absolute -right-6 -top-6 size-40 opacity-[0.08]" aria-hidden="true" />
            <div className="relative flex items-center gap-5">
              <div
                className="grid size-[54px] flex-none place-items-center border text-[20px] font-semibold"
                style={{ borderColor: "#9AD4E3", color: "#9AD4E3" }}
              >
                {initial}
              </div>
              <div>
                <div className="ledger-label">Account</div>
                <div className="font-heading mt-1 text-[24px] font-semibold tracking-tight">{user?.displayName}</div>
                <div className="text-muted-foreground mt-1 text-[14px]">{user?.email}</div>
              </div>
            </div>
          </section>

          {/* Net worth strip */}
          <section className={`${CARD} flex items-center justify-between`}>
            <div>
              <div className="ledger-label">Net worth</div>
              <div className="figure mt-1.5 text-[22px]">
                {netWorth ? formatRon(netWorth.total) : "—"}
              </div>
            </div>
            <div className="text-right">
              <div className="ledger-label">Income / month</div>
              <div className="figure mt-1.5 text-[18px]" style={{ color: GOOD }}>
                {netWorth ? formatRon(netWorth.monthlyIncome) : "—"}
              </div>
            </div>
          </section>

          {/* Income sources */}
          <section className={CARD}>
            <div className="ledger-label mb-4">Income sources</div>
            <form className="space-y-2.5" onSubmit={addIncome}>
              <div className="grid grid-cols-2 gap-2.5">
                <input name="name" required placeholder="Salary" aria-label="Income name" className={FIELD} />
                <input name="amount" type="number" step="0.01" min="0.01" required placeholder="Amount (RON)" aria-label="Amount" className={`${FIELD} font-mono`} />
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <Select value={recurrence} onValueChange={(v) => setRecurrence(v as IncomeSource["recurrence"])}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="MONTHLY">Monthly</SelectItem>
                    <SelectItem value="YEARLY">Yearly</SelectItem>
                    <SelectItem value="ONE_OFF">One-off</SelectItem>
                  </SelectContent>
                </Select>
                <input name="startDate" type="date" required defaultValue={today()} aria-label="Start date" className={`${FIELD} font-mono`} />
              </div>
              <button type="submit" className={`${CTA} px-4 py-2.5 text-[13.5px] font-semibold`}>
                Add income source
              </button>
            </form>
            <div className="mt-2">
              {income.map((source) => (
                <div key={source.id} className="flex items-center gap-3.5 border-b border-[#2A3033] py-3 last:border-b-0">
                  <div className="border-border text-muted-foreground grid size-10 flex-none place-items-center border font-mono text-[13px]">
                    {monogram(source.name)}
                  </div>
                  <div className="flex-1">
                    <p className="text-[14px] font-medium">{source.name}</p>
                    <p className="text-muted-foreground text-[12px]">{RECURRENCE_LABEL[source.recurrence]}</p>
                  </div>
                  <span className="figure text-[14px] font-medium" style={{ color: GOOD }}>
                    {formatRon(source.amount)}
                  </span>
                  <button
                    onClick={() => deleteIncomeSource(source.id).then(reload)}
                    aria-label="Remove income source"
                    className="text-muted-foreground hover:text-foreground grid size-9 flex-none cursor-pointer place-items-center hover:bg-white/[0.06]"
                  >
                    <CloseIcon size={15} />
                  </button>
                </div>
              ))}
              {income.length === 0 && <p className="text-muted-foreground py-2 text-sm">No income sources yet.</p>}
            </div>
            {netWorth && (
              <div className="border-border mt-2 flex justify-between border-t pt-4 text-[15px] font-semibold">
                <span>Total monthly income</span>
                <span className="figure">{formatRon(netWorth.monthlyIncome)}</span>
              </div>
            )}
          </section>

          {/* Savings pots */}
          <section className={CARD}>
            <div className="ledger-label mb-4">Savings pots</div>
            <form className="flex gap-2.5" onSubmit={addSavings}>
              <input name="name" required placeholder="Emergency fund" aria-label="Savings name" className={FIELD} />
              <input name="balance" type="number" step="0.01" min="0" required placeholder="Balance" aria-label="Balance" className={`${FIELD} max-w-36 font-mono`} />
              <button type="submit" className={`${CTA} px-4 text-[13.5px] font-semibold`}>
                Add
              </button>
            </form>
            <ul className="mt-3 space-y-1.5">
              {savings.map((account) => (
                <li key={account.id} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="flex items-center gap-3">
                    <span className="border-border text-muted-foreground grid size-9 flex-none place-items-center border font-mono text-[12px]">
                      {monogram(account.name)}
                    </span>
                    {account.name}
                  </span>
                  <span className="flex items-center gap-1.5">
                    <span className="figure font-medium">{formatRon(account.balance)}</span>
                    <button onClick={() => adjustBalance(account)} className="text-muted-foreground hover:text-foreground cursor-pointer px-1.5 text-[13px]">
                      Adjust
                    </button>
                    <button onClick={() => deleteSavings(account.id).then(reload)} aria-label="Delete pot" className="text-muted-foreground hover:text-foreground grid size-8 flex-none cursor-pointer place-items-center hover:bg-white/[0.06]">
                      <CloseIcon size={15} />
                    </button>
                  </span>
                </li>
              ))}
              {savings.length === 0 && <p className="text-muted-foreground py-2 text-sm">No savings pots yet.</p>}
            </ul>
          </section>

          {/* Export my data */}
          <section className={`${CARD} flex flex-wrap items-center justify-between gap-4`}>
            <div>
              <div className="text-[16px] font-semibold">Export my data</div>
              <div className="text-muted-foreground mt-1 text-[13.5px]">
                Download a copy of your profile, income, savings and expenses
              </div>
            </div>
            <button onClick={handleExport} className={`${OUTLINED} px-6 py-2.5 text-[14px] font-medium`}>
              Download export
            </button>
          </section>

          {/* Sign out */}
          <section className={`${CARD} flex flex-wrap items-center justify-between gap-4`}>
            <div>
              <div className="text-[16px] font-semibold">Sign out</div>
              <div className="text-muted-foreground mt-1 text-[13.5px]">End your session on this device</div>
            </div>
            <button onClick={logout} className={`${OUTLINED_DESTRUCTIVE} px-6 py-2.5 text-[14px] font-medium`}>
              Log out
            </button>
          </section>

          {/* Delete account */}
          <section className={`${CARD} flex flex-wrap items-center justify-between gap-4`} style={{ borderColor: "rgba(224,152,128,.35)" }}>
            <div>
              <div className="text-[16px] font-semibold" style={{ color: OUT }}>Delete account</div>
              <div className="text-muted-foreground mt-1 text-[13.5px]">
                Permanently erase your account and all your data — this cannot be undone.
              </div>
            </div>
            <button onClick={() => setDeleteOpen(true)} className={`${OUTLINED_DESTRUCTIVE} px-6 py-2.5 text-[14px] font-medium`}>
              Delete account
            </button>
          </section>

          <Dialog open={deleteOpen} onOpenChange={onDeleteDialogChange}>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>Delete account</DialogTitle>
                <DialogDescription>
                  This permanently deletes your account and all associated data — income sources,
                  savings, expenses and goals. This action cannot be undone.
                </DialogDescription>
              </DialogHeader>
              <div className="py-2">
                <label className="text-muted-foreground mb-2 block text-[13px]">
                  Type <span className="text-foreground font-semibold">DELETE</span> to confirm
                </label>
                <input
                  value={deleteConfirmText}
                  onChange={(e) => setDeleteConfirmText(e.target.value)}
                  aria-label="Type DELETE to confirm"
                  placeholder="DELETE"
                  className={FIELD}
                />
              </div>
              {deleteError && (
                <Alert variant="destructive">
                  <AlertDescription>{deleteError}</AlertDescription>
                </Alert>
              )}
              <DialogFooter>
                <button
                  onClick={() => onDeleteDialogChange(false)}
                  className={`${OUTLINED} px-5 py-2.5 text-[14px] font-medium`}
                >
                  Cancel
                </button>
                <button
                  onClick={handleDeleteAccount}
                  disabled={deleteConfirmText !== "DELETE" || deleting}
                  className="cursor-pointer border border-destructive bg-transparent px-5 py-2.5 text-[14px] font-semibold text-destructive hover:bg-destructive/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {deleting ? "Deleting…" : "Delete my account"}
                </button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        {/* Right */}
        <div className="flex min-w-[min(100%,360px)] flex-1 basis-[46%]">
          <SalaryCalculatorCard />
        </div>
      </div>
    </div>
  )
}
