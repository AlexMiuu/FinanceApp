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
const { DEFAULT_LAYOUT, reorder, moveToOtherColumn, dropAt,
        STORAGE_REGISTRY, readStored, writeStored, clearArgaliStorage,
        recordCategoryUse, lastUsedCategory, topCategories } = m

let passed = 0
const check = (name, fn) => { fn(); passed++; console.log('  ok  ' + name) }

console.log('\n-- dashboard layout arithmetic --')

check('reorder moves a widget down', () => {
  const out = reorder(DEFAULT_LAYOUT, 'main', 0, 1)
  assert.deepEqual(out.main, ['breakdown', 'balance'])
  assert.deepEqual(out.side, DEFAULT_LAYOUT.side)
})

check('reorder moves a widget up', () => {
  assert.deepEqual(reorder(DEFAULT_LAYOUT, 'side', 2, -1).side, ['savings', 'quests', 'streak'])
})

check('reorder past either end is a no-op', () => {
  assert.equal(reorder(DEFAULT_LAYOUT, 'main', 0, -1), DEFAULT_LAYOUT)
  assert.equal(reorder(DEFAULT_LAYOUT, 'side', 2, 1), DEFAULT_LAYOUT)
})

check('reorder never mutates its input', () => {
  const before = structuredClone(DEFAULT_LAYOUT)
  reorder(DEFAULT_LAYOUT, 'main', 0, 1)
  assert.deepEqual(DEFAULT_LAYOUT, before)
})

check('moveToOtherColumn appends to the far column', () => {
  const out = moveToOtherColumn(DEFAULT_LAYOUT, 'side', 0)
  assert.deepEqual(out.side, ['streak', 'quests'])
  assert.deepEqual(out.main, ['balance', 'breakdown', 'savings'])
})

check('a column may be emptied entirely', () => {
  let l = { main: ['balance'], side: ['breakdown', 'savings', 'streak', 'quests'] }
  l = moveToOtherColumn(l, 'main', 0)
  assert.deepEqual(l.main, [])
  assert.equal(l.side.length, 5)
})

check('every widget survives any single move', () => {
  const all = (l) => [...l.main, ...l.side].sort().join(',')
  const expected = all(DEFAULT_LAYOUT)
  assert.equal(all(reorder(DEFAULT_LAYOUT, 'main', 0, 1)), expected)
  assert.equal(all(moveToOtherColumn(DEFAULT_LAYOUT, 'main', 1)), expected)
  assert.equal(all(dropAt(DEFAULT_LAYOUT, { column: 'side', index: 2 }, 'main', 0)), expected)
})

check('dropAt across columns inserts at the target index', () => {
  const out = dropAt(DEFAULT_LAYOUT, { column: 'side', index: 2 }, 'main', 0)
  assert.deepEqual(out.main, ['quests', 'balance', 'breakdown'])
  assert.deepEqual(out.side, ['savings', 'streak'])
})

check('dropAt downward within a column compensates for the removal', () => {
  // ['savings','streak','quests'] — dragging savings onto quests' slot should
  // land it last, not second, because removing it shifted quests down.
  const out = dropAt(DEFAULT_LAYOUT, { column: 'side', index: 0 }, 'side', 2)
  assert.deepEqual(out.side, ['streak', 'savings', 'quests'])
})

check('dropAt upward within a column needs no compensation', () => {
  const out = dropAt(DEFAULT_LAYOUT, { column: 'side', index: 2 }, 'side', 0)
  assert.deepEqual(out.side, ['quests', 'savings', 'streak'])
})

check('dropAt at a column end appends', () => {
  const out = dropAt(DEFAULT_LAYOUT, { column: 'main', index: 0 }, 'side', 3)
  assert.deepEqual(out.side, ['savings', 'streak', 'quests', 'balance'])
  assert.deepEqual(out.main, ['breakdown'])
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

console.log(`\n${passed} checks passed\n`)
