# Ingredient dictionary

`ingredients.json` lists animal-derived and doubtful ingredients in **English and Spanish**. The app's rule engine (`IngredientRuleEngine`) uses it to check ingredient lists from Open Food Facts and from labels users scan. It's compiled into the app at build time, so editing this file is all it takes.

**No Kotlin knowledge needed to contribute. Additions for other regions and languages are very welcome.**

## Structure

```jsonc
{
  "id": "gelatin",                 // unique, kebab-case
  "status": "no",                  // "no" = not vegan · "maybe" = often animal-derived, check with the maker
  "category": "animal-tissue",     // dairy · egg · animal-tissue · seafood · insect · bee · additive · other
  "terms": {                       // whole words/phrases as printed on labels
    "en": ["gelatin", "gelatine"],
    "es": ["gelatina", "grenetina"]
  },
  "eNumbers": ["E441"]             // optional; also matches "E-441", "E 441" and "INS 441"
}
```

- **Accents and case don't matter.** `"lacteos"` also matches "LÁCTEOS".
- **Longer phrases win.** If `"suero de leche"` matches, the same words aren't also reported as `"leche"`.
- **`plantBasedExceptions`** are look-alikes that must never be flagged ("leche de coco", "manteca de cacao", "miel de agave"). Add one whenever a plant ingredient contains a flagged word.
- **`crossContaminationMarkers`** start allergen warnings ("puede contener trazas de leche"). Everything from the marker to the end of the sentence is ignored, because traces aren't ingredients.

## Checklist for a pull request

1. Add terms to an existing entry when the meaning is the same; otherwise create a new entry.
2. Every entry needs at least one `en` and one `es` term.
3. Don't flag an ingredient as `"no"` unless it is always animal-derived. When in doubt, use `"maybe"`.
4. Add a test case in `IngredientRuleEngineTest` for anything tricky (look-alikes, regional names).
5. Run `./gradlew :shared:feature:scanner:testAndroidHostTest`. `IngredientDictionaryTest` validates the file: unique ids and codes, no term owned by two entries, and no exception that is also a flagged term.
