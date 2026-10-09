# Privacy Policy (draft)

_Last updated: 2026-10-07. This draft will be finalized before the first store release._

Vegan Scanner is a free, open-source app. It is built to collect as little data as possible.

## What the app does today

- **Camera:** used only on your device to read barcodes. No images leave your device.
- **Barcode lookups:** when you scan, the barcode number is sent to [Open Food Facts](https://world.openfoodfacts.org) to fetch product data. Open Food Facts receives the request like any website visit. See their [privacy policy](https://world.openfoodfacts.org/privacy).
- **Ingredient-label scans:** the photo is read by on-device text recognition (ML Kit on Android, Apple Vision on iOS) and discarded right away. It is never stored or uploaded. Only the ingredient text you confirm is saved, on your device, to evaluate that product.
- **Scan history and product cache:** stored only on your device. You can delete entries or clear the history at any time. Uninstalling the app removes them.
- **Online ingredient research:** when an ingredient isn't recognized, its **name only** (for example "goma gelana") is sent to our server on Supabase. The server asks Google's Gemini AI to assess it, using Wikipedia and Google Search, and saves the answer so everyone benefits. No barcode, photo or personal data is sent. The app uses an anonymous session (a random ID, no email or name) to limit requests and accept reports. Our Gemini API key is on Google's paid tier, under which Google doesn't use these requests to improve its products.
- **On-device AI:** when online research isn't possible, ingredient names may be checked by the AI model built into your phone (Gemini Nano on Android, Apple Intelligence on iPhone). This happens entirely on the device; nothing is sent anywhere.
- **Community verdicts:** when AI research marks a product as vegan, the app shares that verdict with other users. It includes the barcode, the ingredient list (which may come from your label scan), and the AI's reasons, linked to your anonymous session ID. No photo or personal data is shared. Reports you make on shared verdicts are private.
- **No sign-up, no ads, no tracking, no analytics.**

## Planned features

This document will be updated before these ship:

- **Crash reporting** (Firebase Crashlytics) to fix bugs.
- **Community verdicts:** when on-device AI decides a product is vegan, the barcode and the verdict will be shared anonymously so other users benefit, and other users can report wrong verdicts. (Reporting web-researched ingredients already works this way.)
- **Marketplace:** sellers will choose to publish listings, an approximate location (rounded to about 1 km) and a WhatsApp contact number. Account deletion will remove all of this.

## Contact

Open an issue at https://github.com/BrandonVargas/VeganScanner/issues.
