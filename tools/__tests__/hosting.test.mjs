import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { CONTACT_PLACEHOLDER, findPlaceholders } from '../lib/hosting-checks.mjs';

const dir = new URL('../../hosting/', import.meta.url);
const names = readdirSync(dir).filter((name) => name.endsWith('.html'));
const read = (name) => readFileSync(new URL(name, dir), 'utf8');

test('a page still holding the contact placeholder is reported, a finished one is not', () => {
  const problems = findPlaceholders({ 'a.html': `<a>${CONTACT_PLACEHOLDER}</a>`, 'b.html': '<a>owner@example.org</a>' });
  assert.deepEqual(problems, [`a.html: still has ${CONTACT_PLACEHOLDER}`]);
});

test('every policy and deletion page exists in English and Spanish and links to its translation', () => {
  for (const page of ['privacy', 'delete-account']) {
    assert.ok(names.includes(`${page}.html`) && names.includes(`${page}-es.html`), `${page} is missing a language`);
    assert.match(read(`${page}.html`), new RegExp(`href="/${page}-es"`));
    assert.match(read(`${page}-es.html`), new RegExp(`href="/${page}"`));
  }
});

test('every internal link points at a page that exists', () => {
  const pages = new Set(names.map((name) => '/' + name.replace(/\.html$/, '')).concat('/'));
  for (const name of names) {
    for (const [, href] of read(name).matchAll(/href="(\/[^"]*)"/g)) {
      if (href.endsWith('.css')) continue;
      assert.ok(pages.has(href), `${name} links to ${href}, which is not a page`);
    }
  }
});
