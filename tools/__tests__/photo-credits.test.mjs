import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { test } from 'node:test';

// The app reads photo_credits.json; NOTICE.md is the human-readable copy. Both must name every bundled photograph.
const resources = new URL('../../composeApp/src/commonMain/composeResources/', import.meta.url);
const credits = JSON.parse(readFileSync(new URL('files/photo_credits.json', resources), 'utf8'));
const notice = readFileSync(new URL('drawable/NOTICE.md', resources), 'utf8');
const photographs = readdirSync(new URL('drawable/', resources))
  .filter((name) => /^dish_.*\.jpg$/.test(name))
  .map((name) => name.replace(/\.jpg$/, ''));

test('every bundled dish photograph has exactly one credit', () => {
  const credited = credits.map((credit) => credit.image);
  assert.deepEqual([...credited].sort(), [...new Set(credited)].sort(), 'an image is credited twice');
  assert.deepEqual([...credited].sort(), [...photographs].sort());
});

test('every credit names its title, author, licence and an https source', () => {
  for (const credit of credits) {
    for (const field of ['title', 'author', 'licence']) {
      assert.ok(credit[field]?.trim(), `${credit.image} has no ${field}`);
    }
    assert.match(credit.source, /^https:\/\//, `${credit.image} has no https source`);
    if (credit.licenceUrl !== undefined) assert.match(credit.licenceUrl, /^https:\/\//);
  }
});

test('an attribution licence always carries a link to its deed', () => {
  for (const credit of credits.filter((entry) => /^CC BY/.test(entry.licence))) {
    assert.ok(credit.licenceUrl, `${credit.image} is ${credit.licence} without a licence link`);
  }
});

test('NOTICE.md says the same as photo_credits.json', () => {
  for (const credit of credits) {
    const line = `- \`${credit.image}.jpg\` — **${credit.licence}**, by ${credit.author}. Source: ${credit.source}`;
    assert.ok(notice.includes(line), `NOTICE.md does not match the credit for ${credit.image}`);
  }
  const listed = notice.match(/^- `dish_[^`]+\.jpg`/gm) ?? [];
  assert.equal(listed.length, credits.length);
});
