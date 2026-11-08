# Decision-support operator coverage

Target: `release/2026.08`, inspected at b6fa82558f7 and validated against the
installed d67651993b1 snapshot29 ON package. Definitions and provider mappings
are SQL setup because this release has no guideline authoring UI. Positive
evaluation and the diagnosis source change use native chart/list/detail and Dx
Registry controls. No rule-engine return value supplies the expected truth table.

| Condition family | Implemented list operators | Context and selected coverage |
| --- | --- | --- |
| `dxcodes` | any, all, not, notany, notall | Active diagnosis codes; two active records, missing codes, independent condition failures and native resolution of exactly one code |
| `drugs` | any, all, not, notany, notall | Active prescription ATC values; two codes, a second matching record, a missing code and an empty patient's sources |
| `age` | any, all, not, notany, notall | Integer comparisons `<`, `<=`, `>`, `>=`, `=`; exact 30/31-year birthdays, year/month/day units, conjunction boundaries and negative multi-value conditions |
| `sex` | any, all, not, notany, notall | F/M positive and negative cases; distinct-value all/notall must follow the documented AND/negated-AND contract, with the current defect tracked as #5817 |
| `notes` | any, all, not, notany, notall | Latest nonarchived note versions; two distinct tokens, multiple matches and absent data. Literal tokens containing the marker's hyphen are quoted in this DSL |
| `billedFor` | any with parameters | Shipped `payer=MSP` / `notInDays` eligibility condition on ON/BC; the ON matrix checks recent/absent billing, exact 30-day boundary, 31-day window and multiple codes. Other operators and the parameterless overload throw `NOT IMPLEMENTED` |
| `paid` | any, all with parameters | Implemented settlement/eligibility readers with MSP options; not selected by this matrix. Negative variants throw `NOT IMPLEMENTED`; they are not treated as supported positives |
| `flowsheet` | any, notany | Depends on actual configured flowsheet/provider context. Other variants throw `NOT IMPLEMENTED`; no synthetic clinical policy or fake flowsheet is introduced here |
| `hasATCcode` | any, notany | Dynamic Rx-context arguments, rather than this patient-chart evaluation context. Other variants throw `NOT IMPLEMENTED` |
| `hasRxClass` | any, not, notany | Dynamic Rx-context arguments; all/notall throw `NOT IMPLEMENTED` |

`not` aliases `notany` for the selected source families. Numeric ranges are
expanded by the DSL into comparisons; the matrix explicitly specifies its
multi-comparison expectations. It does not assume numeric/string coercions or
new operators that the release does not implement.

The fixed 41-rule matrix is evaluated on A, B, A, then A after native diagnosis
resolution and B again: 205 exact rule/patient observations. A is female and
turns 30 on the observation date; B is male and turns 31, with no seeded
diagnosis, medication, note or billing sources. Every rule has an independently
specified per-condition result for each visit. Age fixtures handle leap-day
birth dates without creating an invalid date. `billedFor` with `notInDays` means
eligibility because no selected code was billed inside that window, rather than
an invented positive-presence interpretation.

The chart initially displays six warnings. The check uses its actual expander
and waits for the corresponding response/collapse control before asserting all
warning identities. Native More Info and List Guidelines controls are used for
each detail. Patient/rule identities and the displayed configuration must match;
every source snapshot remains unchanged by evaluation. A separate unmapped
provider must receive none of the owned warnings. Native diagnosis resolution
changes the selected source fact; fresh visits verify all affected and unaffected
conditions without a service restart.

Notes and billing have no implemented actual-value text reader in the detail
screen; the current UI prints `Error: Cannot get patient data`. Empty source
lists have a similar display limitation. The check does not invent an actual
value for those cells or claim this display is repaired. It still checks their
per-condition Boolean evaluation against independent SQL fixtures. Age, sex and
populated medication actual values are checked. Existing diagnosis-resolution
and per-condition cache checks remain registered.

The installed final run reaches all 205 observations and reports exactly 40
mismatches: the two sex all/notall cases across chart warning, list result,
detail consequence and condition result on all five visits. These are fatal and
tracked in [#5817](https://github.com/carlos-emr/carlos/issues/5817). Every other
selected operator case, source transition, provider scope and clinical snapshot
passes; the guideline/patient/provider cleanup leaves zero owned rows. There is
no expected-failure pin or release waiver. Synthetic JSON observations are
retained in private execution storage with source/package provenance.
