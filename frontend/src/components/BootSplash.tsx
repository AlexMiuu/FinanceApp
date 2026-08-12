import { useEffect, useState } from "react"
import { ArgaliMark } from "@/components/brand"

/**
 * Session-intro splash: a faint mark sits behind the shell while the wordmark
 * rises and a progress rule fills, then the overlay fades out.
 *
 * Gating to once per session is the caller's job — see HomePage, which records
 * STORAGE_REGISTRY.splashSeen so a reload mid-session goes straight to the app.
 */
export function BootSplash({ onDone }: { onDone: () => void }) {
  const [gone, setGone] = useState(false)

  useEffect(() => {
    const done = setTimeout(onDone, 2800)
    const hide = setTimeout(() => setGone(true), 2800)
    return () => {
      clearTimeout(done)
      clearTimeout(hide)
    }
  }, [onDone])

  if (gone) return null

  return (
    <div
      className="fixed inset-0 z-[200] flex flex-col items-center justify-center gap-[34px] overflow-hidden"
      style={{
        background: "linear-gradient(160deg,#101416 0%,#0B0E10 100%)",
        animation: "bootOut .5s ease 2.3s forwards",
      }}
      role="status"
      aria-label="Loading Argali"
    >
      <div
        className="text-primary absolute"
        style={{
          right: "-9vw",
          top: "-13vh",
          width: "min(720px,68vw)",
          height: "min(720px,68vw)",
          opacity: 0.12,
          animation: "fadeIn 1.1s ease forwards",
        }}
      >
        <ArgaliMark withEye={false} className="h-full w-full" />
      </div>

      <div
        className="text-primary relative h-20 w-20"
        style={{ animation: "bootRise .7s ease both" }}
      >
        <ArgaliMark className="h-full w-full" />
      </div>

      <div
        className="text-foreground relative font-mono text-[26px] font-medium uppercase"
        style={{
          letterSpacing: "0.30em",
          paddingLeft: "0.30em",
          animation: "bootRise .7s ease .12s both",
        }}
      >
        Argali
      </div>

      <div
        className="relative flex flex-col items-center gap-4"
        style={{ animation: "fadeIn .8s ease .5s both" }}
      >
        <div className="border-border h-px w-[190px] overflow-hidden border-t">
          <div
            className="bg-primary h-px"
            style={{ animation: "bootFill 2.1s cubic-bezier(.35,0,.2,1) both" }}
          />
        </div>
        <div className="ledger-label text-[10.5px]">Securing your account</div>
      </div>
    </div>
  )
}
