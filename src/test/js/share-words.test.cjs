const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createShareLink, readSharedWords } = require('../../../share-words.js');

const location = { origin: 'https://wordswith.example', pathname: '/' };

test('round-trips the parsed list in a versioned fragment, preserving case and phrases', () => {
  const words = ['steeple', 'Danube', 'to bridge the gap', 'cracked'];
  const link = createShareLink(words, location);
  assert.equal(
    link,
    'https://wordswith.example/#share=v1.steeple%0ADanube%0Ato%20bridge%20the%20gap%0Acracked'
  );
  assert.deepEqual(readSharedWords(new URL(link).hash), { status: 'ok', words });
});

test('keeps the current path so the link opens the same page', () => {
  const link = createShareLink(['among'], { origin: 'https://wordswith.example', pathname: '/add-words.html' });
  assert.equal(link, 'https://wordswith.example/add-words.html#share=v1.among');
});

test('encodes characters that would otherwise change the link', () => {
  const words = ['#tag', 'a+b', '50%'];
  assert.deepEqual(readSharedWords(new URL(createShareLink(words, location)).hash), { status: 'ok', words });
});

test('ignores addresses without a share fragment', () => {
  assert.deepEqual(readSharedWords(''), { status: 'none', words: [] });
  assert.deepEqual(readSharedWords('#'), { status: 'none', words: [] });
  assert.deepEqual(readSharedWords('#section'), { status: 'none', words: [] });
});

test('rejects unknown versions, broken encoding and empty payloads', () => {
  assert.equal(readSharedWords('#share=v2.steeple').status, 'invalid');
  assert.equal(readSharedWords('#share=v1.%E0%A4%A').status, 'invalid');
  assert.equal(readSharedWords('#share=v1.%0A%0A').status, 'invalid');
  assert.equal(readSharedWords('#share=v1').status, 'invalid');
  assert.equal(readSharedWords('#share=').status, 'invalid');
});
