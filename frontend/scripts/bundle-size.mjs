// Throwaway measurement helper for the M10 code-splitting exit criterion.
// Walks the built chunks' *static* import graph to work out what a browser must
// download before a given screen can paint.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { gzipSync } from 'node:zlib'

const dir = 'dist/assets'
const files = readdirSync(dir).filter((f) => f.endsWith('.js'))

const staticDeps = new Map()
for (const f of files) {
  const src = readFileSync(`${dir}/${f}`, 'utf8')
  const deps = new Set()
  for (const m of src.matchAll(
    /(?:^|[;\n}])\s*import\s*(?:[^"';]*?\s*from\s*)?["']\.\/([^"']+\.js)["']/g
  )) {
    deps.add(m[1])
  }
  for (const m of src.matchAll(/export\s*\*\s*from\s*["']\.\/([^"']+\.js)["']/g)) {
    deps.add(m[1])
  }
  staticDeps.set(f, deps)
}

const find = (prefix) => files.find((f) => f.startsWith(prefix + '-'))

function closure(roots) {
  const seen = new Set()
  const stack = [...roots]
  while (stack.length) {
    const f = stack.pop()
    if (!f || seen.has(f)) continue
    seen.add(f)
    for (const d of staticDeps.get(f) ?? []) stack.push(d)
  }
  return seen
}

function report(label, roots) {
  const set = closure(roots)
  let raw = 0
  let gz = 0
  for (const f of set) {
    const buf = readFileSync(`${dir}/${f}`)
    raw += buf.length
    gz += gzipSync(buf).length
  }
  console.log(
    `${label}: ${(raw / 1024).toFixed(2)} kB raw / ${(gz / 1024).toFixed(2)} kB gzip (${set.size} chunks)`
  )
  for (const f of [...set].sort()) console.log('   ' + f)
  console.log('')
}

report('Signed-out (auth screen)', [find('index'), find('rolldown-runtime'), find('AuthPage')])
report('Dashboard first paint', [
  find('index'),
  find('rolldown-runtime'),
  find('HomePage'),
  find('DashboardTab'),
])

const total = files.reduce((s, f) => s + statSync(`${dir}/${f}`).size, 0)
console.log('All JS emitted:', (total / 1024).toFixed(2), 'kB raw')
