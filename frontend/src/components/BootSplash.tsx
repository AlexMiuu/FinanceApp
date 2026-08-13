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
        <div className="flex h-[22px] items-end gap-[3px]" aria-hidden="true">
          <span className="w-[2px]" style={{ height: "100%", background: "#9AD4E3" }} />
          <span className="w-[2px]" style={{ height: "72%", background: "#9AD4E3" }} />
          <span className="w-[2px]" style={{ height: "88%", background: "#9AD4E3" }} />
          <span className="w-[2px]" style={{ height: "60%", background: "#4C93A6" }} />
          <span className="w-[2px]" style={{ height: "80%", background: "#4C93A6" }} />
          <span className="w-[1px]" style={{ height: "40%", background: "#62696D" }} />
          <span className="w-[1px]" style={{ height: "40%", background: "#62696D" }} />
        </div>
        {/* A figure that climbs is a figure that lies — the caption breathes, nothing counts up. */}
        <div className="ledger-label text-[10.5px]" style={{ animation: "shimmer 2.4s ease-in-out infinite" }}>
          Counting your notches
        </div>
      </div>
    </div>
  )
}
