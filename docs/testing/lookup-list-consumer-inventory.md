# Lookup-list coverage inventory

Target: `release/2026.08`, inspected at b6fa82558f7 and its installed snapshot29
ON/BC packages. `lookup-list-isolation` prints the actual active-list inventory
before choosing cases. The BC installation has these three lists; ON additionally
has its configured PHU list, whose consumer is hidden by `phu.hide=true`:

| List | Native administration | Reachable consumers | Consumer option identity |
| --- | --- | --- | --- |
| `reasonCode` | Administration → Manage Lookup Lists | Schedule → owned appointment → Edit; Add Appointment; schedule reason display | Item integer ID; UUID remains the option's HTML ID |
| `consultApptInst` | Manage Lookup Lists; Customize Consult Appointment Instructions | E-Chart → Consultations → new/existing request, with `CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP` enabled | Item UUID |
| `practitionerNoType` | Manage Lookup Lists | Administration → Search/Edit Provider Records → owned provider; Add a Provider Record | Item UUID |
| `phu` (configured ON) | Manage Lookup Lists | Demographic editor/add form when `phu.hide=false`; FHIR configuration path | Item UUID |

The native manager offers item add and X removal. Removal deactivates a row;
it does not delete it. Empty labels make no insertion. Duplicate labels are
supported as separate rows with distinct generated UUIDs and appended display
orders. Labels are text, including punctuation and Unicode. There is no exposed
item-label edit, drag ordering, explicit order field, list creation or list
deletion control. The backend `order` method is therefore tested only as a
GET/tokenless-POST refusal, not claimed as a positive native reorder workflow.
The commented-out show/hide edit links are not user controls.

Additional named consumers depend on configured lists: `firstNationCommunity`
is used by the demographic First Nations editor when `FIRST_NATIONS_MODULE` is
enabled and `showBandNumberOnly` is false; `phu` is used by the demographic
Public Health Unit field and the FHIR configuration path. The community list is
absent in both default profiles, and PHU is absent in BC. The isolation check
also selects PHU when both its configured list and native editor select are
present; otherwise it prints the concrete configuration/navigation limitation.
The First Nations check requires an actual
enabled profile and configured three-digit communities; it does not mutate a
live lookup configuration. The current native enabled-profile defect is tracked
as [#5815](https://github.com/carlos-emr/carlos/issues/5815).

The new isolation workflow adds the same punctuation-bearing label to all three
shipped lists and a second identical label within the provider list. It checks
exact identities/order/active state in reopened management and consumer screens,
persists/reopens each consumer's selected value, and removes one list's row
without changing the others. Every removal uses the native application manager
to evict lookup caches. Newly opened consumers must immediately omit inactive
items. Final SQL teardown is permitted only after all owned rows are inactive;
an application cleanup failure retains them and fails the run. The observed
provider editor still offers inactive rows ([#5816](https://github.com/carlos-emr/carlos/issues/5816)).
This is a failed product assertion, not successful cache invalidation. After
recording that failure, an independent owned add/remove sentinel evicts caches
after SQL teardown, and every consumer is reopened to verify no marker remains.
That restoration does not turn the failed product assertion into a pass. Configuration
and item rows outside the marker-owned set are compared in full.

The test needs `EXCLUSIVE=1` because list entries and owned referral directory
fixtures are temporarily visible throughout the disposable clinic. SQL fixtures
for an owned appointment/provider/referral are separate from the native lookup
and selection-save coverage. The original `admin-lookup-lists` and
`consultation-appointment-instructions` checks remain registered.
