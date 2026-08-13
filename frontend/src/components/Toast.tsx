import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react"
import { CheckIcon } from "@/components/brand"

type Toast = { message: string }

const ToastContext = createContext<(message: string) => void>(() => {})

export function useToast() {
  return useContext(ToastContext)
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout>>(null)

  const show = useCallback((message: string) => {
    if (timer.current) clearTimeout(timer.current)
    setToast({ message })
    // Confirmation layer, not an alert — success toasts settle in 5s (V5 §Alerts and toasts).
    timer.current = setTimeout(() => setToast(null), 5000)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="bg-popover border-border edge-mark-good fixed bottom-7 left-7 z-50 flex items-center gap-3 border px-4 py-3 shadow-[0_12px_24px_rgba(0,0,0,0.4)]"
          style={{ animation: "riseIn .2s ease" }}
        >
          <span className="text-good grid size-6 flex-none place-items-center">
            <CheckIcon size={14} />
          </span>
          <span className="text-[13.5px] font-medium">{toast.message}</span>
        </div>
      )}
    </ToastContext.Provider>
  )
}
