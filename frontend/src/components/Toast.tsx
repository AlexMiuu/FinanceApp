import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react"

type Toast = { icon: string; message: string }

const ToastContext = createContext<(icon: string, message: string) => void>(() => {})

export function useToast() {
  return useContext(ToastContext)
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(null)

  const show = useCallback((icon: string, message: string) => {
    if (timer.current) clearTimeout(timer.current)
    setToast({ icon, message })
    timer.current = setTimeout(() => setToast(null), 2600)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div
          role="status"
          className="bg-popover fixed bottom-7 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 rounded-xl border px-4 py-3 shadow-2xl"
          style={{ animation: "toast-in .25s ease" }}
        >
          <span className="text-base">{toast.icon}</span>
          <span className="text-sm font-medium">{toast.message}</span>
        </div>
      )}
    </ToastContext.Provider>
  )
}
