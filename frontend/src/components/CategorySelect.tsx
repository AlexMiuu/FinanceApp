import type { Category } from "@/lib/api"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

/** Category dropdown grouped by top-level category. */
export function CategorySelect({
  categories,
  value,
  onChange,
  placeholder = "Category",
  allowAll = false,
}: {
  categories: Category[]
  value: string | undefined
  onChange: (id: string | undefined) => void
  placeholder?: string
  allowAll?: boolean
}) {
  const parents = categories.filter((c) => c.parentId === null)
  const childrenOf = (parentId: string) => categories.filter((c) => c.parentId === parentId)

  return (
    <Select
      value={value ?? (allowAll ? "all" : undefined)}
      onValueChange={(v) => onChange(v === "all" ? undefined : v)}
    >
      <SelectTrigger className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {allowAll && <SelectItem value="all">All categories</SelectItem>}
        {parents.map((parent) => (
          <SelectGroup key={parent.id}>
            <SelectLabel>{parent.name}</SelectLabel>
            <SelectItem value={parent.id}>{parent.name}</SelectItem>
            {childrenOf(parent.id).map((child) => (
              <SelectItem key={child.id} value={child.id}>
                {parent.name} › {child.name}
              </SelectItem>
            ))}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  )
}
