import { GoogleGenAI } from '@google/genai';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import { HttpsError, onCall } from 'firebase-functions/v2/https';
import { logger } from 'firebase-functions';
import { deleteAccount as deleteAccountData } from './accountDeletion.js';
import { writeCatalogueEntry } from './catalogueAdmin.js';
import { loadCatalogue, resolve, toReadable } from './catalogue.js';
import { BadRequest, parseRequest, SCHEMA_VERSION } from './contract.js';
import { IMAGES, QuotaExceeded, chargeCall } from './quota.js';
import {
  IMAGE_SCHEMA_VERSION,
  NoImageReturned,
  UnknownImageKey,
  attachImageKeys,
  firestoreImageSources,
  imagenGenerator,
  parseImageRequest,
  resolveRecipeImage,
  storageImageStore,
} from './recipeImage.js';
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

/** Separate only because an image model may not be offered in the text model's region. */
const imageGenai = new GoogleGenAI({
  vertexai: true,
  project: process.env.GCLOUD_PROJECT,
  location: process.env.IMAGE_LOCATION ?? process.env.VERTEX_LOCATION ?? 'europe-west1',
});

const imageSources = firestoreImageSources(db);

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
    // A model call is slower than a database read and must still not hang. This is the budget
    // for the work; the client waits longer on purpose, so this deadline is the one that fires
    // and the caller gets an answer instead of giving up on a call still running.
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

    // Before the model, not after: the point is to not spend the call.
    try {
      await chargeCall(db, call.auth.uid, new Date());
    } catch (failure) {
      if (failure instanceof QuotaExceeded) {
        throw new HttpsError('resource-exhausted', 'too many suggestions today, try again tomorrow');
      }
      throw failure;
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
        suggestions: await withImageKeys(toWire(suggestions, catalogue, vocabulary, request.options.maxResults)),
      };
    } catch (failure) {
      logger.error('the model call failed', { requestId: request.requestId, failure: String(failure) });
      throw new HttpsError('unavailable', 'could not reach the model');
    }
  },
);

/**
 * A photograph of one suggested dish, generated once and stored. The caller sends only the
 * `imageKey` `suggestRecipes` handed it, so the prompt is built from text this project wrote.
 */
export const recipeImage = onCall(
  {
    region: process.env.FUNCTIONS_REGION ?? 'europe-southwest1',
    enforceAppCheck: true,
    timeoutSeconds: 60,
    memory: '512MiB',
    maxInstances: 5,
  },
  async (call) => {
    if (!call.auth) {
      throw new HttpsError('unauthenticated', 'sign in first');
    }
    const uid = call.auth.uid;

    let imageKey;
    try {
      ({ imageKey } = parseImageRequest(call.data));
    } catch (failure) {
      const reason = failure instanceof BadRequest ? failure.message : 'malformed request';
      throw new HttpsError('invalid-argument', reason);
    }

    try {
      const { url, generated } = await resolveRecipeImage(imageKey, {
        store: storageImageStore(getStorage().bucket()),
        sources: imageSources,
        generate: imagenGenerator(imageGenai),
        charge: () => chargeCall(db, uid, new Date(), IMAGES),
      });
      logger.info('recipe image', { generated });
      return { schemaVersion: IMAGE_SCHEMA_VERSION, imageUrl: url };
    } catch (failure) {
      if (failure instanceof QuotaExceeded) {
        throw new HttpsError('resource-exhausted', 'too many images today, try again tomorrow');
      }
      // Not `not-found`: the client reads that as "function not deployed".
      if (failure instanceof UnknownImageKey) {
        throw new HttpsError('failed-precondition', 'no dish behind this imageKey');
      }
      logger.error('the image call failed', {
        filtered: failure instanceof NoImageReturned,
        failure: String(failure),
      });
      throw new HttpsError('unavailable', 'could not generate the image');
    }
  },
);

/**
 * Callable form of `tools/seed.mjs` for one ingredient or recipe; the rules keep both collections
 * closed to clients. The `admin` claim is the gate. App Check stays off: it is orthogonal to the
 * claim, and where an admin surface lives is undecided (#165).
 */
export const writeCatalogue = onCall(
  {
    region: process.env.FUNCTIONS_REGION ?? 'europe-southwest1',
    timeoutSeconds: 30,
    maxInstances: 2,
  },
  async (call) => {
    const result = await writeCatalogueEntry(db, call);
    logger.info('catalogue entry written', result);
    return result;
  },
);

/**
 * In-app account deletion, which both stores require (#252). App Check on, like every call the app
 * makes: only the signed-in owner of an account can reach this, and only for their own uid.
 */
export const deleteAccount = onCall(
  {
    region: process.env.FUNCTIONS_REGION ?? 'europe-southwest1',
    enforceAppCheck: true,
    // Above the app's 90 s wait: kitchens are deleted one after another, each recursively.
    timeoutSeconds: 120,
    maxInstances: 5,
  },
  async (call) => {
    const result = await deleteAccountData(db, getAuth(), call);
    // Counts only: the uid is the user's identifier and stays out of the logs.
    logger.info('account deleted', result);
    return result;
  },
);

/**
 * What the model is allowed to point at. Ingredients and units to build the dish from, plus the
 * fixed `dish-types` taxonomy it must tag every suggestion with — #196 turns that tag into a
 * bundled photo, so a term it cannot resolve buys nothing either way.
 */
async function loadVocabulary(languageTags: string[]): Promise<Vocabulary> {
  const [ingredientDocs, taxonomyDocs, dishTypeDocs] = await Promise.all([
    db.collection('ingredients').get(),
    db.collection('taxonomies').where('purpose', '==', 'UNITS').get(),
    db.collection('taxonomies').doc('dish-types').collection('terms').get(),
  ]);

  const ingredients = ingredientDocs.docs.map((doc) => ({
    id: doc.id,
    name: resolve(doc.data(), languageTags) ?? doc.id,
  }));

  const dishTypes = dishTypeDocs.docs.map((doc) => ({
    id: doc.id,
    name: resolve(doc.data(), languageTags) ?? doc.id,
  }));

  const unitsTaxonomy = taxonomyDocs.docs[0];
  if (!unitsTaxonomy) return { ingredients, units: [], unitTaxonomy: null, dishTypes };

  const terms = await unitsTaxonomy.ref.collection('terms').get();
  return {
    ingredients,
    units: terms.docs.map((doc) => ({ id: doc.id, name: resolve(doc.data(), languageTags) ?? doc.id })),
    unitTaxonomy: unitsTaxonomy.id,
    dishTypes,
  };
}

/**
 * A suggestion without a key only loses its picture, so a failed write must not cost the user
 * the suggestions themselves.
 */
async function withImageKeys<T extends Parameters<typeof attachImageKeys>[1][number]>(suggestions: T[]) {
  try {
    return await attachImageKeys(imageSources, suggestions, new Date());
  } catch (failure) {
    logger.error('could not record image sources', { failure: String(failure) });
    return suggestions.map((suggestion) => ({ ...suggestion, imageKey: null }));
  }
}
