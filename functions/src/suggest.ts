import { GoogleGenAI, Type } from '@google/genai';
import type { Catalogue, ReadableRequest } from './catalogue.js';

/**
 * Fast and cheap: this runs while a user waits, and a suggestion is a starting point rather
 * than a final answer. Named here and nowhere else — the client never learns which model wrote
 * its recipes except from the `modelId` this function reports back.
 */
export const MODEL_ID = 'gemini-2.5-flash';

/** Ours, not the model's. It identifies this implementation of the agent seam. */
export const AGENT_ID = 'pantry-suggest';

/**
 * The shape the model must answer in. Structured output rather than a plea in the prompt: a
 * response that does not parse is not a bad suggestion, it is a failed call.
 */
const responseSchema = {
  type: Type.OBJECT,
  properties: {
    suggestions: {
      type: Type.ARRAY,
      items: {
        type: Type.OBJECT,
        properties: {
          title: { type: Type.STRING },
          summary: { type: Type.STRING },
          servings: { type: Type.INTEGER },
          totalMinutes: { type: Type.INTEGER },
          steps: { type: Type.ARRAY, items: { type: Type.STRING } },
          ingredients: {
            type: Type.ARRAY,
            items: {
              type: Type.OBJECT,
              properties: {
                ingredientId: { type: Type.STRING, description: 'an id from the catalogue, or empty' },
                freeText: { type: Type.STRING, description: 'used only when no catalogue id fits' },
                amount: { type: Type.NUMBER },
                unitTerm: { type: Type.STRING, description: 'a unit id from the catalogue, or empty' },
                optional: { type: Type.BOOLEAN },
              },
              required: ['amount', 'optional'],
            },
          },
        },
        required: ['title', 'summary', 'servings', 'totalMinutes', 'steps', 'ingredients'],
      },
    },
  },
  required: ['suggestions'],
} as const;

export interface Vocabulary {
  ingredients: { id: string; name: string }[];
  units: { id: string; name: string }[];
  unitTaxonomy: string | null;
}

/**
 * The instruction template. It lives here because it must: a prompt on the device is a prompt
 * an attacker can rewrite, and one in the repository is one we can change without a release.
 *
 * The request arrives already reduced to words by [toReadable], so nothing the user typed
 * reaches this function — only labels the catalogue itself supplies.
 */
function instructions(request: ReadableRequest, vocabulary: Vocabulary): string {
  const pantry = request.pantry
    .map((it) => `- ${it.name}: ${it.amount}${it.unit ? ` ${it.unit}` : ''}${it.expiringSoon ? ' (use this first, it is about to spoil)' : ''}`)
    .join('\n');

  const lines = [
    'You suggest home recipes from what someone already has in their kitchen.',
    '',
    `They cook for ${request.servings}.`,
    `Suggest at most ${request.maxResults} dishes.`,
    request.maxMinutes ? `Nothing that takes longer than ${request.maxMinutes} minutes.` : null,
    request.useOnlyPantry
      ? 'Use only what is in their kitchen. Do not add anything they do not have.'
      : 'Prefer what they already have; a few common extras are fine.',
    '',
    'In their kitchen:',
    pantry || '- nothing yet',
    '',
    request.excluded.length
      ? `MUST NOT appear, in any amount, in any dish, including as a trace: ${request.excluded.join(', ')}. These are allergies or hard exclusions. If a dish would need one, suggest a different dish.`
      : null,
    request.avoided.length ? `They would rather avoid: ${request.avoided.join(', ')}.` : null,
    request.preferred.length ? `They enjoy: ${request.preferred.join(', ')}.` : null,
    '',
    'Ingredient lines: set ingredientId to one of the catalogue ids below when the ingredient is one of them, and leave freeText empty. Otherwise leave ingredientId empty and put the name in freeText.',
    `Catalogue ingredients: ${vocabulary.ingredients.map((it) => `${it.id}=${it.name}`).join(', ') || 'none'}`,
    `Units: ${vocabulary.units.map((it) => `${it.id}=${it.name}`).join(', ') || 'none'}`,
    '',
    'Write the dishes in the language the kitchen list above is written in.',
    'Steps are plain sentences. Do not number them, do not add headings, and do not write anything outside the fields.',
  ];
  return lines.filter((line) => line !== null).join('\n');
}

export interface ModelSuggestion {
  title?: string;
  summary?: string;
  servings?: number;
  totalMinutes?: number;
  steps?: string[];
  ingredients?: {
    ingredientId?: string;
    freeText?: string;
    amount?: number;
    unitTerm?: string;
    optional?: boolean;
  }[];
}

export async function askModel(
  client: GoogleGenAI,
  request: ReadableRequest,
  vocabulary: Vocabulary,
): Promise<ModelSuggestion[]> {
  const response = await client.models.generateContent({
    model: MODEL_ID,
    contents: instructions(request, vocabulary),
    config: {
      responseMimeType: 'application/json',
      responseSchema,
      // Low, not zero: the same pantry on two days should not give the same three dishes.
      temperature: 0.7,
    },
  });

  const text = response.text;
  if (!text) return [];
  const parsed: unknown = JSON.parse(text);
  const suggestions = (parsed as { suggestions?: unknown }).suggestions;
  return Array.isArray(suggestions) ? (suggestions as ModelSuggestion[]) : [];
}

/**
 * Back to identifiers. A model works in words, and a word is not something the pantry can be
 * compared against — so an ingredient it claims is in the catalogue is only kept as a pointer
 * if the catalogue agrees. A line the model never claimed for the catalogue travels as free
 * text, which the app already reports as unverifiable rather than missing.
 */
export function toWire(
  suggestions: ModelSuggestion[],
  catalogue: Catalogue,
  unitTaxonomy: string | null,
  maxResults: number,
) {
  return suggestions.slice(0, maxResults).map((suggestion) => ({
    title: suggestion.title ?? null,
    summary: suggestion.summary ?? null,
    servings: suggestion.servings ?? null,
    totalMinutes: suggestion.totalMinutes ?? null,
    steps: (suggestion.steps ?? []).filter((step) => typeof step === 'string'),
    tags: [],
    ingredients: (suggestion.ingredients ?? [])
      .map((line) => {
        const claimed = typeof line.ingredientId === 'string' && line.ingredientId.length > 0;
        const known = claimed && catalogue.ingredient(line.ingredientId as string) ? (line.ingredientId as string) : null;
        const unit =
          unitTaxonomy && line.unitTerm && catalogue.term({ taxonomy: unitTaxonomy, term: line.unitTerm })
            ? { unitTaxonomy, unitTerm: line.unitTerm }
            : { unitTaxonomy: null, unitTerm: null };
        return {
          ingredientId: known,
          // A pointer the catalogue rejected does not fall back to text: the model believed this
          // was a catalogue ingredient, and passing its name on would put a line the pantry
          // cannot check beside one it can, with nothing to tell them apart.
          freeText: known || claimed ? null : (line.freeText ?? null),
          amount: typeof line.amount === 'number' ? line.amount : null,
          ...unit,
          optional: line.optional === true,
        };
      })
      // Neither a pointer nor a name is not a line. The client would drop it anyway.
      .filter((line) => line.ingredientId !== null || line.freeText !== null),
  }));
}
