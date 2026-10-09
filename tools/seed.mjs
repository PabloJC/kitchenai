#!/usr/bin/env node
// Writes the read-only catalogues. The rules deny these collections to every client, so this
// runs through the Admin SDK with Application Default Credentials — never a key file.
//
//   gcloud auth application-default login
//   node tools/seed.mjs --project <projectId>

import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { applicationDefault, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { invalidPluralLabels, unknownPurposes } from './lib/seed-checks.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const seedDir = join(here, '..', 'firebase', 'seed');

function projectFromArgv() {
  const at = process.argv.indexOf('--project');
  const value = at === -1 ? null : process.argv[at + 1];
  if (!value) {
    // No default on purpose: the wrong project here is a silent write to somebody else's data.
    console.error('usage: node tools/seed.mjs --project <projectId>');
    process.exit(1);
  }
  return value;
}

async function read(name) {
  return JSON.parse(await readFile(join(seedDir, name), 'utf8'));
}

// Mirrors `UnitDimension`; the app ignores a dimension it does not know, so a typo here would
// silently turn a unit into one that never converts.
const DIMENSIONS = new Set(['MASS', 'VOLUME']);

function checkConversions(taxonomies) {
  for (const [id, { terms }] of Object.entries(taxonomies)) {
    for (const [termId, { conversion }] of Object.entries(terms ?? {})) {
      if (conversion === undefined) continue;
      const { dimension, factor } = conversion;
      if (!DIMENSIONS.has(dimension) || !Number.isFinite(factor) || factor <= 0) {
        console.error(`invalid conversion on term ${id}/${termId}`);
        process.exit(1);
      }
    }
  }
}

function checkPurposes(taxonomies) {
  const unknown = unknownPurposes(taxonomies);
  if (unknown.length > 0) {
    console.error(`unknown purpose on taxonomy ${unknown.join(', ')}`);
    process.exit(1);
  }
}

function checkPluralLabels(taxonomies) {
  const invalid = invalidPluralLabels(taxonomies);
  if (invalid.length > 0) {
    console.error(`invalid pluralLabels on term ${invalid.join(', ')}`);
    process.exit(1);
  }
}

// `set` by document id, never `add`: running this twice has to leave the same database.
async function seedTaxonomies(db, taxonomies) {
  checkConversions(taxonomies);
  checkPurposes(taxonomies);
  checkPluralLabels(taxonomies);
  let terms = 0;
  for (const [id, { terms: children, ...taxonomy }] of Object.entries(taxonomies)) {
    await db.collection('taxonomies').doc(id).set(taxonomy);
    for (const [termId, term] of Object.entries(children ?? {})) {
      await db.collection('taxonomies').doc(id).collection('terms').doc(termId).set(term);
      terms += 1;
    }
  }
  return { taxonomies: Object.keys(taxonomies).length, terms };
}

async function seedIngredients(db, ingredients) {
  for (const [id, ingredient] of Object.entries(ingredients)) {
    await db.collection('ingredients').doc(id).set(ingredient);
  }
  return Object.keys(ingredients).length;
}

const projectId = projectFromArgv();
initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore();

const counts = await seedTaxonomies(db, await read('taxonomies.json'));
const ingredients = await seedIngredients(db, await read('ingredients.json'));

// Counts only. What is in these documents is the user's vocabulary, not log material.
console.log(`taxonomies: ${counts.taxonomies}`);
console.log(`terms:      ${counts.terms}`);
console.log(`ingredients: ${ingredients}`);
