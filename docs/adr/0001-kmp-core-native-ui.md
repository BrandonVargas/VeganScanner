# 0001. Kotlin Multiplatform core with native UIs

## Status
Accepted (2026-10)

## Context
The app ships on Android and iOS, is maintained by one developer, and doubles as a portfolio piece. The core of the product (barcode lookup, vegan verdict rules, caching, history, and later the marketplace) is pure business logic. The UI layer depends heavily on platform capabilities: the camera, on-device OCR, and on-device LLMs (Gemini Nano, Apple Foundation Models).

## Decision
- All domain, data and presentation logic, ViewModels included, lives in Kotlin Multiplatform modules under `shared/`.
- UIs are fully native: Jetpack Compose on Android, SwiftUI on iOS.
- Platform capabilities are interfaces in `commonMain`, implemented natively (`expect`/`actual` or Swift classes injected through Koin).
- Modules are split by layer and feature (`core/*`, `feature/*`). `shared/umbrella` aggregates them into the single `VeganKit` iOS framework.

## Consequences
- Each verdict rule and state machine is written and tested once, and the tests run on both JVM and iOS.
- Each platform gets idiomatic UX (Material 3, and Liquid Glass/HIG on iOS) and first-class access to its own ML APIs.
- UI work is done twice. Compose Multiplatform was rejected for now because the native-UI-on-shared-core pattern is the more common choice in production and shows both skill sets.
