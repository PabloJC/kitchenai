// Calls the deployed `suggestRecipes` the way the app will, so the whole chain can be proved
// before any screen exists to prove it with: anonymous auth, an App Check token, the callable
// protocol, the daily quota and the response contract.
//
//   node tools/smoke-agent.mjs --debug-token <token-from-logcat> --cert-sha1 <debug-keystore-sha1>
//
// The fingerprint comes from the same keystore that signs the debug build:
//   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
//     -storepass android | grep SHA1
//
// The debug token must already be registered in the console, or App Check refuses the call —
// which is itself a result worth seeing.

import { readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    'debug-token': { type: 'string' },
    'cert-sha1': { type: 'string' },
    config: { type: 'string', default: 'androidApp/google-services.json' },
    region: { type: 'string', default: 'europe-southwest1' },
    repeat: { type: 'string', default: '1' },
    raw: { type: 'boolean', default: false },
  },
});

if (!values['debug-token']) {
  console.error('--debug-token is required. It is printed by a debug build on first launch.');
  process.exit(1);
}

// Read rather than hardcoded: this file is gitignored, and its values are the app's own.
const config = JSON.parse(readFileSync(values.config, 'utf8'));
const projectId = config.project_info.project_id;
const client = config.client[0];
const apiKey = client.api_key[0].current_key;
const appId = client.client_info.mobilesdk_app_id;
const packageName = client.client_info.android_client_info.package_name;
const url = `https://${values.region}-${projectId}.cloudfunctions.net/suggestRecipes`;

/**
 * The API key in `google-services.json` is restricted to this Android app, so a call from a
 * laptop is refused unless it identifies itself the way the Android SDK does. These are the
 * headers that SDK sends; the fingerprint is the local debug keystore's, which is why this
 * script only works on the machine that builds the debug app.
 */
function androidHeaders() {
  const cert = values['cert-sha1'];
  return cert ? { 'X-Android-Package': packageName, 'X-Android-Cert': cert.replaceAll(':', '').toUpperCase() } : {};
}

async function post(endpoint, body, headers = {}) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 400) };
  }
  return { status: response.status, body: parsed };
}

/** A fresh anonymous account, exactly what the app's session gate creates. */
async function signIn() {
  const { status, body } = await post(
    `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey}`,
    { returnSecureToken: true },
    androidHeaders(),
  );
  if (status !== 200) throw new Error(`anonymous sign-in failed: ${JSON.stringify(body).slice(0, 200)}`);
  return body.idToken;
}

/** The debug secret is not the token: it is traded for a real one, and only if registered. */
async function appCheckToken() {
  const { status, body } = await post(
    `https://firebaseappcheck.googleapis.com/v1/projects/${projectId}/apps/${appId}:exchangeDebugToken?key=${apiKey}`,
    { debugToken: values['debug-token'] },
    androidHeaders(),
  );
  if (status !== 200) {
    throw new Error(
      `App Check refused the debug token (${status}). Register it under App Check -> Apps -> Manage debug tokens.\n` +
        JSON.stringify(body).slice(0, 300),
    );
  }
  return body.token;
}

/** Two holdings, one of them about to spoil, so the answer can be checked for weighting it. */
const request = {
  schemaVersion: 1,
  requestId: crypto.randomUUID(),
  capability: 'SUGGEST_FROM_PANTRY',
  languageTags: ['es'],
  servings: 2,
  options: { maxResults: 3, maxMinutes: 30, useOnlyPantry: false },
  constraints: [{ taxonomy: 'allergens', term: 'nuts', strength: 'EXCLUDE' }],
  preferences: [],
  avoidedIngredients: [],
  pantry: [
    { ingredientId: 'egg', amount: 4, unitTaxonomy: 'units', unitTerm: 'piece', expiringSoon: true },
    { ingredientId: 'rice', amount: 500, unitTaxonomy: 'units', unitTerm: 'gram', expiringSoon: false },
  ],
};

const idToken = await signIn();
console.log('anonymous sign-in: ok');
const check = await appCheckToken();
console.log('app check token:   ok');

for (let attempt = 1; attempt <= Number(values.repeat); attempt += 1) {
  const started = Date.now();
  const { status, body } = await post(
    url,
    { data: { ...request, requestId: crypto.randomUUID() } },
    { Authorization: `Bearer ${idToken}`, 'X-Firebase-AppCheck': check },
  );
  const took = Date.now() - started;

  if (status !== 200) {
    console.log(`call ${attempt}: HTTP ${status} in ${took}ms`);
    console.log(JSON.stringify(body).slice(0, 400));
    continue;
  }

  const result = body.result;
  if (values.raw) console.log(JSON.stringify(result, null, 1));
  console.log(`call ${attempt}: ok in ${took}ms — agent=${result.agentId} model=${result.modelId}`);
  for (const suggestion of result.suggestions) {
    const pointers = suggestion.ingredients.filter((line) => line.ingredientId).length;
    const text = suggestion.ingredients.length - pointers;
    console.log(
      `  - ${suggestion.title} (${suggestion.totalMinutes} min, ${suggestion.servings} servings)\n` +
        `    ${suggestion.ingredients.length} lines: ${pointers} catalogue, ${text} free text; ${suggestion.steps.length} steps`,
    );
  }
}
