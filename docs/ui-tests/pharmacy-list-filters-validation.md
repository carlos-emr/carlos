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

At PR creation, the old JSP handlers reproduced the punctuation exception,
incorrect address matching and ignored phone criterion in a Node VM. All 740
Node tests pass, including 12 focused filter regressions. Live installed-DEB
execution is pending adequate host disk space; it has not yet been claimed as
passing. This check covers search and patient selection, not clinic-pharmacy
creation, editing or deletion through the UI.
