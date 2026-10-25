const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const context = vm.createContext({ Date, Intl });
vm.runInContext(readFileSync(`${__dirname}/../assets/share-progress.js`, 'utf8'), context);
const build = context.buildShareProgress;
const now = new Date(2026, 8, 9, 12);
const attempt = (day, result, hour = 10) => ({ practicedAt: new Date(2026, 8, day, hour).toISOString(), result });
const item = (...practiceHistory) => ({ practiceHistory });

test('includes only practiced vocabulary in the image chips', () => {
  const result = build([
    { text: 'in context', ...item(attempt(9, 'used')) },
    { text: 'older', ...item(attempt(1, 'used')) },
    { text: 'new', practiceHistory: [] }
  ], now);
  assert.deepEqual(Array.from(result.words), ['in context']);
});

test('image fits its chip rows, retaining the maximum height for large lists', async () => {
  const chips = [];
  const ctx = {
    fillRect() {}, fillText() {}, beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, fill() {},
    measureText: text => ({ width: Array.from(text).length * 18 }),
    roundRect: (x, y, width, height) => chips.push({ x, y, width, height })
  };
  const canvas = { getContext: () => ctx, toBlob: callback => callback({ type: 'image/png' }) };
  const rendering = vm.createContext({ Date, Intl, document: {
    fonts: { load: async () => [] }, createElement: () => canvas
  } });
  vm.runInContext(readFileSync(`${__dirname}/../assets/share-progress.js`, 'utf8'), rendering);
  const blob = await rendering.createProgressImage({ practiced: 100, used: 60, period: 'Sep 3 - Sep 9', words: ['long '.repeat(200), ...Array(99).fill('example')] });
  assert.equal(blob.type, 'image/png');
  assert.equal(canvas.width, 1080);
  assert.ok(canvas.height <= 1080);
  assert.equal(canvas.height, chips.at(-1).y + 64 + 56);
  assert.ok(chips.length > 1 && chips.length < 100);
  for (const chip of chips) {
    assert.ok(chip.x >= 56 && chip.x + chip.width <= 1024);
    assert.ok(chip.y >= 634 && chip.y + chip.height <= 1024);
  }
  for (const [words, expectedHeight] of [
    [[], 636],
    [['one', 'two'], 754],
    [['long '.repeat(200), 'second row'], 836]
  ]) {
    chips.length = 0;
    await rendering.createProgressImage({ practiced: words.length, used: 1, period: 'Sep 3 - Sep 9', words });
    assert.equal(canvas.height, expectedHeight);
  }
});

test('counts a word as used when any attempt in the period succeeded', () => {
  const result = build([
    item(attempt(8, 'used'), attempt(9, 'needs-practice')),
    item(attempt(7, 'needs-practice'), attempt(8, 'used'), attempt(9, 'needs-practice')),
    item(attempt(8, 'needs-practice'), attempt(9, 'needs-practice')),
    item(attempt(9, 'used'), attempt(9, 'used'))
  ], now);
  assert.equal(result.practiced, 4);
  assert.equal(result.used, 3);
});

test('does not count a successful attempt outside the period', () => {
  const result = build([
    item(attempt(1, 'used'), attempt(9, 'needs-practice')),
    item(attempt(2, 'used'))
  ], now);
  assert.equal(result.practiced, 1);
  assert.equal(result.used, 0);
});

test('includes the first local midnight, excludes older and future attempts', () => {
  const result = build([
    item(attempt(3, 'used', 0)), item(attempt(2, 'used', 23)),
    item(attempt(9, 'used', 13)), item(attempt(10, 'used'))
  ], now);
  assert.equal(result.practiced, 1);
  assert.equal(result.used, 1);
  assert.match(result.period, /Sep 3, 2026 - Sep 9, 2026/);
});

test('uses the latest dated result for records created before detailed history', () => {
  const result = build([
    { text: 'legacy used', attempts: 20, successfulUses: 10, status: 'confident', lastPracticedAt: attempt(8, 'used').practicedAt },
    { text: 'legacy learning', attempts: 2, status: 'needs-practice', lastPracticedAt: attempt(7, 'needs-practice').practicedAt },
    { text: 'too old', attempts: 4, status: 'confident', lastPracticedAt: attempt(1, 'used').practicedAt },
    { attempts: 20, successfulUses: 10, status: 'confident' },
    item(null, { result: 'used', practicedAt: 'invalid' }, attempt(9, 'skipped'))
  ], now);
  // The malformed item without text is counted as activity but cannot produce a chip.
  assert.equal(result.practiced, 3);
  assert.equal(result.used, 2);
  assert.deepEqual(Array.from(result.words), ['legacy used', 'legacy learning']);
});

test('migrated Building status preserves legacy sharing evidence without inferring new success', () => {
  const result = build([
    {text:'legacy',status:'building',attempts:20,legacyLastResult:'used',practiceHistory:[],
      lastPracticedAt:attempt(8,'used').practicedAt},
    {text:'recent',status:'getting-comfortable',attempts:5,legacyLastResult:'used',
      practiceHistory:[attempt(9,'needs-practice')],lastPracticedAt:attempt(9,'needs-practice').practicedAt}
  ],now);
  assert.equal(result.practiced,2);
  assert.equal(result.used,1);
});

test('sorts practiced words by their latest attempt across the whole period', () => {
  const result = build([
    { text: 'earlier', ...item(attempt(4, 'used')) },
    { text: 'today', ...item(attempt(9, 'needs-practice')) },
    { text: 'middle', attempts: 1, status: 'confident', lastPracticedAt: attempt(7, 'used').practicedAt }
  ], now);
  assert.deepEqual(Array.from(result.words), ['today', 'middle', 'earlier']);
});

test('handles an empty history and singular message', () => {
  assert.equal(build([], now).practiced, 0);
  assert.match(build([item(attempt(9, 'used'))], now).text, /1 word or phrase aloud/);
});

test('uses calendar days across year and daylight-saving boundaries', () => {
  for (const end of [new Date(2027, 0, 2, 12), new Date(2026, 2, 31, 12)]) {
    const start = new Date(end);
    start.setDate(start.getDate() - 6);
    start.setHours(0, 0, 0, 0);
    const result = build([
      item({ practicedAt: start.toISOString(), result: 'used' }),
      item({ practicedAt: new Date(start.getTime() - 1).toISOString(), result: 'used' })
    ], end);
    assert.equal(result.practiced, 1);
  }
});
