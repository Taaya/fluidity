const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createPromptHistory } = require('../../../prompt-history.js');

function storage() {
  const data = new Map();
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
}
const question = { id: 'shown-prompt', word: 'cracked' };
const options = (store, fetcher) => ({ storage: store, fetcher, newId: () => 'learner-one' });

test('remembers a shown prompt across reload and replays failed writes before another session', async () => {
  const store = storage();
  const first = createPromptHistory(options(store, async () => { throw new Error('offline'); }));
  await assert.rejects(first.record(question, 'Intermediate'));
  let acknowledged = false;
  const reloaded = createPromptHistory(options(store, async (url, request) => {
    assert.equal(url, '/api/dictionary-items/tasks');
    assert.equal(request.headers['X-Learner-Id'], 'learner-one');
    assert.equal(request.keepalive, true);
    assert.deepEqual(JSON.parse(request.body), { word: 'cracked', taskId: 'shown-prompt', level: 'Intermediate' });
    acknowledged = true;
    return { ok: true };
  }));
  assert.equal(reloaded.learnerId, first.learnerId);
  await reloaded.flush();
  assert.ok(acknowledged);
  assert.deepEqual(JSON.parse(store.getItem('wordswith:pending-prompt-exposures:v1')), []);
});

test('an HTTP failure does not silently discard history', async () => {
  const store = storage();
  const history = createPromptHistory(options(store, async () => ({ ok: false })));
  await assert.rejects(history.record(question, 'Advanced'), /Could not save/);
  assert.equal(JSON.parse(store.getItem('wordswith:pending-prompt-exposures:v1')).length, 1);
  await assert.rejects(history.flush());
});

test('serializes writes and includes a new exposure while an earlier write is in flight', async () => {
  const store = storage();
  let release;
  const firstResponse = new Promise(resolve => { release = resolve; });
  const ids = [];
  const history = createPromptHistory(options(store, async (_, request) => {
    ids.push(JSON.parse(request.body).taskId);
    if (ids.length === 1) await firstResponse;
    return { ok: true };
  }));
  const first = history.record(question, 'Intermediate');
  const second = history.record({ id: 'next-prompt', word: 'among' }, 'Intermediate');
  const duplicate = history.record(question, 'Intermediate');
  release();
  await Promise.all([first, second, duplicate]);
  assert.deepEqual(ids, ['shown-prompt', 'next-prompt']);
  assert.deepEqual(JSON.parse(store.getItem('wordswith:pending-prompt-exposures:v1')), []);
});

test('does not remove a different tab’s newly queued exposure when acknowledging an older one', async () => {
  const store = storage();
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let calls = 0;
  const history = createPromptHistory(options(store, async () => {
    if (++calls === 1) await gate;
    return { ok: true };
  }));
  const recorded = history.record(question, 'Intermediate');
  const queueKey = 'wordswith:pending-prompt-exposures:v1';
  const queue = JSON.parse(store.getItem(queueKey));
  queue.push({ word: 'Danube', taskId: 'another-tab', level: 'Intermediate' });
  store.setItem(queueKey, JSON.stringify(queue));
  release();
  await recorded;
  assert.equal(calls, 2);
  assert.deepEqual(JSON.parse(store.getItem(queueKey)), []);
});
