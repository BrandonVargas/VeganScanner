# 0008. On-device AI as the offline fallback for ingredient research

## Status
Accepted (2026-10)

## Context
Cloud research (ADR 0007) needs a connection and is capped by daily limits. Without it, unrecognized ingredients stay unrecognized. Recent phones ship a small language model that runs locally and for free: Gemini Nano through Android AICore, and Apple Foundation Models on iOS 26 with Apple Intelligence.

## Decision
- **Shared contract, native models.** `OnDeviceLanguageModel` in shared code has two calls: `isAvailable()` and `generate(instructions, prompt)`.
  - Android implements it with the ML Kit GenAI Prompt API (`GeminiNanoModel`). If the model is downloadable, the first availability check starts the download in the background.
  - iOS implements it with Foundation Models (`AppleFoundationModel`). The framework is weak-linked, because the app still supports iOS 17.
  - Each app passes its model to `startVeganKit`.
- **Shared prompt, parsing and safety.** `OnDeviceIngredientClassifier` sends one request per ingredient with the same rules and statuses as the server, then parses the JSON answer leniently. Unusable answers and requests over 20 s are dropped.
- **Fallback only.** `OnDeviceFallbackResearchRepository` tries online research first. The phone's model only sees names that research couldn't handle (offline, daily limit, service down). The online answer always wins: it has sources and is shared.
- **Same verdict rules as online research.** On-device answers only resolve unrecognized ingredients. A "non_vegan" answer makes the product non-vegan. Doubtful ingredients only get an explanation.
- **Clearly labeled.** A verdict resolved only on the phone has the source `ON_DEVICE_AI` ("Estimated by the AI on your phone"). Its ingredients show no sources and no report button, because nothing is shared. Answers aren't cached, so online research replaces them as soon as it's possible.

## Alternatives considered
- **On-device first:** private and free, but smaller models are less accurate and can't cite sources.
- **Structured-output APIs** (`@Generable`, ML Kit typed content): stricter output, but each platform does it differently. One JSON prompt keeps the platforms identical, and the lenient parser covers the small models' formatting slips.
- **Bundling an open model** (e.g. Gemma via MediaPipe): works on more devices, but adds hundreds of MB to the app.

## Consequences
- It only works on supported devices with the model ready. Elsewhere nothing changes, and the app shows the existing "couldn't look up" note.
- Small models are less reliable, so their per-ingredient answers aren't cached or shared. A product they make vegan can still be shared as a community verdict that records its source and has a warning and report option (ADR 0009).
- Nothing leaves the phone for these estimates.
