# `common/` — shared migrations (reserved)

Currently **empty**. The initial `V1` baseline is province-complete (it lives in `on/` and `bc/`),
so there is no shared baseline file here yet.

This location is applied together with the selected province (`common` + `on`, or `common` + `bc`).
Put **genuinely shared future schema changes** here as `VYYYY.MM.DD[.N]__short_description.sql` so a
single migration covers both provinces.

Flyway ignores this README (only `V*`/`R*` `.sql` files are migrations).
