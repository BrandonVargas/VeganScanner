# Ingredient knowledge

The rule engine (`IngredientRuleEngine`) checks ingredient lists from Open Food Facts and from labels users scan. It uses two data files. Both are compiled into the app at build time and work offline.

| File | What it is | Who edits it |
|---|---|---|
| `ingredients.json` | **Curated dictionary.** Hand-reviewed English/Spanish terms, Mexican label names, plant-based look-alikes, "may contain" markers and corrections (`overrides`) to Open Food Facts. **Always wins** on conflicts. | Contributors, through pull requests |
| `off-taxonomy.json` | **Open Food Facts ingredient taxonomy**, trimmed to about 5,000 ingredients with their vegan status and English/Spanish names. © Open Food Facts contributors, [ODbL](https://opendatacommons.org/licenses/odbl/1-0/). | Generated: `./gradlew :shared:feature:scanner:updateOffTaxonomy`, refreshed weekly by CI. **Don't edit by hand.** |

If something is missing or wrong, first check whether it should be fixed upstream in the [Open Food Facts taxonomy](https://github.com/openfoodfacts/openfoodfacts-server/tree/main/taxonomies), so every app benefits. Use `ingredients.json` for regional terms and for decisions that are specific to this app.

**No Kotlin knowledge needed to contribute.**

## How ingredient lists are checked

1. The text is split into items at `, ; ( ) [ ] .`. Text before a `:` ("Emulsificantes:") is treated as a heading.
2. "May contain / puede contener trazas de…" statements are ignored. Traces aren't ingredients.
3. In each item, the **longest known phrase** wins. "Leche de coco" (vegan look-alike) beats "leche", and "suero de leche" isn't also reported as "leche".
4. Each item becomes **not vegan**, **doubtful**, **vegan** or **unrecognized**. Connector and processing words ("de", "y", "orgánico", "en polvo", "integral", quantities) don't count against recognition.
5. A doubtful or unrecognized compound followed by its composition, like "base de avena (agua, avena)", is judged by the listed sub-ingredients instead of its generic name. A non-vegan name ("clara (agua)") stays flagged.
6. The product is **Vegan** only when *every* item is recognized as vegan. Unrecognized items are listed so you can check them, and they are what the web-research step will look up.

## `ingredients.json` structure

```jsonc
{
  "overrides": [            // corrections to Open Food Facts, applied to the whole sub-tree
    { "id": "en:sugar", "status": "yes", "reason": "Why we disagree with Open Food Facts" }
  ],
  "entries": [{
    "id": "gelatin",                 // unique, kebab-case
    "status": "no",                  // "no" · "maybe" (often animal-derived) · "yes" (vegan staples Open Food Facts lacks)
    "category": "animal-tissue",     // dairy · egg · animal-tissue · seafood · insect · bee · additive · plant · other
    "terms": { "en": ["gelatin", "gelatine"], "es": ["gelatina", "grenetina"] },
    "eNumbers": ["E441"]             // optional; also matches "E-441", "E 441" and "INS 441"
  }],
  "plantBasedExceptions": { "es": ["leche de coco", "manteca de cacao"] },
  "crossContaminationMarkers": { "es": ["puede contener", "trazas de"] }
}
```

- **Accents and case don't matter.** `"lacteos"` also matches "LÁCTEOS".
- **Overrides** never relax an explicit `no` in Open Food Facts. After changing them, run `updateOffTaxonomy` so the change reaches the generated file.

## Checklist for a pull request

1. Add terms to an existing entry when the meaning is the same; otherwise create a new entry.
2. Every entry needs at least one `en` and one `es` term.
3. Don't mark an ingredient `"no"` unless it is always animal-derived. When in doubt, use `"maybe"`.
4. Add a test case in `IngredientRuleEngineTest` for anything tricky (look-alikes, regional names).
5. Run `./gradlew :shared:feature:scanner:testAndroidHostTest`. `IngredientDictionaryTest` and `OffTaxonomyTest` validate both files.
