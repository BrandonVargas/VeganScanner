# 0006. Ingredient dictionary as data, OCR in the platform UI

## Status
Accepted (2026-10)

## Context
Phase 2 adds two things:
- A rule engine for ingredients that Open Food Facts doesn't recognize.
- A way to read a product's printed ingredient list when the database has no data, or only inconclusive data.

Both must work offline, in English and Spanish, and with contributions from non-Kotlin developers.

## Decision
**Dictionary as data**
- `shared/feature/scanner/dictionary/ingredients.json` holds terms, E-numbers, plant-based look-alikes and cross-contamination markers.
- A Gradle task (`generateIngredientDictionary`) embeds the JSON as a Kotlin string in `commonMain`. Both platforms then ship the same data without platform resource loading.
- `IngredientDictionaryTest` validates the file in CI.

**Open Food Facts taxonomy (added in phase 2b)**
- A curated dictionary alone covers too little. The Open Food Facts ingredient taxonomy (ODbL) adds about 5,000 ingredients with an effective vegan status (inherited through `parents`) and English/Spanish names.
- `updateOffTaxonomy` (build-logic) trims it into the committed `dictionary/off-taxonomy.json`. Curated `overrides` (e.g. sugar → vegan) are applied to whole sub-trees, but never relax an explicit `no`.
- A weekly GitHub Action refreshes the file and opens a PR.
- `GenerateDictionarySourceTask` embeds it in chunks, because a JVM string constant is limited to 64 KB.
- Precedence: curated terms, then curated look-alikes and vegan staples, then the taxonomy.

**Matching**
- Text is folded (lowercase, no accents, punctuation → space) one character at a time, so match positions map back to the original text. Flags show the wording printed on the label.
- The text is split into items. In each item, the longest known phrase wins, using hash lookups of word n-grams. Per-term regexes don't scale to thousands of terms.
- "May contain…" clauses are masked first. Plant-based look-alikes are vegan terms that win by being longer.
- Each item is classified as not vegan, doubtful, vegan or unrecognized. Connector and processing words don't count against recognition.

**Verdicts**
- The rule engine concludes NON_VEGAN on any animal-derived ingredient, and **VEGAN only when every item is recognized as vegan**. When some items are unrecognized, the result is **LIKELY_VEGAN** ("Probably vegan", not conclusive), and those items are listed for the user and for later research steps.
- The pipeline merges partial verdicts cautiously: MAYBE beats LIKELY, which beats UNKNOWN. One exception: a MAYBE that only lists *unrecognized* ingredients is upgraded to LIKELY by a clean dictionary check.

**OCR lives in the platform UI**
- OCR runs in the UI layer: CameraX + ML Kit Text Recognition on Android, VisionKit + Vision on iOS.
- Only the recognized text reaches shared code (`LabelScanViewModel`). There, `IngredientLabelText` trims it to the ingredient list. Image handling stays native, and no images cross the KMP boundary.
- Users review and correct the text before it's used.
- The text is stored per barcode in Room (`label_scan`, DB v2 via auto-migration). `ScanProductUseCase` prefers it over database ingredient text. It also works for products missing from Open Food Facts and when offline.
- Photos are never stored or uploaded.

## Consequences
- The dictionary grows through simple JSON pull requests, and its integrity is enforced by tests.
- "Probably vegan" is honest about uncertainty and leaves room for the on-device AI step (phase 3) to confirm.
- OCR quality depends on the photo. The review step covers misreads.
