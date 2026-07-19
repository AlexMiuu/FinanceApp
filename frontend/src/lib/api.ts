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

export function getAccessToken(): string | null {
  return accessToken
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

/**
 * Fetch wrapper: attaches the access token and retries once after a refresh
 * on 401. Pass raw=true to get the Response itself (file downloads).
 */
export async function api<T>(path: string, init?: RequestInit, raw = false): Promise<T> {
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
  if (raw) return res as unknown as T
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

// ---- Dashboard & reports (M3) ----

export type Dashboard = {
  month: string
  totalSpent: number
  mandatorySpent: number
  expenseCount: number
  previousMonthTotal: number
  projectedMonthEnd: number | null
  byCategory: { category: string; amount: number }[]
  byDay: { date: string; amount: number }[]
}

export const getDashboard = (month?: string) =>
  api<Dashboard>(`/api/v1/dashboard${month ? `?month=${month}` : ""}`)

export type ReportFilters = {
  from: string | null
  to: string | null
  categoryIds: string[] | null
}

export type ReportResult = {
  totalSpent: number
  expenseCount: number
  byCategory: Record<string, number>
  byMonth: Record<string, number>
}

export type Report = {
  id: string
  name: string
  filters: ReportFilters
  lastRunAt: string | null
  cachedResult: ReportResult | null
}

export const listReports = () => api<Report[]>("/api/v1/reports")

export const createReport = (name: string, filters: ReportFilters) =>
  api<Report>("/api/v1/reports", { method: "POST", body: JSON.stringify({ name, filters }) })

export const deleteReport = (id: string) =>
  api<void>(`/api/v1/reports/${id}`, { method: "DELETE" })

export const runReport = (id: string) =>
  api<ReportResult>(`/api/v1/reports/${id}/run`, { method: "POST" })

// ---- Profile & money (M4) ----

export type IncomeSource = {
  id: string
  name: string
  amount: number // bani
  recurrence: "MONTHLY" | "YEARLY" | "ONE_OFF"
  startDate: string
  endDate: string | null
}

export type IncomeInput = Omit<IncomeSource, "id">

export type SavingsAccount = {
  id: string
  name: string
  balance: number // bani
}

export type NetWorth = {
  total: number
  accounts: number
  monthlyIncome: number
}

export type SalaryBreakdown = {
  gross: number
  cas: number
  cass: number
  taxable: number
  incomeTax: number
  net: number
  rulesValidFrom: string
}

export const listIncomeSources = () => api<IncomeSource[]>("/api/v1/me/income-sources")
export const createIncomeSource = (body: IncomeInput) =>
  api<IncomeSource>("/api/v1/me/income-sources", { method: "POST", body: JSON.stringify(body) })
export const deleteIncomeSource = (id: string) =>
  api<void>(`/api/v1/me/income-sources/${id}`, { method: "DELETE" })

export const listSavings = () => api<SavingsAccount[]>("/api/v1/me/savings")
export const createSavings = (name: string, balance: number) =>
  api<SavingsAccount>("/api/v1/me/savings", { method: "POST", body: JSON.stringify({ name, balance }) })
export const updateSavings = (id: string, name: string, balance: number) =>
  api<SavingsAccount>(`/api/v1/me/savings/${id}`, { method: "PUT", body: JSON.stringify({ name, balance }) })
export const deleteSavings = (id: string) =>
  api<void>(`/api/v1/me/savings/${id}`, { method: "DELETE" })

export const getNetWorth = () => api<NetWorth>("/api/v1/me/net-worth")

export const calculateSalary = (mode: "GROSS_TO_NET" | "NET_TO_GROSS", amount: number) =>
  api<SalaryBreakdown>("/api/v1/salary-calculator", {
    method: "POST",
    body: JSON.stringify({ mode, amount }),
  })

// ---- Goals & calendar (M5) ----

export type Goal = {
  id: string
  name: string
  categoryId: string | null
  targetAmount: number // bani
  period: "DAILY" | "MONTHLY" | "YEARLY"
  startDate: string
  endDate: string | null
  active: boolean
  currentActual: number
  currentMet: boolean
  periodStart: string
  periodEnd: string
}

export type GoalInput = {
  name: string
  categoryId: string | null
  targetAmount: number
  period: Goal["period"]
  startDate: string
  endDate: string | null
}

export type DayStatus = "MET" | "MISSED" | "IN_PROGRESS" | "FUTURE" | "NO_GOAL"

export type CalendarDay = {
  date: string
  status: DayStatus
  totalSpent: number
  goals: { goalId: string; name: string; target: number; actual: number; met: boolean }[]
}

export type PeriodSummary = {
  goalId: string
  name: string
  target: number
  actual: number
  met: boolean
  inProgress: boolean
}

export type GoalCalendar = {
  month: string
  days: CalendarDay[]
  monthlyGoals: PeriodSummary[]
  yearlyGoals: PeriodSummary[]
}

export const listGoals = () => api<Goal[]>("/api/v1/goals")
export const createGoal = (body: GoalInput) =>
  api<Goal>("/api/v1/goals", { method: "POST", body: JSON.stringify(body) })
export const deleteGoal = (id: string) => api<void>(`/api/v1/goals/${id}`, { method: "DELETE" })
export const getCalendar = (month?: string) =>
  api<GoalCalendar>(`/api/v1/calendar${month ? `?month=${month}` : ""}`)

// ---- Recurring expenses (manual-flow robustness) ----

export type RecurringExpense = {
  id: string
  categoryId: string
  amount: number // bani
  note: string | null
  dayOfMonth: number
  nextRun: string
  active: boolean
}

export const listRecurring = () => api<RecurringExpense[]>("/api/v1/expenses/recurring")

export const createRecurring = (body: {
  amount: number
  categoryId: string
  note: string | null
  startDate: string
}) => api<RecurringExpense>("/api/v1/expenses/recurring", { method: "POST", body: JSON.stringify(body) })

export const deleteRecurring = (id: string) =>
  api<void>(`/api/v1/expenses/recurring/${id}`, { method: "DELETE" })

// ---- Quests & notifications (M6) ----

export type Quest = {
  id: string
  templateCode: string
  title: string
  status: "SUGGESTED" | "ACTIVE" | "COMPLETED" | "FAILED" | "DECLINED"
  kind: "CAP" | "DAYS"
  target: number // bani for CAP, day count for DAYS
  progress: number
  periodStart: string
  periodEnd: string
  params: Record<string, unknown>
}

export const listQuests = () => api<Quest[]>("/api/v1/quests")
export const acceptQuest = (id: string) =>
  api<{ status: string }>(`/api/v1/quests/${id}/accept`, { method: "POST" })
export const declineQuest = (id: string) =>
  api<{ status: string }>(`/api/v1/quests/${id}/decline`, { method: "POST" })

export type AppNotification = {
  id: string
  type: string
  title: string
  body: string
  createdAt: string
  read: boolean
}

export type NotificationList = {
  items: AppNotification[]
  unread: number
}

export const listNotifications = () => api<NotificationList>("/api/v1/notifications")
export const markNotificationRead = (id: string) =>
  api<{ read: boolean }>(`/api/v1/notifications/${id}/read`, { method: "POST" })
export const markAllNotificationsRead = () =>
  api<{ read: boolean }>("/api/v1/notifications/read-all", { method: "POST" })

/** Downloads the report CSV with the auth header, then triggers a save. */
export async function downloadReportCsv(id: string, name: string) {
  const res = await api<Response>(`/api/v1/reports/${id}/export`, undefined, true)
  const blob = await (res as Response).blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = `${name}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
