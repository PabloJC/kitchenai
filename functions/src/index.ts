import { GoogleGenAI } from '@google/genai';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { loadCatalogue, resolve, toReadable } from './catalogue.js';
import { BadRequest, parseRequest, SCHEMA_VERSION } from './contract.js';
import { AGENT_ID, MODEL_ID, askModel, toWire, type Vocabulary } from './suggest.js';

initializeApp();
const db = getFirestore();

/**
 * Vertex AI in this same project, so there is no key anywhere: the function authenticates with
 * its own service account. That is the whole reason the model call lives on a server.
 *
 * The default keeps inference inside the EU. A pantry is a list of what someone eats and when
 * it goes off, and it is the one thing this call sends anywhere — so where it is processed is a
 * decision, not a default worth inheriting.
 */
const genai = new GoogleGenAI({
  vertexai: true,
  project: process.env.GCLOUD_PROJECT,
  location: process.env.VERTEX_LOCATION ?? 'europe-west1',
});

/**
 * The one endpoint the app calls.
 *
 * `enforceAppCheck` is the point of the design, not a hardening step: without it anyone holding
 * the URL can spend this project's model budget. A debug build needs a debug token registered
 * in the console, or it will be refused here — which is the intended behaviour.
 */
export const suggestRecipes = onCall(
  {
    // Where Firestore already lives: this function reads the whole catalogue on every call, so
    // the round trips it does not make are worth more than anything else here.
    region: process.env.FUNCTIONS_REGION ?? 'europe-southwest1',
    enforceAppCheck: true,
    // A model call is slower than a database read and must still not hang. The client gives up
    // at 30s, so anything past that is work nobody is waiting for.
    timeoutSeconds: 60,
    memory: '512MiB',
    // Ceiling on concurrent spend. A runaway client cannot turn into an unbounded bill.
    maxInstances: 10,
  },
  async (call) => {
    if (!call.auth) {
      throw new HttpsError('unauthenticated', 'sign in first');
    }

    let request;
    try {
      request = parseRequest(call.data);
    } catch (failure) {
      // The only place a caller's own text is echoed, and it is our own message, never theirs.
      const reason = failure instanceof BadRequest ? failure.message : 'malformed request';
      throw new HttpsError('invalid-argument', reason);
    }

    const catalogue = await loadCatalogue(db, request.languageTags);
    const readable = toReadable(request, catalogue);
    const vocabulary = await loadVocabulary(request.languageTags);

    try {
      const suggestions = await askModel(genai, readable, vocabulary);
      // No pantry content and no user id: this log is read by whoever is on call, and a food
      // list is the user's own content.
      logger.info('suggested', {
        requestId: request.requestId,
        returned: suggestions.length,
        pantrySize: request.pantry.length,
      });
      return {
        schemaVersion: SCHEMA_VERSION,
        agentId: AGENT_ID,
        modelId: MODEL_ID,
        suggestions: toWire(suggestions, catalogue, vocabulary.unitTaxonomy, request.options.maxResults),
      };
    } catch (failure) {
      logger.error('the model call failed', { requestId: request.requestId, failure: String(failure) });
      throw new HttpsError('unavailable', 'could not reach the model');
    }
  },
);

/**
 * What the model is allowed to point at. Ingredients and units only: the app resolves those
 * against the pantry, and a tag it cannot resolve buys nothing.
 */
async function loadVocabulary(languageTags: string[]): Promise<Vocabulary> {
  const [ingredientDocs, taxonomyDocs] = await Promise.all([
    db.collection('ingredients').get(),
    db.collection('taxonomies').where('purpose', '==', 'UNITS').get(),
  ]);

  const ingredients = ingredientDocs.docs.map((doc) => ({
    id: doc.id,
    name: resolve(doc.data(), languageTags) ?? doc.id,
  }));

  const unitsTaxonomy = taxonomyDocs.docs[0];
  if (!unitsTaxonomy) return { ingredients, units: [], unitTaxonomy: null };

  const terms = await unitsTaxonomy.ref.collection('terms').get();
  return {
    ingredients,
    units: terms.docs.map((doc) => ({ id: doc.id, name: resolve(doc.data(), languageTags) ?? doc.id })),
    unitTaxonomy: unitsTaxonomy.id,
  };
}
