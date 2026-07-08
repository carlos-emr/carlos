# `on/` — Ontario migrations

The frozen Ontario slice of the V1 genesis baseline:
- `V1.0.1__on_schema.sql` — Ontario-only tables (structure)
- `V1.0.2__on_data.sql` — Ontario reference data (full: shared + Ontario rows)

Applied together with `common/` for an Ontario install (`flyway.locations=...common,...on`). New
Ontario-only changes go here as `VYYYY.MM.DD[.N]__short_description.sql`.
