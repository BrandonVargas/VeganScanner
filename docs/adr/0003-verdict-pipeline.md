# 0003. Vegan verdicts via a chain of resolvers

## Status
Accepted (2026-10)

## Context
Open Food Facts (OFF) is the primary data source, but its answer is often inconclusive (`maybe-vegan`, `vegan-status-unknown`) or the product is missing. The app adds more sources over time: a community cache, a rule engine, on-device AI and label OCR. These steps must stay easy to add, reorder and test in isolation.

## Decision
`VerdictPipeline` runs an ordered list of `VerdictResolver`s (Chain of Responsibility). Each resolver returns either `Conclusive(verdict)` or `Inconclusive(partial)`. The first conclusive verdict wins. Otherwise the partial verdicts are merged cautiously: MAYBE_VEGAN beats LIKELY_VEGAN, which beats UNKNOWN, and the doubtful ingredients are combined. That way the UI can always explain the result.

Order (✅ implemented, ⏳ planned):
1. ✅ `OffAnalysisResolver`: OFF `ingredients_analysis_tags`.
2. ⏳ `CommunityVerdictResolver`: verdicts other devices produced, read from Supabase.
3. ✅ `OffIngredientsResolver`: OFF per-ingredient `vegan` flags, evaluated recursively.
4. ✅ `RuleEngineResolver`: bundled en/es dictionary of animal-derived ingredients and E-numbers, applied to database text or a scanned label ([ADR 0006](0006-dictionary-and-label-ocr.md)). It can conclude NON_VEGAN; a clean check gives the non-conclusive LIKELY_VEGAN.
5. ⏳ `OnDeviceAiResolver`: Gemini Nano / Apple Foundation Models, only for still-ambiguous ingredients.

Nested ingredients are evaluated by `IngredientVeganEvaluator`. For example, "lecithin [maybe] (soy [yes])" resolves to vegan, and an unflagged functional label like "(preservative)" doesn't downgrade a vegan parent.

## Safety rules
- An AI step can never override a non-vegan finding from an earlier step.
- A conclusive answer from OFF always wins over community data.
- Every verdict carries its `VerdictSource`, which the UI always shows.

## Consequences
- Each resolver is a small, pure, separately tested class, and adding a step is a one-line change in `scannerModule`.
- Tests use recorded real OFF responses (`shared/core/testing/OffFixtures.kt`).
