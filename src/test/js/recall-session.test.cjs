const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function app(saved = new Map(), options = {}) {
  const elements = new Map();
  function element() {
    const classes = new Set(), attrs = new Map();
    return { textContent: '', hidden: false, children: [], disabled: false,
      classList: { add: x => classes.add(x), remove: x => classes.delete(x), contains: x => classes.has(x),
        toggle: (x, on) => on ? classes.add(x) : classes.delete(x) },
      style: {}, dataset: {}, addEventListener() {}, focus() {},
      open: false, showModal() { this.open = true; }, close() { this.open = false; },
      setAttribute: (k,v) => attrs.set(k,v), getAttribute: k => attrs.get(k),
      querySelector: () => element(), replaceChildren() { this.children = []; },
      appendChild(child) { this.children.push(child); },
      append(...children) { this.children.push(...children); }
    };
  }
  const get = id => { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); };
  const exposures = [], timers = [];
  const context = vm.createContext({
    document: { getElementById: get, createElement: element, addEventListener() {} },
    window: { localStorage: { getItem: k => saved.get(k) ?? null, setItem: (k,v) => saved.set(k,v), removeItem: k => saved.delete(k) },
      location: { reload() {} },
      fetch: async () => {}, scrollTo() {}, setTimeout: fn => timers.push(fn) },
    crypto: { randomUUID: () => 'test-id' },
    fetch: options.fetch,
    createPromptHistory: () => ({ learnerId: 'test', flush: options.flush || (async () => {}),
      record: q => { exposures.push(q.id); return Promise.resolve(); } })
  });
  // Boot the real application functions without rendering the unrelated vocabulary screen.
  const source = fs.readFileSync(path.join(__dirname, '../../../script.js'), 'utf8')
    .replace(/renderImportControls\(\);\s*renderHome\(\);\s*$/, '');
  vm.runInContext(source, context);
  const run = code => vm.runInContext(code, context);
  const flush = () => { while (timers.length) timers.shift()(); };
  run(`state.vocabularyItems = ['among','cracked'].map((text, i) => migrateVocabularyItem({id:String(i),text}));
    prepareSession(state.vocabularyItems, {sessionId:'session', questions: [
      {id:'a',word:'among',text:'Divide the work within the group.',starter:'I would...'},
      {id:'b',word:'among',text:'Describe your place within the crowd.',starter:'I was...'},
      {id:'c',word:'cracked',text:'Describe the damage to the bowl.',starter:'It...'},
      {id:'d',word:'cracked',text:'Describe the broken screen.',starter:'It...'}
    ]}); renderSessionPreview();`);
  return { run, get, saved, flush, exposures };
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

const nextTurn = () => new Promise(resolve => setImmediate(resolve));

function preparingApp(options = {}) {
  const requests = [];
  const a = app(new Map(), { ...options, fetch: (url, init) => {
    const pending = deferred();
    requests.push({ url, ...init, ...pending });
    return pending.promise;
  } });
  a.run('showHome()');
  return { ...a, requests };
}

function resolveSession(request, words = JSON.parse(request.body).words) {
  request.resolve({ ok: true, json: async () => ({ sessionId: 'prepared', words,
    questions: words.map((word, i) => ({ id: 'q' + i, word, text: 'Question for ' + word })) }) });
}

test('Practice shows fixed selection immediately, before history flush or generation; Ready waits once', async () => {
  const history = deferred();
  const a = preparingApp({ flush: () => history.promise });
  a.run(`state.vocabularyItems = ['among','cracked','three','four','five','six']
    .map((text,i) => migrateVocabularyItem({id:String(i),text}));`);
  const done = a.run('startSession()');
  assert.equal(a.get('preview-view').classList.contains('is-active'), true);
  assert.equal(a.get('preview-chips').children.length, 5);
  assert.equal(a.requests.length, 0);
  const preview = a.get('preview-chips').children.map(c => c.textContent);
  assert.equal(a.get('ready-button').textContent, 'Ready');
  a.run('beginPractice(); beginPractice()');
  assert.equal(a.get('ready-button').textContent, 'Preparing…');
  assert.equal(a.get('ready-button').disabled, true);
  assert.equal(a.exposures.length, 0);
  history.resolve();
  await nextTurn();
  assert.equal(a.requests.length, 1);
  assert.deepEqual(JSON.parse(a.requests[0].body).words.sort(), [...preview].sort());
  await a.run('startSession()'); // Double click must not issue another request.
  resolveSession(a.requests[0]);
  await done;
  a.flush();
  assert.equal(a.get('training-view').classList.contains('is-active'), true);
  assert.equal(a.requests.length, 1);
  assert.equal(a.exposures.length, 1);
});

test('finished preparation keeps preview stable until Ready, with no prompt exposure', async () => {
  const a = preparingApp();
  const done = a.run('startSession()');
  const preview = a.get('preview-chips').children.map(c => c.textContent);
  await nextTurn();
  resolveSession(a.requests[0]);
  await done;
  a.flush();
  assert.deepEqual(a.get('preview-chips').children.map(c => c.textContent), preview);
  assert.equal(a.get('preview-view').classList.contains('is-active'), true);
  assert.equal(a.exposures.length, 0);
  a.run('beginPractice()'); a.flush();
  assert.equal(a.exposures.length, 1);
});

test('leaving before history flush prevents generation', async () => {
  const history = deferred();
  const a = preparingApp({ flush: () => history.promise });
  const done = a.run('startSession()');
  a.run('beginPractice(); showHome()');
  history.resolve();
  await done;
  assert.equal(a.requests.length, 0);
  assert.equal(a.run('state.loading'), false);
});

test('late success or failure after Back cannot change a newer preparation', async () => {
  for (const fails of [false, true]) {
    const a = preparingApp();
    const old = a.run('startSession()');
    await nextTurn();
    a.run('beginPractice(); showHome()');
    assert.equal(a.get('home-view').classList.contains('is-active'), true);
    const current = a.run('startSession()');
    await nextTurn();
    if (fails) a.requests[0].reject(new Error('Old failure'));
    else resolveSession(a.requests[0]);
    await old;
    assert.equal(a.run('state.loading'), true);
    assert.equal(a.get('preview-error').hidden, true);
    assert.equal(a.get('preview-view').classList.contains('is-active'), true);
    resolveSession(a.requests[1]);
    await current;
    assert.equal(a.get('preview-view').classList.contains('is-active'), true);
    assert.equal(a.exposures.length, 0);
  }
});

test('partial preparation and network failure offer retry for exactly the previewed words', async () => {
  for (const partial of [false, true]) {
    const a = preparingApp();
    const done = a.run('startSession()');
    a.run('beginPractice()');
    await nextTurn();
    if (partial) resolveSession(a.requests[0], ['among']);
    else a.requests[0].reject(new Error('Connection lost'));
    await done;
    assert.equal(a.get('preview-error').hidden, false);
    assert.equal(a.get('ready-button').textContent, 'Try again');
    assert.equal(a.get('ready-button').disabled, false);
    assert.equal(a.get('preview-chips').children.length, 2);
    assert.equal(a.exposures.length, 0);
    const retry = a.run('beginPractice()');
    await nextTurn();
    assert.equal(a.requests[1].body, a.requests[0].body);
    resolveSession(a.requests[1]);
    await retry;
    a.flush();
    assert.equal(a.get('training-view').classList.contains('is-active'), true);
    assert.equal(a.exposures.length, 1);
  }
});

test('preview contains exactly the selected words; no prompt is shown before Ready', () => {
  const a = app();
  assert.deepEqual(a.get('preview-chips').children.map(c => c.textContent).sort(), ['among','cracked']);
  assert.equal(a.get('preview-view').classList.contains('is-active'), true);
  assert.equal(a.run('state.currentQuestion'), null);
  assert.equal(a.exposures.length, 0);
  a.run('beginPractice(); beginPractice();'); a.flush();
  assert.equal(a.exposures.length, 1);
  assert.equal(a.get('question-word').hidden, true);
  assert.equal(a.get('question-word').textContent, '');
  assert.equal(a.get('word-spoiler').getAttribute('aria-expanded'), 'false');
});

test('Used without revealing persists false; the next revealed answer persists true across reload', () => {
  const a = app();
  a.run('beginPractice()'); a.flush();
  const first = a.run('state.sessionItems[0].id');
  a.run(`assessCurrentWord('used')`); a.flush();
  assert.equal(a.saved.has('wordswith:vocabulary-preview'), false);
  assert.equal(a.run(`state.pendingResults.get('${first}').practiceHistory[0].wordRevealed`), false);
  a.run('revealWord()');
  assert.equal(a.get('question-word').hidden, false);
  assert.equal(a.get('word-spoiler').hidden, true);
  assert.equal(a.get('question-word').textContent, a.run('state.sessionItems[1].text'));
  // Avoid only the unrelated summary rendering; assessment and persistence remain real.
  a.run(`renderSummary = () => {}; assessCurrentWord('used')`);
  const histories = a.run('loadVocabularyItems().map(i => i.practiceHistory)');
  assert.equal(histories.flat().length, 2);
  assert.deepEqual(Array.from(histories.flat(), h => h.wordRevealed).sort(), [false, true]);
  assert.ok(histories.flat().every(h => h.sessionId === 'session' && h.taskId && h.practicedAt));
});

test('reveal stays recorded after changing prompt, but resets for the next word', () => {
  const a = app(); a.run('beginPractice()'); a.flush();
  a.run('revealWord(); revealWord(); tryAnotherPrompt()'); a.flush();
  assert.equal(a.get('question-word').hidden, true);
  a.run(`assessCurrentWord('used')`); a.flush();
  assert.equal(a.run('[...state.pendingResults.values()][0].practiceHistory[0].wordRevealed'), true);
  assert.equal(a.run('state.revealedItemIds.has(state.sessionItems[state.currentIndex].id)'), false);
  assert.equal(a.get('question-word').textContent, '');
});

test('legacy attempts are not backfilled as independent recall', () => {
  const a = app();
  assert.equal(a.run(`migrateVocabularyItem({text:'among',attempts:4}).practiceHistory.length`), 0);
});

test('clearing starts a fresh profile and restoration swaps backups without losing new words', () => {
  const a = app();
  a.run('saveVocabularyItems()');
  a.saved.set('wordswith:learner-id', 'old-profile');
  const original = a.saved.get('wordswith:vocabulary-preview');
  a.run('clearVocabulary()');
  assert.equal(a.saved.has('wordswith:vocabulary-preview'), false);
  assert.equal(a.saved.has('wordswith:learner-id'), false);
  a.saved.set('wordswith:vocabulary-preview', '[{"text":"new word"}]');
  a.saved.set('wordswith:learner-id', 'new-profile');
  a.run('restoreVocabulary()');
  assert.equal(a.saved.get('wordswith:vocabulary-preview'), original);
  assert.equal(a.saved.get('wordswith:learner-id'), 'old-profile');
  a.run('restoreVocabulary()');
  assert.equal(a.saved.get('wordswith:vocabulary-preview'), '[{"text":"new word"}]');
  assert.equal(a.saved.get('wordswith:learner-id'), 'new-profile');
});

test('default session reserves two new and three practiced words after bulk import', () => {
  const a = app();
  a.run(`state.vocabularyItems = [
    {id:'recent',status:'building',addedAt:'2026-01-01',lastPracticedAt:'2026-09-12'},
    ...Array.from({length:30}, (_,i) => ({id:'import'+i,status:'new',addedAt:'2026-09-11'})),
    {id:'old-gc',status:'getting-comfortable',addedAt:'2026-01-01',lastPracticedAt:'2026-08-01'},
    {id:'old-new',status:'new',addedAt:'2026-08-02'},
    {id:'old-building',status:'building',addedAt:'2026-01-01',lastPracticedAt:'2026-08-03'}
  ];`);
  assert.deepEqual(Array.from(a.run('selectSessionItems().map(i=>i.id)')),
    ['old-new','import0','old-gc','old-building','recent']);
  assert.equal(a.run('selectSessionItems(null,100).length'), 34);
  assert.deepEqual(Array.from(a.run('selectSessionItems(null,100).slice(0,5).map(i=>i.id)')),
    ['old-new','import0','old-gc','old-building','recent']);
  assert.equal(a.run('new Set(selectSessionItems(null,100).map(i=>i.id)).size'), 34);
  assert.deepEqual(Array.from(a.run("selectSessionItems(['recent','old-gc']).map(i=>i.id)")),
    ['old-gc','recent']);
});

test('missing new or practiced places are filled from the other group without duplicates', () => {
  const a = app();
  for (const [newWords, practicedWords, expectedNew, expectedTotal] of [
    [7,0,5,5], [0,7,0,5], [1,7,1,5], [7,1,4,5], [1,1,1,2], [0,0,0,0]
  ]) {
    a.run(`state.vocabularyItems = [
      ...Array.from({length:${newWords}},(_,i)=>({id:'n'+i,status:'new',addedAt:'2026-09-10'})),
      ...Array.from({length:${practicedWords}},(_,i)=>({id:'p'+i,status:'building',lastPracticedAt:'2026-09-01'}))
    ];`);
    assert.equal(a.run('selectSessionItems().length'), expectedTotal);
    assert.equal(a.run("selectSessionItems().filter(i=>i.status==='new').length"), expectedNew);
    assert.equal(a.run('new Set(selectSessionItems().map(i=>i.id)).size'), expectedTotal);
  }
});

test('Practice again reuses vocabulary when no other words exist', () => {
  const a = app();
  assert.deepEqual(Array.from(a.run(`selectSessionItems().map(i=>i.id)`)).sort(), ['0','1']);
});

test('recent success, protected single difficulty, demotion after a break, and recovery', () => {
  const a = app();
  const status = sequence => a.run(`progressStatus({attempts:${sequence.length}, practiceHistory:
    ${JSON.stringify([...sequence].map(value => ({result: value === 'U' ? 'used' : 'needs-practice',
      practicedAt: '2026-01-01T00:00:00Z'})))}})`);
  for (const [sequence, expected] of [
    ['', 'new'], ['U','building'], ['S','building'], ['UU','building'],
    ['UUU','getting-comfortable'], ['USUU','getting-comfortable'],
    ['UUUS','getting-comfortable'], ['UUUSUS','getting-comfortable'],
    ['UUUSS','building'], ['UUUSSSS','building'],
    ['UUUSSSSUU','building'], ['UUUSSSSUUU','getting-comfortable']
  ]) assert.equal(status(sequence), expected, sequence);
});

test('migration replays history instead of trusting old status or lifetime totals', () => {
  const a = app();
  assert.equal(a.run(`migrateVocabularyItem({text:'among',status:'confident',attempts:20,successfulUses:19}).status`), 'building');
  assert.equal(a.run(`migrateVocabularyItem({text:'among',status:'confident',attempts:20,successfulUses:19}).successfulUses`), 19);
  assert.equal(a.run(`migrateVocabularyItem({text:'among',status:'confident',attempts:20}).legacyLastResult`), 'used');
  a.run(`const legacy = migrateVocabularyItem({text:'among',status:'needs-practice',attempts:4,
    practiceHistory:['used','used','used','needs-practice'].map(result=>({result}))});`);
  assert.equal(a.run('legacy.status'), 'getting-comfortable');
  assert.equal(a.run('migrateVocabularyItem(JSON.parse(JSON.stringify(legacy))).status'), 'getting-comfortable');
});

test('Another prompt leaves progress unchanged; assessed result persists new status after completion', () => {
  const a = app();
  a.run(`state.vocabularyItems.forEach(item => {
    item.attempts=2; item.practiceHistory=[{result:'used'},{result:'used'}]; item.status='building';
  }); beginPractice();`); a.flush();
  a.run('tryAnotherPrompt()'); a.flush();
  assert.equal(a.run('state.pendingResults.size'), 0);
  assert.equal(a.run('state.vocabularyItems[0].practiceHistory.length'), 2);
  a.run("assessCurrentWord('used')"); a.flush();
  assert.equal(a.run('[...state.pendingResults.values()][0].status'), 'getting-comfortable');
  a.run("renderSummary = () => {}; assessCurrentWord('needs-practice')");
  assert.deepEqual(Array.from(a.run('loadVocabularyItems().map(i=>i.status)')).sort(),
    ['building','getting-comfortable']);
  a.run('renderHome()');
  assert.equal(a.get('new-count').textContent, '0');
  assert.equal(a.get('building-count').textContent, '1');
  assert.equal(a.get('comfortable-count').textContent, '1');
});

test('leaving requires confirmation; cancelling retains pending answers, leaving discards them', () => {
  const a = app(); a.run('saveVocabularyItems(); renderHome = () => {}; beginPractice()'); a.flush();
  const before = a.saved.get('wordswith:vocabulary-preview');
  a.run("assessCurrentWord('used')"); a.flush();
  a.run('requestLeaveSession()');
  assert.equal(a.get('leave-dialog').open, true);
  assert.equal(a.run('state.running'), true);
  a.get('leave-dialog').close();
  assert.equal(a.run('state.pendingResults.size'), 1);
  a.run('requestLeaveSession(); leaveDialog.close(); leaveSession()');
  assert.equal(a.run('state.pendingResults.size'), 0);
  assert.equal(a.run('state.running'), false);
  assert.equal(a.saved.get('wordswith:vocabulary-preview'), before);
  assert.ok(a.exposures.length > 0);
});
