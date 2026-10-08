# 0005. Swift interop: SKIE and shared ViewModels

## Status
Accepted (2026-10)

## Context
Shared ViewModels expose `StateFlow`s, sealed interfaces and suspend functions. Plain Kotlin/Native ObjC export makes these awkward to use from Swift, and it gives ViewModels no lifecycle on iOS.

## Decision
- **SKIE** (Touchlab) turns Flows into `AsyncSequence`, sealed hierarchies into exhaustive Swift enums (`onEnum(of:)`), and suspend functions into `async`.
- **Lifecycle:** `ViewModelStoreHolder` (Kotlin, iosMain) wraps a `ViewModelStore`. Each SwiftUI screen model owns one holder and calls `clear()` from `deinit`, which cancels `viewModelScope`, exactly as Android does when a screen leaves the back stack.
- Swift screen models are `@Observable @MainActor` classes that mirror `state` through `for await`.
- **Text input stays in the UI.** Text fields must update synchronously with the IME/keyboard, so the text lives in Compose `TextFieldState` or SwiftUI `@State`. The ViewModel receives the value on submit, and input formatting (`sanitizeManualEntry`) is a shared pure function.
- The iOS target uses Swift 5 language mode for now. Kotlin types aren't `Sendable`, and strict Swift 6 concurrency adds noise without safety gains here, because all ViewModel access happens on the main actor.
- Kotlin API names are chosen to avoid Swift clashes (for example `VerdictSource.UNDETERMINED`, not `NONE`). Data-layer classes are `internal`, which keeps Ktor and Room types out of the Swift API.

## Consequences
- SwiftUI code reads naturally (`switch onEnum(of: state)`), and the compiler enforces exhaustiveness.
- SKIE versions must track Kotlin versions. Renovate groups them.
