import { useState, type FormEvent } from "react"
import {
  createCategory,
  deleteCategory,
  updateCategory,
  type Category,
} from "@/lib/api"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { CloseIcon } from "@/components/brand"

const CTA = "cursor-pointer border border-[#4C93A6] bg-[#123945] text-[#C4E7F0] hover:bg-[#174756]"

const MANDATORY_HINT =
  "Mandatory categories are the bills you cannot skip — rent, utilities, transport to work. Reports subtract them before showing what was really discretionary."

export default function CategoriesTab({
  categories,
  onChanged,
}: {
  categories: Category[]
  onChanged: () => void
}) {
  const [error, setError] = useState<string | null>(null)
  const [parentId, setParentId] = useState<string>("none")
  const [mandatory, setMandatory] = useState(false)

  const parents = categories.filter((c) => c.parentId === null)
  const childrenOf = (id: string) => categories.filter((c) => c.parentId === id)

  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    const form = e.currentTarget // React nulls currentTarget after awaits
    const data = new FormData(form)
    try {
      await createCategory({
        name: String(data.get("name")),
        parentId: parentId === "none" ? null : parentId,
        isMandatory: mandatory,
      })
      form.reset()
      setMandatory(false)
      setParentId("none")
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Create failed")
    }
  }

  async function toggleMandatory(category: Category) {
    setError(null)
    try {
      await updateCategory(category.id, {
        name: category.name,
        isMandatory: !category.isMandatory,
      })
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Update failed")
    }
  }

  async function remove(id: string) {
    setError(null)
    try {
      await deleteCategory(id)
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Delete failed")
    }
  }

  async function rename(category: Category) {
    const next = window.prompt("Rename category", category.name)
    if (next === null) return
    const name = next.trim()
    if (!name || name === category.name) return
    setError(null)
    try {
      await updateCategory(category.id, { name, isMandatory: category.isMandatory })
      onChanged()
    } catch (err) {
      setError(err instanceof Error ? err.message : "Rename failed")
    }
  }

  function CategoryRow({ category, indent }: { category: Category; indent: boolean }) {
    return (
      <div className="flex items-center gap-3.5 border-b border-[#2A3033] px-6 py-3 last:border-b-0">
        <span
          className={`min-w-0 flex-1 truncate text-[14.5px] ${indent ? "text-muted-foreground pl-6" : ""}`}
        >
          {category.name}
        </span>
        <button
          type="button"
          role="switch"
          aria-checked={category.isMandatory}
          aria-label={category.isMandatory ? `Mark ${category.name} as optional` : `Mark ${category.name} as mandatory`}
          onClick={() => toggleMandatory(category)}
          className="flex h-6 w-11 flex-none cursor-pointer items-center border p-0.5"
          style={{
            background: category.isMandatory ? "#123945" : "transparent",
            borderColor: category.isMandatory ? "#4C93A6" : "var(--border)",
            justifyContent: category.isMandatory ? "flex-end" : "flex-start",
          }}
        >
          <span className="block size-[18px]" style={{ background: category.isMandatory ? "#C4E7F0" : "#62696D" }} />
        </button>
        <button
          type="button"
          onClick={() => rename(category)}
          className="status-tag text-muted-foreground hover:text-foreground flex-none cursor-pointer"
        >
          rename
        </button>
        <button
          type="button"
          onClick={() => remove(category.id)}
          aria-label={`Delete ${category.name}`}
          className="text-muted-foreground hover:text-destructive grid size-11 flex-none cursor-pointer place-items-center hover:bg-white/[0.06]"
        >
          <CloseIcon size={15} />
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-[22px]">
      <section className="ledger-card p-6">
        <div className="ledger-label mb-4">Add category</div>
        <form className="flex flex-col gap-3.5" onSubmit={submit}>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="cat-name" className="ledger-label !text-[10px]">
                Name
              </Label>
              <Input id="cat-name" name="name" required maxLength={60} className="min-h-11" />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label className="ledger-label !text-[10px]">Parent (optional)</Label>
              <Select value={parentId} onValueChange={setParentId}>
                <SelectTrigger className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="none">None — top level</SelectItem>
                  {parents.map((p) => (
                    <SelectItem key={p.id} value={p.id}>
                      {p.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <label className="flex items-start gap-2.5 text-[13.5px]">
            <Checkbox
              id="cat-mandatory"
              checked={mandatory}
              onCheckedChange={(v) => setMandatory(v === true)}
            />
            <span className="text-muted-foreground">
              Mandatory expense (rent, utilities…) — used to tailor quests
            </span>
          </label>
          <button type="submit" className={`${CTA} self-start px-5 py-2.5 text-[13.5px] font-semibold`}>
            + Add category
          </button>
        </form>
      </section>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <section className="ledger-card overflow-hidden !p-0">
        <div className="border-border flex items-center gap-3 border-b px-6 py-4">
          <h2 className="ledger-label flex-1">Your categories · {categories.length}</h2>
          <span className="ledger-label cursor-default" title={MANDATORY_HINT}>
            Mandatory
          </span>
        </div>
        {parents.length === 0 ? (
          <p className="text-muted-foreground px-6 py-6 text-center text-sm">No categories yet.</p>
        ) : (
          parents.map((parent) => (
            <div key={parent.id}>
              <CategoryRow category={parent} indent={false} />
              {childrenOf(parent.id).map((child) => (
                <CategoryRow key={child.id} category={child} indent />
              ))}
            </div>
          ))
        )}
      </section>
    </div>
  )
}
