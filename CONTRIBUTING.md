# Contributing to Vegan Scanner

Thanks for helping! Bug reports, translations, ingredient knowledge and code are all welcome.

## Ground rules

- Be kind. Everyone is welcome regardless of diet.
- Open an issue before large changes, so we can agree on the approach first.
- Keep pull requests focused. One concern per PR.

## Development setup

See [Getting started](README.md#getting-started). Before opening a PR, run:

```bash
./gradlew spotlessApply                          # format (ktlint + Compose rules)
./gradlew spotlessCheck testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:lintDebug
./gradlew iosSimulatorArm64Test                  # macOS only
```

If you change the iOS project, edit `iosApp/project.yml` and run `xcodegen`. Don't edit the `.xcodeproj` by hand.

## Architecture in one minute

- Business logic, ViewModels included, goes in `shared/`, never in a platform app.
- Platform apps only render state and forward user actions.
- New verdict logic is a new `VerdictResolver`. See [ADR 0003](docs/adr/0003-verdict-pipeline.md).
- Decisions worth remembering get an ADR in [`docs/adr`](docs/adr).

## Tests

- Shared code: `commonTest` with `kotlin.test` + Turbine. Use the recorded Open Food Facts responses in `shared/core/testing` instead of the live API.
- Android UI: Roborazzi screenshot tests. If you change UI on purpose, re-record with `./gradlew :androidApp:recordRoborazziDebug` and commit the new PNGs.
- iOS: Swift Testing for models, XCUITest for flows.

## Translations

Android strings live in `androidApp/src/main/res/values*/strings.xml`. iOS strings live in `iosApp/VeganScanner/Resources/Localizable.xcstrings`. Please update both.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org) (`feat:`, `fix:`, `docs:`, `chore:`…).

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
