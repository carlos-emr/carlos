# Clinical behavior coverage (#3774)

This group executes the release's BCAR 2007/2012 form population and patient-scoped load/save delegation; workflow creation, state/date updates and all three list projections; RH state descriptions, patient filtering and decision-support result propagation; inbox dispatch/authorization and populated summary versus empty paginated results; and drug-interaction DTO effect translation, malformed severity, identifier normalization and description fallback.

Local validation: 48 JUnit cases passed. A disposable mutation forcing every workflow transition to state 1 is detected. The VM was stopped throughout compilation. DAO/controller boundaries are mocked: these tests do not claim browser, database, or clinical-guideline validation. BCAR SQL assertions verify that both patient and record scope reach the helper, while the actual form transformations execute. Fixtures contain only synthetic values.
