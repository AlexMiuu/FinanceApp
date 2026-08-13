import { useEffect, useRef, useState } from "react"
import { useAuth } from "@/auth/AuthContext"
import { createExpense, type Category } from "@/lib/api"
import { lastUsedCategory, recordCategoryUse, topCategories } from "@/lib/categoryUsage"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { CloseIcon } from "@/components/brand"

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
      setError("An amount above zero is needed to notch the stick.")
      return
    }
    setSaving(true)
    try {
      await createExpense({ amount: bani, categoryId, note: note || null, expenseDate: date })
      recordCategoryUse(userId, categoryId)
      toast("Transaction added")
      onSaved?.()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Save failed")
    } finally {
      setSaving(false)
    }
  }

  const fieldClass =
    "text-foreground border-border w-full border bg-transparent px-4 py-3.5 text-[15px] outline-none focus-visible:border-primary"
  const fieldLabelClass = "ledger-label mt-6 mb-2.5 block text-[10.5px]"
  const amountError = error === "An amount above zero is needed to notch the stick."

  return (
    <div
      className="fixed inset-0 z-[80] flex justify-end"
      style={{ background: "rgba(4,8,12,0.55)", animation: "fadeIn .16s ease" }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="bg-popover border-border h-full w-[440px] max-w-[92vw] overflow-y-auto border-l p-7 shadow-[-24px_0_60px_rgba(0,0,0,0.5)]"
        style={{ animation: "sheetIn .22s cubic-bezier(.22,.9,.3,1)" }}
        role="dialog"
        aria-label="Record an expense"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="font-heading text-[22px] font-semibold tracking-tight">Record an expense</h2>
            <p className="text-muted-foreground mt-1.5 text-[13.5px]">
              Balance stays visible while you type
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground border-border hover:border-[#4C93A6] grid size-11 flex-none cursor-pointer place-items-center border bg-transparent hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-[-3px] focus-visible:outline-[#9AD4E3]"
          >
            <CloseIcon />
          </button>
        </div>

        <label className={fieldLabelClass}>Amount · RON</label>
        <input
          ref={amountRef}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && save()}
          inputMode="decimal"
          placeholder="0.00"
          aria-invalid={amountError}
          className={`${fieldClass} tnum font-mono text-2xl font-light ${
            amountError ? "border-destructive" : ""
          }`}
          style={amountError ? { boxShadow: "inset 2px 0 0 0 var(--destructive)" } : undefined}
        />
        {amountError && <p className="text-destructive mt-2 text-[12.5px]">{error}</p>}

        <label className={fieldLabelClass}>What was it</label>
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && save()}
          placeholder="Grocery run"
          className={fieldClass}
        />

        <label className={fieldLabelClass}>Category</label>
        {chips.length > 0 && (
          <div className="mb-3 flex flex-wrap gap-2">
            {chips.map((c) => {
              const active = categoryId === c.id
              return (
                <button
                  key={c.id}
                  onClick={() => setCategoryId(c.id)}
                  className={`status-tag cursor-pointer border px-3 py-1.5 transition-colors ${
                    active
                      ? "border-primary text-primary"
                      : "border-border text-muted-foreground hover:border-[#4C93A6]"
                  }`}
                >
                  {categoryLabel(categories, c.id)}
                </button>
              )
            })}
          </div>
        )}
        <div className="flex gap-3">
          <div className="min-w-0 flex-1">
            <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} />
          </div>
          <div className="min-w-0 flex-1">
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              aria-label="Date"
              className={`${fieldClass} font-mono`}
            />
          </div>
        </div>

        {error && !amountError && <p className="text-destructive mt-4 text-[13px]">{error}</p>}

        <p className="text-muted-foreground mt-7.5 text-[12px]">Nothing moves. This only records.</p>
        <div className="mt-3 flex gap-3">
          <button
            onClick={onClose}
            className="text-foreground border-border flex-1 cursor-pointer border bg-transparent py-3.5 text-[14.5px] hover:bg-popover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          >
            Cancel
          </button>
          <button
            onClick={save}
            disabled={saving}
            className="bg-[#123945] border-[#4C93A6] text-[#C4E7F0] hover:bg-[#174756] flex-[2] cursor-pointer border py-3.5 text-[14.5px] font-medium disabled:opacity-60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#9AD4E3]"
          >
            {saving ? "Cutting the notch…" : "Cut the notch"}
          </button>
        </div>
      </div>
    </div>
  )
}
