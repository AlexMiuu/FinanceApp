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
    timer.current = setTimeout(() => setToast(null), 2600)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div
          role="status"
          className="bg-popover border-border fixed bottom-7 left-1/2 z-50 flex -translate-x-1/2 items-center gap-3 border px-4 py-3 shadow-[0_12px_24px_rgba(0,0,0,0.4)]"
          style={{ animation: "toast-in .25s ease" }}
        >
          <span className="bg-primary/15 text-primary grid size-6 flex-none place-items-center rounded-full">
            <CheckIcon size={14} />
          </span>
          <span className="text-[13.5px] font-medium">{toast.message}</span>
        </div>
      )}
    </ToastContext.Provider>
  )
}
