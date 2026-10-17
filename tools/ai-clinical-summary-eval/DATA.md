# Data provenance and safety

All patient records committed in this directory are fabricated synthetic test
fixtures. They contain no production CARLOS records and no protected health
information.

The Evelyn Carter record was created for omission, conflict, citation, and
wrong-patient testing. Counterfactual variants change one declared synthetic
social attribute while retaining the base clinical record.

Generated model runs are deliberately ignored. Do not commit raw inference
artifacts, and never use this harness with real patient data unless an approved
privacy, security, retention, and clinical-safety design is in place.

The NHS England Synthetic Clinical Notes dataset discussed in issue #3455 is
not copied into this harness. Any future import must document the exact dataset
version and applicable license separately.
