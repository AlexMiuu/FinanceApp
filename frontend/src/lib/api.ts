export type User = {
  id: string
  email: string
  displayName: string
  avatarUrl: string | null
}

export type AuthResponse = {
  accessToken: string
  user: User
}

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json()
    return new ApiError(res.status, body.error ?? res.statusText)
  } catch {
    return new ApiError(res.status, res.statusText)
  }
}

/** Fetch wrapper: attaches the access token and retries once after a refresh on 401. */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const doFetch = () =>
    fetch(path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...init?.headers,
      },
    })

  let res = await doFetch()
  if (res.status === 401 && accessToken) {
    const refreshed = await refreshSession()
    if (!refreshed) throw new ApiError(401, "Session expired")
    res = await doFetch()
  }
  if (!res.ok) throw await parseError(res)
  if (res.status === 204) return undefined as T
  return res.json()
}

/** Bootstraps or extends the session from the HttpOnly refresh cookie. */
export async function refreshSession(): Promise<AuthResponse | null> {
  const res = await fetch("/api/v1/auth/refresh", { method: "POST" })
  if (!res.ok) {
    setAccessToken(null)
    return null
  }
  const body: AuthResponse = await res.json()
  setAccessToken(body.accessToken)
  return body
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  })
  if (!res.ok) throw await parseError(res)
  const body: AuthResponse = await res.json()
  setAccessToken(body.accessToken)
  return body
}

export async function register(
  email: string,
  password: string,
  displayName: string
): Promise<AuthResponse> {
  const res = await fetch("/api/v1/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, displayName }),
  })
  if (!res.ok) throw await parseError(res)
  const body: AuthResponse = await res.json()
  setAccessToken(body.accessToken)
  return body
}

export async function logout(): Promise<void> {
  await fetch("/api/v1/auth/logout", { method: "POST" })
  setAccessToken(null)
}

export async function oauthProviders(): Promise<{ google: boolean }> {
  const res = await fetch("/api/v1/auth/oauth/providers")
  if (!res.ok) return { google: false }
  return res.json()
}

// ---- Categories & expenses (M2) ----

export type Category = {
  id: string
  name: string
  parentId: string | null
  isMandatory: boolean
}

export type Expense = {
  id: string
  amount: number // bani (RON cents)
  currency: string
  categoryId: string
  note: string | null
  expenseDate: string // ISO date
}

export type ExpensePage = {
  items: Expense[]
  page: number
  size: number
  totalElements: number
}

export const getCategories = () => api<Category[]>("/api/v1/categories")

export const createCategory = (body: {
  name: string
  parentId: string | null
  isMandatory: boolean
}) => api<Category>("/api/v1/categories", { method: "POST", body: JSON.stringify(body) })

export const updateCategory = (id: string, body: { name: string; isMandatory: boolean }) =>
  api<Category>(`/api/v1/categories/${id}`, { method: "PUT", body: JSON.stringify(body) })

export const deleteCategory = (id: string) =>
  api<void>(`/api/v1/categories/${id}`, { method: "DELETE" })

export type ExpenseInput = {
  amount: number
  categoryId: string
  note: string | null
  expenseDate: string
}

export const listExpenses = (filters: {
  from?: string
  to?: string
  categoryId?: string
  page?: number
}) => {
  const params = new URLSearchParams()
  if (filters.from) params.set("from", filters.from)
  if (filters.to) params.set("to", filters.to)
  if (filters.categoryId) params.set("categoryId", filters.categoryId)
  if (filters.page) params.set("page", String(filters.page))
  return api<ExpensePage>(`/api/v1/expenses?${params}`)
}

export const createExpense = (body: ExpenseInput) =>
  api<Expense>("/api/v1/expenses", { method: "POST", body: JSON.stringify(body) })

export const updateExpense = (id: string, body: ExpenseInput) =>
  api<Expense>(`/api/v1/expenses/${id}`, { method: "PUT", body: JSON.stringify(body) })

export const deleteExpense = (id: string) =>
  api<void>(`/api/v1/expenses/${id}`, { method: "DELETE" })

export const formatRon = (bani: number) =>
  new Intl.NumberFormat("ro-RO", { style: "currency", currency: "RON" }).format(bani / 100)
