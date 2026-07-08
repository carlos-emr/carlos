# `common/` — province-neutral migrations

Generated baseline files land here (`V1__baseline_core_schema.sql`, `V1.1__baseline_core_seed.sql`,
`V1.2__reference_icd.sql`) when you run `database/mysql/build-baseline.sh` in a devcontainer.

**New province-neutral schema changes** go here as `VYYYY.MM.DD[.N]__short_description.sql`.

Flyway ignores this README (only `V*`/`R*` `.sql` files are migrations).
