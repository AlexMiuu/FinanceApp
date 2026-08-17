// Behavioural checks for the frontend's DOM-free logic: dashboard rearrangement
// arithmetic and the D9 storage registry (namespacing, caps, eviction, clearing).
//
// This project has no frontend test runner yet, and these two areas are the ones
// a type-check cannot vouch for — it already caught a cross-user leak where the
// shared fallback object was being mutated in place. It is a deliberate stopgap:
// fold it into vitest when a runner is adopted.
//
//   npm run logic-check
import assert from 'node:assert/strict'

// Minimal Storage stand-in with the real semantics that matter here: key(i)
// ordering, and reindexing when an item is removed mid-iteration.
class FakeStorage {
  #map = new Map()
  get length() { return this.#map.size }
  key(i) { return [...this.#map.keys()][i] ?? null }
  getItem(k) { return this.#map.has(k) ? this.#map.get(k) : null }
  setItem(k, v) { this.#map.set(k, String(v)) }
  removeItem(k) { this.#map.delete(k) }
  clear() { this.#map.clear() }
}

const local = new FakeStorage()
const session = new FakeStorage()
globalThis.window = { localStorage: local, sessionStorage: session }

const m = await import('../.logic-check/bundle.js')
const { DEFAULT_LAYOUT, WIDGET_NAMES, reorder, moveToOtherColumn, dropAt,
        STORAGE_REGISTRY, readStored, writeStored, clearArgaliStorage,
        recordCategoryUse, lastUsedCategory, topCategories,
        normaliseNote, recentRepeats, categoryForNote } = m

let passed = 0
const check = (name, fn) => { fn(); passed++; console.log('  ok  ' + name) }

console.log('\n-- dashboard layout arithmetic --')

// The rearrangement arithmetic is catalogue-agnostic, so it is exercised on a
// synthetic layout rather than DEFAULT_LAYOUT. Pinning these to the real widget
// ids once meant that renaming a widget broke eleven unrelated assertions.
const L = { main: ['a', 'b'], side: ['c', 'd', 'e'] }

check('DEFAULT_LAYOUT places every known widget exactly once', () => {
  const placed = [...DEFAULT_LAYOUT.main, ...DEFAULT_LAYOUT.side]
  assert.deepEqual([...new Set(placed)].sort(), Object.keys(WIDGET_NAMES).sort(),
    'DEFAULT_LAYOUT and WIDGET_NAMES disagree — the server rejects a layout that is not a full permutation')
  assert.equal(placed.length, new Set(placed).size, 'a widget is placed twice')
})

check('reorder moves a widget down', () => {
  const out = reorder(L, 'main', 0, 1)
  assert.deepEqual(out.main, ['b', 'a'])
  assert.deepEqual(out.side, L.side)
})

check('reorder moves a widget up', () => {
  assert.deepEqual(reorder(L, 'side', 2, -1).side, ['c', 'e', 'd'])
})

check('reorder past either end is a no-op', () => {
  assert.equal(reorder(L, 'main', 0, -1), L)
  assert.equal(reorder(L, 'side', 2, 1), L)
})

check('reorder never mutates its input', () => {
  const before = structuredClone(L)
  reorder(L, 'main', 0, 1)
  assert.deepEqual(L, before)
})

check('moveToOtherColumn appends to the far column', () => {
  const out = moveToOtherColumn(L, 'side', 0)
  assert.deepEqual(out.side, ['d', 'e'])
  assert.deepEqual(out.main, ['a', 'b', 'c'])
})

check('a column may be emptied entirely', () => {
  let l = { main: ['a'], side: ['b', 'c', 'd', 'e'] }
  l = moveToOtherColumn(l, 'main', 0)
  assert.deepEqual(l.main, [])
  assert.equal(l.side.length, 5)
})

check('every widget survives any single move', () => {
  const all = (l) => [...l.main, ...l.side].sort().join(',')
  const expected = all(L)
  assert.equal(all(reorder(L, 'main', 0, 1)), expected)
  assert.equal(all(moveToOtherColumn(L, 'main', 1)), expected)
  assert.equal(all(dropAt(L, { column: 'side', index: 2 }, 'main', 0)), expected)
})

check('dropAt across columns inserts at the target index', () => {
  const out = dropAt(L, { column: 'side', index: 2 }, 'main', 0)
  assert.deepEqual(out.main, ['e', 'a', 'b'])
  assert.deepEqual(out.side, ['c', 'd'])
})

check('dropAt downward within a column compensates for the removal', () => {
  // ['c','d','e'] — dragging c onto e's slot should land it last, not second,
  // because removing it shifted e down.
  const out = dropAt(L, { column: 'side', index: 0 }, 'side', 2)
  assert.deepEqual(out.side, ['d', 'c', 'e'])
})

check('dropAt upward within a column needs no compensation', () => {
  const out = dropAt(L, { column: 'side', index: 2 }, 'side', 0)
  assert.deepEqual(out.side, ['e', 'c', 'd'])
})

check('dropAt at a column end appends', () => {
  const out = dropAt(L, { column: 'main', index: 0 }, 'side', 3)
  assert.deepEqual(out.side, ['c', 'd', 'e', 'a'])
  assert.deepEqual(out.main, ['b'])
})

console.log('\n-- D9: storage registry --')

check('every registry key is argali:-namespaced', () => {
  local.clear(); session.clear()
  writeStored(STORAGE_REGISTRY.categoryUsage, { counts: {}, last: null }, 'user-1')
  writeStored(STORAGE_REGISTRY.splashSeen, true)
  for (const store of [local, session]) {
    for (let i = 0; i < store.length; i++) {
      assert.ok(store.key(i).startsWith('argali:'), 'unnamespaced key: ' + store.key(i))
    }
  }
})

check('per-user keys are scoped by user id', () => {
  local.clear()
  writeStored(STORAGE_REGISTRY.categoryUsage, { counts: { a: 1 }, last: 'a' }, 'user-1')
  writeStored(STORAGE_REGISTRY.categoryUsage, { counts: { b: 9 }, last: 'b' }, 'user-2')
  assert.equal(local.length, 2)
  assert.equal(readStored(STORAGE_REGISTRY.categoryUsage, null, 'user-1').last, 'a')
  assert.equal(readStored(STORAGE_REGISTRY.categoryUsage, null, 'user-2').last, 'b')
})

check('the splash flag lives in sessionStorage, not localStorage', () => {
  local.clear(); session.clear()
  writeStored(STORAGE_REGISTRY.splashSeen, true)
  assert.equal(session.length, 1)
  assert.equal(local.length, 0)
  assert.equal(readStored(STORAGE_REGISTRY.splashSeen, false), true)
})

check('corrupted JSON falls back instead of throwing', () => {
  local.clear()
  local.setItem('argali:category-usage:user-1', '{not json')
  assert.deepEqual(readStored(STORAGE_REGISTRY.categoryUsage, { counts: {}, last: null }, 'user-1'),
    { counts: {}, last: null })
})

check('clearArgaliStorage wipes both stores', () => {
  local.clear(); session.clear()
  writeStored(STORAGE_REGISTRY.categoryUsage, { counts: { a: 1 }, last: 'a' }, 'user-1')
  writeStored(STORAGE_REGISTRY.splashSeen, true)
  assert.ok(local.length > 0 && session.length > 0)
  clearArgaliStorage()
  assert.equal(local.length, 0)
  assert.equal(session.length, 0)
})

check('clearArgaliStorage leaves another app\'s keys alone', () => {
  local.clear(); session.clear()
  local.setItem('someone-else:token', 'keep me')
  session.setItem('unrelated', 'keep me too')
  writeStored(STORAGE_REGISTRY.categoryUsage, { counts: {}, last: null }, 'user-1')
  clearArgaliStorage()
  assert.equal(local.getItem('someone-else:token'), 'keep me')
  assert.equal(session.getItem('unrelated'), 'keep me too')
  assert.equal(local.getItem('argali:category-usage:user-1'), null)
})

check('clearArgaliStorage removes every argali key despite reindexing', () => {
  local.clear()
  for (let i = 0; i < 25; i++) local.setItem('argali:junk-' + i, 'x')
  clearArgaliStorage()
  assert.equal(local.length, 0)
})

console.log('\n-- D9: categoryUsage is bounded --')

check('counts and last-used are recorded', () => {
  local.clear()
  recordCategoryUse('u', 'food')
  recordCategoryUse('u', 'food')
  recordCategoryUse('u', 'rent')
  assert.equal(lastUsedCategory('u'), 'rent')
  assert.deepEqual(topCategories('u', 2), ['food', 'rent'])
})

check('the record stops growing at the registry cap', () => {
  local.clear()
  const cap = STORAGE_REGISTRY.categoryUsage.cap
  for (let i = 0; i < cap * 5; i++) recordCategoryUse('u', 'cat-' + i)
  const stored = JSON.parse(local.getItem('argali:category-usage:u'))
  assert.equal(Object.keys(stored.counts).length, cap,
    `expected ${cap} entries, got ${Object.keys(stored.counts).length}`)
})

check('eviction keeps the most-used categories', () => {
  local.clear()
  const cap = STORAGE_REGISTRY.categoryUsage.cap
  for (let i = 0; i < 20; i++) recordCategoryUse('u', 'hot')      // 20 uses
  for (let i = 0; i < cap * 3; i++) recordCategoryUse('u', 'cold-' + i) // 1 use each
  const stored = JSON.parse(local.getItem('argali:category-usage:u'))
  assert.ok('hot' in stored.counts, 'the most-used category was evicted')
  assert.equal(stored.counts.hot, 20)
})

check('eviction never drops the last-used category', () => {
  local.clear()
  const cap = STORAGE_REGISTRY.categoryUsage.cap
  for (let i = 0; i < cap * 3; i++) recordCategoryUse('u', 'filler-' + i)
  for (let i = 0; i < 30; i++) recordCategoryUse('u', 'popular')
  recordCategoryUse('u', 'brand-new') // one use, but it is `last`
  const stored = JSON.parse(local.getItem('argali:category-usage:u'))
  assert.equal(stored.last, 'brand-new')
  assert.ok('brand-new' in stored.counts, 'the last-used category was evicted')
  assert.equal(lastUsedCategory('u'), 'brand-new')
})

check('a user with no history reads as empty, not undefined', () => {
  local.clear()
  assert.equal(lastUsedCategory('nobody'), null)
  assert.deepEqual(topCategories('nobody', 5), [])
})

console.log('\n-- add-expense suggestions --')

// Newest first, the order the API returns expenses in.
const expense = (id, note, amount, categoryId, expenseDate = '2026-08-01') =>
  ({ id, note, amount, categoryId, expenseDate, currency: 'RON' })

check('normaliseNote ignores case and stray whitespace', () => {
  assert.equal(normaliseNote('  Mega   Image '), 'mega image')
  assert.equal(normaliseNote('MEGA IMAGE'), normaliseNote('mega image'))
})

check('recentRepeats keeps newest first and collapses exact repeats', () => {
  const out = recentRepeats([
    expense('1', 'Mega Image', 14820, 'food'),
    expense('2', 'Mega Image', 14820, 'food'),
    expense('3', 'Rent', 124000, 'housing'),
  ], 5)
  assert.deepEqual(out.map((r) => r.id), ['1', '3'])
})

check('the same shop at a different amount is a separate suggestion', () => {
  const out = recentRepeats([
    expense('1', 'Mega Image', 14820, 'food'),
    expense('2', 'Mega Image', 9900, 'food'),
  ], 5)
  assert.equal(out.length, 2)
})

check('the same shop refiled to another category is a separate suggestion', () => {
  const out = recentRepeats([
    expense('1', 'Petrom', 32000, 'transport'),
    expense('2', 'Petrom', 32000, 'food'),
  ], 5)
  assert.equal(out.length, 2)
})

check('entries with no usable description are skipped', () => {
  const out = recentRepeats([
    expense('1', null, 100, 'food'),
    expense('2', '   ', 200, 'food'),
    expense('3', 'Kaufland', 300, 'food'),
  ], 5)
  assert.deepEqual(out.map((r) => r.id), ['3'])
})

check('recentRepeats honours its limit, including zero', () => {
  const many = Array.from({ length: 10 }, (_, i) => expense(String(i), 'Shop ' + i, 100 + i, 'food'))
  assert.equal(recentRepeats(many, 4).length, 4)
  assert.equal(recentRepeats(many, 0).length, 0)
  assert.deepEqual(recentRepeats([], 4), [])
})

check('recentRepeats trims the description it hands back', () => {
  assert.equal(recentRepeats([expense('1', '  Mega Image  ', 100, 'food')], 1)[0].note, 'Mega Image')
})

check('categoryForNote matches regardless of case and spacing', () => {
  const history = [expense('1', 'Mega Image', 14820, 'food')]
  assert.equal(categoryForNote(history, '  mega   IMAGE '), 'food')
})

check('categoryForNote returns the most recent filing, not the most frequent', () => {
  // Refiled to transport most recently, after two older food entries.
  const history = [
    expense('3', 'Petrom', 32000, 'transport'),
    expense('2', 'Petrom', 32000, 'food'),
    expense('1', 'Petrom', 32000, 'food'),
  ]
  assert.equal(categoryForNote(history, 'Petrom'), 'transport')
})

check('categoryForNote returns null for an unknown or blank description', () => {
  const history = [expense('1', 'Mega Image', 14820, 'food')]
  assert.equal(categoryForNote(history, 'Somewhere new'), null)
  assert.equal(categoryForNote(history, '   '), null)
  assert.equal(categoryForNote([], 'Mega Image'), null)
})

check('a null description in history never matches a blank input', () => {
  assert.equal(categoryForNote([expense('1', null, 100, 'food')], ''), null)
})

console.log(`\n${passed} checks passed\n`)
