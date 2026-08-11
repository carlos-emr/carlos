# Ontario-Aligned Default Measurement Groups

## Purpose

Refine the default measurement groups introduced for issue 3356 so the diabetes and respiratory groups more closely reflect Ontario primary-care and chronic-disease measurement conventions while remaining compatible with CARLOS's existing measurement types.

## Scope

The existing five default groups remain. Two groups gain mappings:

- Diabetes Review adds `BP`, `WT`, `Body Mass Index`, `HDL`, `LDL`, `Triglycerides`, and `TC/HDL`.
- Respiratory Review adds `FVC (before puff)`, `FVC (after puff)`, `FEV1 / FVC ratio (before puff)`, and `FEV1 / FVC ratio (after puff)`.

All existing mappings remain unchanged. The result contains 34 default group-to-measurement mappings.

## Compatibility and Boundaries

The additions use exact `measurementType.typeDisplayName` values already present in both Ontario and British Columbia seed data. The migration continues to insert a mapping only when its measurement type exists, avoid duplicate group mappings and styles, preserve clinic-defined groups, and delete the legacy `Test` style only when it has no mappings.

The misspelled existing display name `Total Cholestorol` is not exposed in the default group. Correcting global measurement-type names is outside this change because existing mappings and integrations use those strings. Eye examinations, foot examinations, counselling, medication review, and other complete diabetes-flowsheet elements are also excluded because they belong to other CARLOS subsystems rather than ordinary measurements.

## Data and UI Behaviour

No schema, Java API, or route changes are required. The common Flyway migration and development SQL snapshot will contain the same 34 mappings. Existing measurement screens render the expanded groups through their current group lookup behaviour.

## Testing

The seed regression test will be changed first to require all new mappings and an expected total of 34, and must fail before production seeds are edited. After updating both seed sources, the regression test and focused measurement DAO, style, and action tests must pass.

The local development database will then be updated idempotently from the revised migration. Verification will assert five styles, 34 mappings across the five groups, no empty `Test` style, a successful HTTP response from CARLOS, and the expected per-group counts. The application will be rebuilt with `make install` and left running for manual review.
