# Legacy projections of imported CDS records

The import preserves complete logical source fields in `cds_import_field`; restricted typed
laboratory results can exist solely in `cds_laboratory_result`. Existing native-only exports do
not serialize those representations. A released, clinically complete imported chart therefore
still requires a disclosure boundary: an empty native list must not be presented as a complete
export while structured or restricted data was silently omitted.

`CdsLegacyProjectionService` performs existence-only checks after the caller's existing patient
authorization. It refuses a complete chart projection when any imported source field or typed
laboratory result exists. Category projections refuse when that category has source fields;
laboratory projections also independently check typed result rows, including when source-field
rows are missing. No protected values, patient identifiers or restriction details appear in the
refusal. Ordinary non-imported records keep their existing projection.

The finite core entry points covered are:

| Consumer | Boundary |
| --- | --- |
| Existing demographic CDS/profile export | 409 plain-text refusal before archive creation |
| Full EChart PDF | 409 plain-text refusal before PDF/output stream creation |
| Selected encounter PDF | 409 for requested clinical categories before PDF rendering |
| REST prevention/immunization, including precision endpoints | 409 before native transfer conversion |
| REST HL7 laboratory list | 409 before native transfer conversion |
| REST allergy and medication list consumers | 409 at the category authorization boundary |
| SOAP allergy, prevention and care measurement reads/feeds | Explicit client fault, including empty native lists and unscoped feeds |
| REST care measurements | 409 before native entity serialization |
| Demographic REST `GET /demographics/{id}` optional allergies, measurements, medications and CPP notes | Existing patient/category authorization runs first; only requested imported categories are refused before their DAO reads/conversion |
| FHIR patient/search and immunization list/single resource | Explicit unsupported FHIR response before resource construction |

The existing authorized, category-local clinical source view remains available and applies its
own chart, category, sensitive-laboratory, maintenance and hold checks. These boundaries do not
implement a new exporter and do not turn a source-view privilege into disclosure authorization.
They do not disable ordinary chart editing/viewing simply because source fields exist.

The demographic optional-includes path applies the same category contract as the dedicated endpoints. CPP notes cover PersonalHistory, FamilyHistory, PastHealth, RiskFactors and AlertsAndSpecialNeeds; the pre-existing exclusion of ProblemList concerns and non-CPP ClinicalNotes remains intact. Unrequested categories, contacts and basic demographics keep their existing behavior. This closes the alternate native-only measurement/Date projection without introducing a new DTO or disclosure role.

Installed portals, additional SOAP/third-party integrations, arbitrary custom reports, direct SQL, bulk
extracts, alternate PDF renderers, and cached/indexed copies need runtime catalogue verification.
Maintenance and held charts are independently blocked by the physical SQL/HTTP gate; this
projection service additionally addresses *completed and released* records. Unsupported enabled
consumers must retain an explicit refusal/operational exclusion until their representation and
restriction contract is proved. This finite list is not a claim that all installed egress is safe.
