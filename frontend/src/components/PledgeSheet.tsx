import { useEffect, useRef, useState } from "react"
import { createOath, type Category } from "@/lib/api"
import { useToast } from "@/components/Toast"
import { CategorySelect } from "@/components/CategorySelect"
import { CloseIcon } from "@/components/brand"

function categoryLabel(categories: Category[], id: string | undefined): string {
  const category = categories.find((c) => c.id === id)
  return category?.name ?? "a category"
}

function endOfWeek(): Date {
  const now = new Date()
  const day = now.getDay() // 0=Sun..6=Sat
  const untilSunday = day === 0 ? 0 : 7 - day
  const d = new Date(now)
  d.setDate(now.getDate() + untilSunday)
  d.setHours(23, 59, 59, 999)
  return d
}

function endOfMonth(): Date {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth() + 1, 0, 23, 59, 59, 999)
}

type ExpiryPreset = "week" | "month"

const PRESETS: { key: ExpiryPreset; label: string; compute: () => Date }[] = [
  { key: "week", label: "This week", compute: endOfWeek },
  { key: "month", label: "This month", compute: endOfMonth },
]

/**
 * Slide-over for swearing an oath — a forward-looking spending pledge, distinct
 * from AddSheet's backward-looking expense record. Mirrors its interaction
 * pattern (category picker, amount, save/cancel) but never records anything.
 */
export function PledgeSheet({
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
  const toast = useToast()

  const [amount, setAmount] = useState("")
  const [categoryId, setCategoryId] = useState<string | undefined>()
  const [preset, setPreset] = useState<ExpiryPreset>("week")
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const amountRef = useRef<HTMLInputElement | null>(null)

  useEffect(() => {
    if (!open) return
    setAmount("")
    setCategoryId(undefined)
    setPreset("week")
    setError(null)
    const t = setTimeout(() => amountRef.current?.focus(), 60)
    return () => clearTimeout(t)
  }, [open])

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
    const expiresAt = PRESETS.find((p) => p.key === preset)!.compute().toISOString()
    setSaving(true)
    try {
      await createOath({ categoryId, pledgedAmount: bani, expiresAt })
      toast("Oath sworn")
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

  const previewAmount =
    amount && !Number.isNaN(parseFloat(amount)) ? `${parseFloat(amount).toFixed(2)} RON` : "—"
  const previewCategory = categoryLabel(categories, categoryId)
  const previewWindow = PRESETS.find((p) => p.key === preset)!.label.toLowerCase()

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
        aria-label="Take an oath"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-[22px] font-semibold tracking-tight">Take an oath</h2>
            <p className="text-muted-foreground mt-1.5 text-[13.5px]">
              A pledge, sworn before you spend — not a record after
            </p>
          </div>
          <button
            onClick={onClose}
            aria-label="Close"
            className="text-muted-foreground hover:text-foreground grid size-9 flex-none cursor-pointer place-items-center rounded-[10px] border border-white/10 bg-white/[0.06] hover:bg-white/[0.12]"
          >
            <CloseIcon />
          </button>
        </div>

        <p className="text-muted-foreground mt-6 text-[13.5px]">
          Pledge to spend no more than{" "}
          <span className="text-foreground font-medium">{previewAmount}</span> on{" "}
          <span className="text-foreground font-medium">{previewCategory}</span> {previewWindow}.
        </p>

        <label className="text-muted-foreground mt-6 mb-2.5 block text-[13px]">Pledged amount (RON)</label>
        <input
          ref={amountRef}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && save()}
          inputMode="decimal"
          placeholder="0.00"
          className={`${fieldClass} tnum font-mono text-2xl font-bold`}
        />

        <label className="text-muted-foreground mt-5 mb-2.5 block text-[13px]">Category</label>
        <CategorySelect categories={categories} value={categoryId} onChange={setCategoryId} />

        <label className="text-muted-foreground mt-5 mb-2.5 block text-[13px]">Window</label>
        <div className="flex gap-2.5">
          {PRESETS.map((p) => (
            <button
              key={p.key}
              onClick={() => setPreset(p.key)}
              className={`flex-1 cursor-pointer rounded-xl border px-3.5 py-3 text-[13.5px] transition-colors ${
                preset === p.key
                  ? "border-primary bg-primary/15 text-foreground"
                  : "border-white/12 bg-white/[0.05] hover:border-primary"
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>

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
            {saving ? "Swearing…" : "Swear the oath"}
          </button>
        </div>
      </div>
    </div>
  )
}
