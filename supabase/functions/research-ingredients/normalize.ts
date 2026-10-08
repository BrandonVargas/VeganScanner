/**
 * Same folding as the app's `TextFolding.foldTerm` (Kotlin): lowercase, no accents,
 * anything but [a-z0-9] becomes a space, whitespace collapsed. Used as the cache key.
 */
export function normalizeIngredientName(name: string): string {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

const ALLOWED = /^[\p{L}\p{N} .,'’()/%&+\-]+$/u;

/**
 * Ingredient names come from untrusted clients and end up in an LLM prompt, so only short, label-like text is
 * accepted: at most 60 characters and 6 words, letters, digits and basic punctuation.
 */
export function isAcceptableIngredientName(name: string): boolean {
  const trimmed = name.trim();
  const words = trimmed.split(/\s+/).filter(Boolean);
  return trimmed.length >= 2 && trimmed.length <= 60 && words.length <= 6 && ALLOWED.test(trimmed) &&
    normalizeIngredientName(trimmed).length > 0;
}
