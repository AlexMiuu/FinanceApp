// The smallest routing this app needs.
//
// Argali has one page reachable without an account (the salary calculator) and
// the signed-in app behind everything else. A routing library would be a
// reasonable call once there are more — password reset, a marketing page — but
// for two paths it would be more dependency than routing, and the bundle is
// deliberately watched here (see `npm run bundle-size`).
//
// nginx already serves index.html for unknown paths (`try_files ... /index.html`),
// so a deep link to a route below survives a hard refresh.

import { useEffect, useState } from "react"

/** Every path the app answers on. Anything else falls back to the app itself. */
export const ROUTES = {
  /** Romanian, because the search terms that should find this page are Romanian. */
  salaryCalculator: "/calculator-salariu",
} as const

/** Normalised current path — no trailing slash, so `/x` and `/x/` are one route. */
function currentPath(): string {
  const path = window.location.pathname
  return path.length > 1 && path.endsWith("/") ? path.slice(0, -1) : path
}

/** Re-renders on back/forward and on {@link navigate}. */
export function usePath(): string {
  const [path, setPath] = useState(currentPath)

  useEffect(() => {
    const onChange = () => setPath(currentPath())
    window.addEventListener("popstate", onChange)
    // pushState does not fire popstate, so navigate() announces itself.
    window.addEventListener("argali:navigate", onChange)
    return () => {
      window.removeEventListener("popstate", onChange)
      window.removeEventListener("argali:navigate", onChange)
    }
  }, [])

  return path
}

/** Client-side navigation. Falls back to a full load if history is unavailable. */
export function navigate(to: string): void {
  if (currentPath() === to) return
  try {
    window.history.pushState(null, "", to)
    window.dispatchEvent(new Event("argali:navigate"))
  } catch {
    window.location.assign(to)
  }
}
