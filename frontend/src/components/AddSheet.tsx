import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { createExpense, type Category } from "@/lib/api"
import { lastUsedCategory, recordCategoryUse, topCategories } from "@/lib/categoryUsage"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"

const today = () => new Date().toISOString().slice(0, 10)

function categoryLabel(categories: Category[], id: string): string {
  const category = categories.find((c) => c.id === id)
  if (!category) return "—"
  if (!category.parentId) return category.name
  const parent = categories.find((c) => c.id === category.parentId)
  return parent ? `${parent.name} › ${category.name}` : category.name
}

/**
 * Slide-over for a quick expense entry — the balance and everything behind stay
 * visible while you type. Wired to the real expense API.
 */
export function AddSheet({
  open,
  onClose,
  categories,
  onSaved,
}: {
  open: boolean
  onClose: () => void
  categories: Category[]
  onSaved?: () => void
}) {
  const { user } = useAuth()
  const userId = user?.id ?? "anon"
  const toast = useToast()

  const [amount, setAmount] = useState("")
  const [note, setNote] = useState("")
  const [date, setDate] = useState(today())
  const [categoryId, setCategoryId] = useState<string | undefined>()
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const amountRef = useRef<HTMLInputElement | null>(null)

  const chips = topCategories(userId, 5)
    .map((id) => categories.find((c) => c.id === id))
    .filter((c): c is Category => c !== undefined)

  // Fresh defaults each time it opens; focus the amount.
  useEffect(() => {
    if (!open) return
    setAmount("")
    setNote("")
    setDate(today())
    setCategoryId(lastUsedCategory(userId) ?? undefined)
    setError(null)
    const t = setTimeout(() => amountRef.current?.focus(), 60)
    return () => clearTimeout(t)
  }, [open, userId])

  // Escape closes.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose()
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [open, onClose])

  if (!open) return null

  async function save() {
    setError(null)
    if (!categoryId) {
      setError("Pick a category")
      return
    }
    const bani = Math.round(parseFloat(amount) * 100)
    if (!bani || bani <= 0) {
      setError("Enter a valid amount")
      return
    }
    setSaving(true)
    try {
      await createExpense({ amount: bani, categoryId, note: note || null, expenseDate: date })
      recordCategoryUse(userId, categoryId)
      toast("✓", "Transaction added")
      onSaved?.()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    } finally {
      setSaving(false)
    }
  }

  const fieldClass =
    "text-foreground w-full rounded-xl border border-white/10 bg-[#241C17] px-4.5 py-3.5 text-[15px] outline-none focus-visible:border-primary"

  return (
    <div
      className="fixed inset-0 z-[80] flex justify-end"
      style={{ background: "rgba(4,8,12,0.55)", animation: "fadeIn .16s ease" }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-card h-full w-[430px] max-w-[92vw] overflow-y-auto border-l border-white/10 p-7 shadow-[-24px_0_60px_rgba(0,0,0,0.5)]"
        style={{ animation: "sheetIn .22s cubic-bezier(.22,.9,.3,1)" }}
        role="dialog"
        aria-label="Add transaction"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[22px] font-semibold tracking-tight">Add transaction</h2>
            <p className="text-muted-foreground mt-1.5 text-[13.5px]">
              Balance stays visible while you type
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground grid size-9 flex-none cursor-pointer place-items-center rounded-[10px] border border-white/10 bg-white/[0.06] text-[15px] hover:bg-white/[0.12]"
          >
            ✕
          </button>
        </div>

        <label className="text-muted-foreground mt-6.5 mb-2.5 block text-[13px]">Amount (RON)</label>
        <input
          ref={amountRef}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && save()}
          inputMode="decimal"
          placeholder="0.00"
          className={`${fieldClass} tnum font-mono text-2xl font-bold`}
        />

        <label className="text-muted-foreground mt-5 mb-2.5 block text-[13px]">Description</label>
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && save()}
          placeholder="Grocery run"
          className={fieldClass}
        />

        <label className="text-muted-foreground mt-5 mb-2.5 block text-[13px]">Category</label>
        {chips.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-2.5">
            {chips.map((c) => {
              const active = categoryId === c.id
              return (
                <button
                  key={c.id}
                  onClick={() => setCategoryId(c.id)}
                  className={`cursor-pointer rounded-full border px-3.5 py-2 text-[13.5px] transition-colors ${
                    active
                      ? "border-primary bg-primary/15 text-foreground"
                      : "border-white/12 bg-white/[0.05] hover:border-primary"
                  }`}
                >
                  {categoryLabel(categories, c.id)}
                </button>
              )
            })}
          </div>
        )}
        <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} />

        <label className="text-muted-foreground mt-5 mb-2.5 block text-[13px]">Date</label>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className={`${fieldClass} font-mono`}
        />

        {error && <p className="text-destructive mt-4 text-[13px]">{error}</p>}

        <div className="mt-7.5 flex gap-3">
          <button
            onClick={onClose}
            className="text-foreground flex-1 cursor-pointer rounded-xl border border-white/15 bg-transparent py-3.5 text-[14.5px] hover:bg-white/[0.06]"
          >
            Cancel
          </button>
          <button
            onClick={save}
            disabled={saving}
            className="bg-primary text-primary-foreground flex-[2] cursor-pointer rounded-xl border-none py-3.5 text-[14.5px] font-semibold shadow-[0_8px_22px_rgba(217,169,122,0.35)] hover:bg-[#D8B27A] disabled:opacity-60"
          >
            {saving ? "Saving…" : "Save transaction"}
          </button>
        </div>
      </div>
    </div>
  )
}
