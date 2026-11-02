import { createHash, randomUUID } from 'node:crypto';
import { Modality, type GoogleGenAI } from '@google/genai';
import type { Firestore } from 'firebase-admin/firestore';
import type { getStorage } from 'firebase-admin/storage';
import { BadRequest, asObject } from './contract.ts';

/** Named here and nowhere else, like `MODEL_ID`: the client only ever sees a URL. */
// Not Imagen: Vertex no longer serves those models to this project, in any region.
export const IMAGE_MODEL_ID = 'gemini-2.5-flash-image';

export const IMAGE_SCHEMA_VERSION = 1;

/** Server-only; the rules keep it closed. A key only resolves to text this function wrote. */
const SOURCES = 'recipeImageSources';

/** Long enough to open a suggestion days later; a generated image outlives its source anyway. */
const SOURCE_KEEP_DAYS = 30;

const MAX_TITLE = 200;
const MAX_SUMMARY = 400;

/** What a picture is drawn from: our own model's words, never the caller's. */
export interface ImageSource {
  title: string;
  summary: string | null;
  dishType: string | null;
  /** The model refused this dish once; asking again would only spend another allowance. */
  withheld?: boolean;
}

export class UnknownImageKey extends Error {}

/** The model answered with no picture, usually because a safety filter withheld it. */
export class NoImageReturned extends Error {}

export type GenerateImage = (prompt: string) => Promise<Buffer | null>;

export interface ImageStore {
  /** The download URL of an image already stored at [path], or null. */
  urlOf(path: string): Promise<string | null>;
  save(path: string, bytes: Buffer): Promise<string>;
}

export interface ImageSources {
  get(key: string): Promise<ImageSource | null>;
  record(entries: Array<[string, ImageSource]>, now: Date): Promise<void>;
  markWithheld(key: string): Promise<void>;
}

/** Same dish, same key: normalised so "Tortilla  de patatas" and "tortilla de patatas" share one image. */
export function imageKeyFor(title: string, dishType: string | null): string {
  const normalised = title.trim().toLowerCase().replace(/\s+/g, ' ');
  return createHash('sha256').update(`${normalised}\n${dishType ?? ''}`).digest('hex');
}

/** The only field is a key this function issued, so no sentence from the device reaches the model. */
export function parseImageRequest(raw: unknown): { imageKey: string } {
  const body = asObject(raw, 'request');
  if (body.schemaVersion !== IMAGE_SCHEMA_VERSION) {
    throw new BadRequest(`unsupported schemaVersion, this function speaks ${IMAGE_SCHEMA_VERSION}`);
  }
  const key = body.imageKey;
  if (typeof key !== 'string' || !/^[0-9a-f]{64}$/.test(key)) {
    throw new BadRequest('imageKey must be a key suggestRecipes issued');
  }
  return { imageKey: key };
}

export function buildPrompt(source: ImageSource): string {
  // Quotes and line breaks are dropped so the dish name stays one quoted value in the prompt.
  const clean = (text: string, max: number) => text.replace(/["\r\n]+/g, ' ').trim().slice(0, max);
  const lines = [
    'Appetising, realistic food photograph of one home-cooked dish, plated and ready to eat,',
    'on a kitchen table in soft natural light, shot from a three-quarter angle with a shallow depth of field.',
    `The dish: "${clean(source.title, MAX_TITLE)}".`,
  ];
  if (source.summary) lines.push(`What it is: ${clean(source.summary, MAX_SUMMARY)}.`);
  if (source.dishType) lines.push(`Kind of dish: ${source.dishType}.`);
  lines.push('No text, no lettering, no logos, no packaging, no hands and no people.');
  return lines.join(' ');
}

/**
 * Stored image first, model second. The quota is charged only when the model is about to run, so
 * reopening a dish never costs an allowance.
 */
export async function resolveRecipeImage(
  imageKey: string,
  deps: { store: ImageStore; sources: ImageSources; generate: GenerateImage; charge: () => Promise<unknown> },
): Promise<{ url: string; generated: boolean }> {
  const path = `recipe-images/${imageKey}.jpg`;
  const existing = await deps.store.urlOf(path);
  if (existing) return { url: existing, generated: false };

  const source = await deps.sources.get(imageKey);
  if (!source) throw new UnknownImageKey(imageKey);
  // Refused before: a filter that withheld this dish will withhold it again, and each try is charged.
  if (source.withheld) throw new NoImageReturned();

  await deps.charge();
  const bytes = await deps.generate(buildPrompt(source));
  if (!bytes) {
    await deps.sources.markWithheld(imageKey);
    throw new NoImageReturned();
  }
  return { url: await deps.store.save(path, bytes), generated: true };
}

/**
 * Gives each suggestion the key the app later asks for its picture with, and records what that
 * key stands for. A suggestion with no title gets no key: the client drops it anyway.
 */
export async function attachImageKeys<
  T extends { title: string | null; summary: string | null; tags: Array<{ taxonomy: string; term: string }> },
>(
  sources: ImageSources,
  suggestions: T[],
  now: Date,
): Promise<Array<T & { imageKey: string | null }>> {
  const entries: Array<[string, ImageSource]> = [];
  const keyed = suggestions.map((suggestion) => {
    const title = suggestion.title?.trim();
    if (!title) return { ...suggestion, imageKey: null };
    const dishType = suggestion.tags.find((tag) => tag.taxonomy === 'dish-types')?.term ?? null;
    const key = imageKeyFor(title, dishType);
    entries.push([key, { title, summary: suggestion.summary, dishType }]);
    return { ...suggestion, imageKey: key };
  });
  if (entries.length > 0) await sources.record(entries, now);
  return keyed;
}

export function geminiImageGenerator(genai: GoogleGenAI): GenerateImage {
  return async (prompt) => {
    const response = await genai.models.generateContent({
      model: IMAGE_MODEL_ID,
      contents: prompt,
      config: {
        responseModalities: [Modality.IMAGE],
        imageConfig: {
          aspectRatio: '16:9',
          personGeneration: 'ALLOW_NONE',
          outputMimeType: 'image/jpeg',
          outputCompressionQuality: 80,
        },
      },
    });
    const parts = response.candidates?.[0]?.content?.parts ?? [];
    const bytes = parts.find((part) => part.inlineData?.data)?.inlineData?.data;
    return bytes ? Buffer.from(bytes, 'base64') : null;
  };
}

type Bucket = ReturnType<ReturnType<typeof getStorage>['bucket']>;

/** The token-bearing URL a client opens; the token is what lets it past the closed rules. */
export function downloadUrl(bucket: string, path: string, token: string, endpoint?: string): string {
  const base = endpoint ?? process.env.STORAGE_EMULATOR_HOST ?? 'https://firebasestorage.googleapis.com';
  return `${base}/v0/b/${bucket}/o/${encodeURIComponent(path)}?alt=media&token=${token}`;
}

/**
 * Not `getDownloadURL`: the Admin SDK's `save` mints no download token, and that call fails on a
 * file without one. The token is written here instead, through the bucket API.
 */
export function storageImageStore(bucket: Bucket): ImageStore {
  const TOKEN_KEY = 'firebaseStorageDownloadTokens';
  return {
    async urlOf(path) {
      const file = bucket.file(path);
      const [exists] = await file.exists();
      if (!exists) return null;
      const [metadata] = await file.getMetadata();
      let token = String(metadata.metadata?.[TOKEN_KEY] ?? '').split(',')[0];
      if (!token) {
        token = randomUUID();
        await file.setMetadata({ metadata: { [TOKEN_KEY]: token } });
      }
      return downloadUrl(bucket.name, path, token);
    },
    async save(path, bytes) {
      const token = randomUUID();
      // The key is a content hash, so the bytes behind a path never change.
      await bucket.file(path).save(bytes, {
        contentType: 'image/jpeg',
        metadata: { cacheControl: 'public, max-age=31536000, immutable', metadata: { [TOKEN_KEY]: token } },
      });
      return downloadUrl(bucket.name, path, token);
    },
  };
}

export function firestoreImageSources(db: Firestore): ImageSources {
  return {
    async get(key) {
      const data = (await db.collection(SOURCES).doc(key).get()).data();
      if (!data || typeof data.title !== 'string') return null;
      return {
        title: data.title,
        summary: typeof data.summary === 'string' ? data.summary : null,
        dishType: typeof data.dishType === 'string' ? data.dishType : null,
        withheld: data.withheld === true,
      };
    },
    async record(entries, now) {
      const batch = db.batch();
      // A Firestore TTL policy on this field clears what nobody asked a picture for.
      const expiresAt = new Date(now.getTime() + SOURCE_KEEP_DAYS * 24 * 60 * 60 * 1000);
      for (const [key, source] of entries) {
        // Merged, so suggesting a refused dish again does not forget the refusal.
        batch.set(db.collection(SOURCES).doc(key), { ...source, expiresAt }, { merge: true });
      }
      await batch.commit();
    },
    async markWithheld(key) {
      await db.collection(SOURCES).doc(key).set({ withheld: true }, { merge: true });
    },
  };
}
