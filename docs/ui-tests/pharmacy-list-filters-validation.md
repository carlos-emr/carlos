# Rx pharmacy-list filtering

Run `npm run test:pharmacy-list-filters-playwright` with the shared live harness
configuration. The manifest entry `pharmacy-list-filters` belongs to the core tier.

The check creates an owned patient and two synthetic clinic pharmacies. It opens
Prescriptions from the Master Record and follows the Pharmacy link. It types
literal brackets and plus signs, changes name/address filters in both orders,
and verifies that city, postal code, phone and fax remain active when the name
changes. Input/paste events and case-insensitive matching are covered. It then
selects the matching pharmacy through the UI and verifies the patient association
both in the database and after reloading the page. Fixture cleanup removes only
the owned patient associations and marker-owned pharmacies; an unexpected link
from another patient prevents deletion of that pharmacy.

Related defect: #3769. All six filters now use literal visible text and are
applied together on every input event. The authenticated pharmacy JSON search
endpoint retains its existing wildcard behavior; this change is limited to the
clinic-list UI.

The baseline installed DEB reproduced the punctuation exception. Source-level
negative controls also reproduced incorrect address matching and an ignored
phone criterion. All 740 branch-local Node tests pass, including 12 focused
filter regressions. The live workflow passed all four steps on both installed
validation11 and validation12 DEBs (Ubuntu 26.04 / 8 GiB).

The combined validation12 tree passed 12,458 Java tests (zero failures/errors,
51 skips), 771 Node tests and 1,587 packaging/CLI tests. The matched package
upgrade preserved clinical counts and credentials and passed Flyway/deployment
checks. This check covers search and patient selection; separate pharmacy editor
coverage in #3773 exercises clinic-pharmacy creation, editing and deletion.
