import { useState, type FormEvent } from "react"
import { calculateSalary, createIncomeSource, formatRon, type SalaryBreakdown } from "@/lib/api"
import { useToast } from "@/components/Toast"
import { Alert, AlertDescription } from "@/components/ui/alert"

const GOOD = "#8FC7A6"
const OUT = "#E09880"

const FIELD =
  "text-foreground w-full border border-border bg-transparent px-3.5 py-2.5 text-sm outline-none focus-visible:border-[#4C93A6]"
const CTA =
  "cursor-pointer border border-[#4C93A6] bg-[#123945] text-[#C4E7F0] hover:bg-[#174756]"

const today = () => new Date().toISOString().slice(0, 10)
const toBani = (v: FormDataEntryValue | null) => Math.round(parseFloat(String(v)) * 100)

/**
 * The Romanian gross/net salary breakdown.
 *
 * Used in two places, which is why it takes `onIncomeSaved` rather than reading
 * the session itself: on the Account tab, where a result can be kept as an income
 * source, and on the signed-out page at {@link ROUTES.salaryCalculator}, where it
 * is offered to people without an account.
 *
 * Omitting `onIncomeSaved` removes the only call that writes anything, so the
 * signed-out variant has no write path at all rather than one hidden behind a
 * flag — `POST /api/v1/salary-calculator` is the single request it can make.
 */
export function SalaryCalculator({ onIncomeSaved }: { onIncomeSaved?: () => void }) {
  const toast = useToast()
  const [mode, setMode] = useState<"GROSS_TO_NET" | "NET_TO_GROSS">("GROSS_TO_NET")
  const [result, setResult] = useState<SalaryBreakdown | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [calculating, setCalculating] = useState(false)
  const [savingIncome, setSavingIncome] = useState(false)

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setCalculating(true)
    const data = new FormData(e.currentTarget)
    try {
      setResult(await calculateSalary(mode, toBani(data.get("salary"))))
    } catch (err) {
      setError(err instanceof Error ? err.message : "We could not work that out just now.")
    } finally {
      setCalculating(false)
    }
  }

  async function saveAsIncome() {
    if (!result || !onIncomeSaved) return
    setError(null)
    setSavingIncome(true)
    try {
      await createIncomeSource({
        name: "Salary (calculated)",
        amount: result.net,
        recurrence: "MONTHLY",
        startDate: today(),
        endDate: null,
      })
      toast("Saved as income source")
      onIncomeSaved()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    } finally {
      setSavingIncome(false)
    }
  }

  const row = "flex items-baseline justify-between border-b border-[#2A3033] py-3.5 text-[15px]"

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-[20px] font-semibold">Salary calculator</h2>
        <span className="border-border flex gap-0.5 border p-[3px]">
          {(["GROSS_TO_NET", "NET_TO_GROSS"] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setMode(m)}
              aria-pressed={mode === m}
              className={`cursor-pointer border-none px-3 py-1 font-mono text-[12.5px] font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3] ${
                mode === m ? "bg-primary/15 text-primary" : "text-muted-foreground bg-transparent"
              }`}
            >
              {m === "GROSS_TO_NET" ? "gross → net" : "net → gross"}
            </button>
          ))}
        </span>
      </div>

      <form className="mt-5.5" onSubmit={submit}>
        <label htmlFor="salary-amount" className="text-muted-foreground mb-2.5 block text-[13.5px]">
          {mode === "GROSS_TO_NET" ? "Gross salary (RON)" : "Desired net salary (RON)"}
        </label>
        <div className="flex gap-2.5">
          <input
            id="salary-amount"
            name="salary"
            type="number"
            step="0.01"
            min="1"
            required
            inputMode="decimal"
            placeholder="0.00"
            className={`${FIELD} figure !py-4 text-[22px]`}
          />
          <button
            type="submit"
            disabled={calculating}
            className={`${CTA} px-5 text-[14px] font-semibold disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]`}
          >
            {calculating ? "…" : "Go"}
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

      {result && (
        <div className="border-border mt-5.5 border-t pt-5.5">
          <div className="ledger-label mb-2.5">Where it goes</div>
          <div className="border-border flex h-[26px] border">
            <span style={{ flex: Math.max(result.cas, 0.0001), background: "#4C93A6" }} />
            <span style={{ flex: Math.max(result.cass, 0.0001), background: "#2E6E80", borderLeft: "1px solid #0E1113" }} />
            <span style={{ flex: Math.max(result.incomeTax, 0.0001), background: OUT, borderLeft: "1px solid #0E1113" }} />
            <span style={{ flex: Math.max(result.net, 0.0001), background: "#9AD4E3", borderLeft: "1px solid #0E1113" }} />
          </div>
          <div className="text-muted-foreground mt-2.5 flex flex-wrap gap-x-5 gap-y-1.5 font-mono text-[10.5px] tracking-[0.08em] uppercase">
            <span className="flex items-center gap-1.5">
              <span className="size-2.5" style={{ background: "#4C93A6" }} /> CAS {Math.round((result.cas / result.gross) * 100)}%
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5" style={{ background: "#2E6E80" }} /> CASS {Math.round((result.cass / result.gross) * 100)}%
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5" style={{ background: OUT }} /> Tax {Math.round((result.incomeTax / result.gross) * 100)}%
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2.5" style={{ background: "#9AD4E3" }} /> Net {Math.round((result.net / result.gross) * 100)}%
            </span>
          </div>
          {onIncomeSaved && (
            <button
              onClick={saveAsIncome}
              disabled={savingIncome}
              className={`${CTA} mt-5 px-4 py-2.5 text-[13.5px] font-semibold disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]`}
            >
              {savingIncome ? "Saving…" : "Save as income source"}
            </button>
          )}
        </div>
      )}
    </>
  )
}
