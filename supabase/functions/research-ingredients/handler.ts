import { isAcceptableIngredientName, normalizeIngredientName } from "./normalize.ts";
import { InvalidAnswerError, UpstreamError } from "./gemini.ts";
import type { KnowledgeStore, Language, ResearchedIngredient, Researcher, ResearchResponse } from "./types.ts";

export const MAX_INGREDIENTS_PER_REQUEST = 5;

export type HandlerResult =
  | { status: 200; body: ResearchResponse }
  | { status: 400; body: { error: string } };

/**
 * Cache first: every ingredient is researched on the web at most once for all users.
 * Each cache miss costs one reserved call (per-user and global daily limits) and one independent LLM request,
 * so a crafted "ingredient" can never influence the answer for another ingredient.
 */
export async function researchIngredients(
  input: unknown,
  userId: string,
  deps: { store: KnowledgeStore; researcher: Researcher },
): Promise<HandlerResult> {
  const request = parseRequest(input);
  if (typeof request === "string") return { status: 400, body: { error: request } };

  const response: ResearchResponse = { results: [], deferred: [], deferredReasons: {}, disputed: [], rejected: [] };
  const accepted = new Map<string, string>(); // normalized → display name, de-duplicated
  for (const name of request.ingredients) {
    if (!isAcceptableIngredientName(name)) {
      response.rejected.push(name);
      continue;
    }
    const normalized = normalizeIngredientName(name);
    if (!accepted.has(normalized)) accepted.set(normalized, name.trim());
  }

  const known = await deps.store.find([...accepted.keys()]);
  const misses: [string, string][] = [];
  for (const [normalized, name] of accepted) {
    const row = known.get(normalized);
    if (row?.state === "disputed") {
      response.disputed.push(name);
    } else if (row) {
      response.results.push({
        name,
        normalizedName: normalized,
        status: row.status,
        reasonEn: row.reason_en,
        reasonEs: row.reason_es,
        sources: row.sources,
        cached: true,
      });
    } else {
      misses.push([normalized, name]);
    }
  }

  const researched = await Promise.all(misses.map(async ([normalized, name]) => {
    if (!(await deps.store.reserveCall(userId))) return { name, result: null, reason: "budget" };
    try {
      const research = await deps.researcher.research(name, request.language);
      await deps.store.save({
        normalized_name: normalized,
        display_name: name,
        language: request.language,
        status: research.status,
        reason_en: research.reasonEn,
        reason_es: research.reasonEs,
        sources: research.sources,
        model: research.model,
        state: "active",
      });
      const result: ResearchedIngredient = {
        name,
        normalizedName: normalized,
        status: research.status,
        reasonEn: research.reasonEn,
        reasonEs: research.reasonEs,
        sources: research.sources,
        cached: false,
      };
      return { name, result, reason: null };
    } catch (error) {
      console.error(`Research failed for "${name}":`, error);
      return { name, result: null, reason: failureReason(error) };
    }
  }));
  for (const { name, result, reason } of researched) {
    if (result) {
      response.results.push(result);
    } else {
      response.deferred.push(name);
      response.deferredReasons[name] = reason ?? "error";
    }
  }

  return { status: 200, body: response };
}

function parseRequest(input: unknown): { ingredients: string[]; language: Language } | string {
  if (typeof input !== "object" || input === null) return "Expected a JSON object";
  const { ingredients, language } = input as Record<string, unknown>;
  if (language !== "en" && language !== "es") return "language must be 'en' or 'es'";
  if (!Array.isArray(ingredients) || ingredients.length === 0) return "ingredients must be a non-empty array";
  if (ingredients.length > MAX_INGREDIENTS_PER_REQUEST) {
    return `At most ${MAX_INGREDIENTS_PER_REQUEST} ingredients per request`;
  }
  if (!ingredients.every((it) => typeof it === "string")) return "ingredients must be strings";
  return { ingredients: ingredients as string[], language };
}

/** A short, non-sensitive reason code for clients; details stay in the function logs. */
function failureReason(error: unknown): string {
  if (error instanceof UpstreamError) return `upstream_${error.status}`;
  if (error instanceof InvalidAnswerError) return "invalid_answer";
  if (error instanceof DOMException && error.name === "TimeoutError") return "timeout";
  return "error";
}
