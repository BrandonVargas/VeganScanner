# 0004. Offline-first data with Room KMP

## Status
Accepted (2026-10)

## Context
People scan in supermarkets with poor connectivity. OFF rate-limits product reads (100/min) and sometimes serves an HTML maintenance page. History must work offline.

## Decision
- Room KMP with the bundled SQLite driver is the single local database on both platforms.
- `DefaultProductRepository` order: fresh cache (7-day TTL) → network → stale cache if the network fails. "Not found" is never cached.
- The cache stores the raw OFF product JSON (`OffProductDto`), not domain models. Mapping changes and new rules then apply to cached products without a migration.
- Scan history keeps one row per barcode. Rescanning refreshes the verdict and timestamp.
- Room schemas are exported to `shared/core/database/schemas` and committed, so migrations can be reviewed and tested.

## Consequences
- Repeat scans are instant and don't hit OFF.
- Verdicts are recomputed from cached data, so improved rules reach old scans automatically.
