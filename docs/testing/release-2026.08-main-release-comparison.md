# Main/release compatibility comparison for issue 5700

Main/release behavior is a required comparison. A candidate-only suite does not
establish compatibility. The `main-release-wire-compatibility` command is required
by T01/T08/T19/T21/T24, in addition to installed HTTP, persistence, browser and
package lifecycle requirements. It never makes those other checks pass.

## Binary and client identity

- Pinned main: `e53e6cb555125a942bbb28e52097972a24c88ad1`, published beta6.
- Main Debian SHA-256: `839c981b7de78460d89eaba0a8c95d992d88f861cc4739bba2b81753373a3af1`.
- Release base: `710fb1ac7c3fa75528359e71e8fac0d590b9e36f`.
- Development candidate source: `53709c7`; WAR SHA-256:
  `fb114c57d304d59973c565ec2bc6125f3a520b3680e0decba87e830a054175fc`.
- Client corpus: the committed `src/test/resources/xml/jaxb-moxy-baseline.json`,
  612 inputs over 308 types, plus 14 direct XML/JSON outputs and a local SOAP canary.
- Tomcat embed core 11.0.27 from Maven Central, SHA-256
  `b71785ff101c0df501064b3e3b8c4a02e4f9cc3047a7fa686c41fc4edc2f0cb4`.

Each side runs in a fresh JVM using its own classes and libraries. Tomcat's actual
resource and web application class loader select the JAXB provider. A sorted URL
class path or Maven's test dependency order can pick a different provider on main,
which packages both MOXy and Glassfish. Observations record the selected provider.
Session and OAuth JSON mappers come from their production Spring definitions,
including the anonymous SmartDateModule registration factory.

The comparator preserves XML child order, text, whitespace, attributes and missing
elements; namespace prefixes and declaration placement are irrelevant. It preserves
JSON boolean/number distinctions, arbitrary numeric precision, nulls and array order,
and rejects duplicate keys, nonfinite values and unsafe XML. New public Java API
checks also compare unchanged clients' available signatures over every changed
production type, including deletions, renames and public nested types. Additive
members cannot conceal removed or changed signatures. These are API compatibility
candidates; intentional internal API removals still need a reviewed disposition.

## Development observations

This is retained diagnostic evidence, collected while implementing the harness;
it is **not final source-bound qualification**. The original 627-case run found
589 matching wire cases, 34 unreviewed XML differences, three improved main-side
failures and a SOAP privacy failure on both sides. No listed difference has been
allowlisted. Some represent intentional fixes to an older MOXy contract; that does
not establish compatibility with packaged main's discovered provider. The new gate
stays failed until the main/release difference has an evidence-backed disposition.

The main-side failures improved by the release were ConsultationRequestTo1's
populated request (`UnsupportedOperationException`) and two BpmhForm2Bean outputs
that main emitted as malformed XML. A release-side rejection or malformed output
fails even when main also fails. Missing cases or identical probe errors cannot
silently qualify either binary.

The opt-in DownloadInInterceptor prints a synthetic SOAP payload on **both**
versions. This is pre-existing, and independently fails the required local privacy
acceptance. Only boolean observations are retained for this canary. No Ministry or
SRFax staging service was contacted; their qualification remains blocked by the
user's explicit access disposition.

The table lists exact synthetic differences for review. Position changes also
remain visible; date offsets and lexical forms are not normalized away.

| Case | Main-only output lines | Release-only output lines |
| --- | --- | --- |
| `AppointmentArchiveTransfer-values-xml` | `<appointmentEndDateTime>2026-07-15T13:14:15-04:00</appointmentEndDateTime>`<br>`<appointmentStartDateTime>2026-07-15T13:14:15-04:00</appointmentStartDateTime>`<br>`<createDateTime>2026-07-15T13:14:15-04:00</createDateTime>`<br>`<updateDateTime>2026-07-15T13:14:15-04:00</updateDateTime>` | `<appointmentEndDateTime>2026-07-15T13:14:15</appointmentEndDateTime>`<br>`<appointmentStartDateTime>2026-07-15T13:14:15</appointmentStartDateTime>`<br>`<createDateTime>2026-07-15T13:14:15</createDateTime>`<br>`<updateDateTime>2026-07-15T13:14:15</updateDateTime>` |
| `FilterDefinitionTransfer-default-xml` | `<params/>` | — |
| `commn.model.Demographic:default:xml` | `<lastUpdateDate>2026-07-15T13:14:15.120-04:00</lastUpdateDate>` | `<lastUpdateDate>2026-07-15T13:14:15.12-04:00</lastUpdateDate>` |
| `commn.model.Demographic:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>` |
| `commn.model.DemographicExt:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>` |
| `commn.model.ProfessionalSpecialist:default:xml` | `<lastUpdated>2026-07-15T13:14:15.120-04:00</lastUpdated>` | `<lastUpdated>2026-07-15T13:14:15.12-04:00</lastUpdated>` |
| `commn.model.Provider:default:xml` | `<lastUpdateDate>2026-07-15T13:14:15.120-04:00</lastUpdateDate>` | `<lastUpdateDate>2026-07-15T13:14:15.12-04:00</lastUpdateDate>` |
| `webserv.rest.to.SchedulingResponse:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>`<br>`<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>`<br>`<editVersion>7</editVersion>`<br>`<birthDay>1980-07-15</birthDay>`<br>`<editVersion>7</editVersion>` |
| `webserv.rest.to.model.AllowedAppointmentTypeTransfer:populated:xml` | `<codes xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>` | `<codes>A</codes>` |
| `webserv.rest.to.model.AppointmentExtTo:populated:xml` | `<hPhoneExt>7</hPhoneExt>` | `<hPhoneExt>7</hPhoneExt>` |
| `webserv.rest.to.model.AppointmentTo1:default:xml` | `<createDateTime>2026-07-15T13:14:15.120-04:00</createDateTime>`<br>`<updateDateTime>2026-07-15T13:14:15.120-04:00</updateDateTime>` | `<createDateTime>2026-07-15T13:14:15.12-04:00</createDateTime>`<br>`<updateDateTime>2026-07-15T13:14:15.12-04:00</updateDateTime>` |
| `webserv.rest.to.model.AppointmentTo1:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>`<br>`<editVersion>7</editVersion>` |
| `webserv.rest.to.model.BookingProviderTransfer:populated:xml` | `<codes xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>` | `<codes>A</codes>` |
| `webserv.rest.to.model.BookingScheduleTemplateCodeTransfer:populated:xml` | — | `<code>A</code>` |
| `webserv.rest.to.model.CaseManagementIssueTo1:default:xml` | `<update_date>2026-07-15T13:14:15.120-04:00</update_date>` | `<update_date>2026-07-15T13:14:15.12-04:00</update_date>` |
| `webserv.rest.to.model.ConsultationAttachmentTo1:populated:xml` | `<status>0</status>` | `<status>A</status>` |
| `webserv.rest.to.model.ConsultationRequestSearchResult:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>` |
| `webserv.rest.to.model.ConsultationRequestTo1:default:xml` | `<referralDate>2026-07-15T13:14:15.120-04:00</referralDate>` | `<referralDate>2026-07-15T13:14:15.12-04:00</referralDate>` |
| `webserv.rest.to.model.ConsultationResponseSearchResult:populated:xml` | `<birthDay>1980-07-15T00:00:00-04:00</birthDay>` | `<birthDay>1980-07-15</birthDay>` |
| `webserv.rest.to.model.DocumentTo1:default:xml` | `<contentDateTime>2026-07-15T13:14:15.120-04:00</contentDateTime>`<br>`<observationDate>2026-07-15T13:14:15.120-04:00</observationDate>`<br>`<status>0</status>`<br>`<updateDateTime>2026-07-15T13:14:15.120-04:00</updateDateTime>` | `<contentDateTime>2026-07-15T13:14:15.12-04:00</contentDateTime>`<br>`<observationDate>2026-07-15T13:14:15.12-04:00</observationDate>`<br>`<status>A</status>`<br>`<updateDateTime>2026-07-15T13:14:15.12-04:00</updateDateTime>` |
| `webserv.rest.to.model.DocumentTo1:populated:xml` | `<status>0</status>` | `<status>A</status>` |
| `webserv.rest.to.model.EFormTo1:default:xml` | `<formDate>2026-07-15T13:14:15.120-04:00</formDate>`<br>`<formTime>2026-07-15T13:14:15.120-04:00</formTime>` | `<formDate>2026-07-15T13:14:15.12-04:00</formDate>`<br>`<formTime>2026-07-15T13:14:15.12-04:00</formTime>` |
| `webserv.rest.to.model.FilterDefinitionTransfer:default:xml` | `<params/>` | — |
| `webserv.rest.to.model.MeasurementTo1:default:xml` | `<createDate>2026-07-15T13:14:15.120-04:00</createDate>` | `<createDate>2026-07-15T13:14:15.12-04:00</createDate>` |
| `webserv.rest.to.model.PharmacyInfoTo1:populated:xml` | — | `<status>A</status>` |
| `webserv.rest.to.model.ScheduleTemplateCodeTo:populated:xml` | — | `<code>A</code>` |
| `webserv.rest.to.model.SearchConfigTo1:populated:xml` | `<codes xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>`<br>`<openAccessList xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>` | `<key>A</key>`<br>`<code>A</code>`<br>`<codes>A</codes>`<br>`<openAccessList>A</openAccessList>` |
| `webserv.transfer_objects.CalendarScheduleCodePairTransfer:populated:xml` | — | `<scheduleCode>A</scheduleCode>` |
| `webserv.transfer_objects.DayWorkScheduleTransfer:populated:xml` | — | `<scheduleCode>A</scheduleCode>` |
| `webserv.transfer_objects.DocumentTransfer:default:xml` | `<status>0</status>` | `<status>`<br>`</status>` |
| `webserv.transfer_objects.DocumentTransfer:populated:xml` | `<status>0</status>` | `<status>A</status>` |
| `webserv.transfer_objects.DrugTransfer:populated:xml` | `<eTreatmentType>7</eTreatmentType>` | `<eTreatmentType>7</eTreatmentType>` |
| `webserv.transfer_objects.PrescriptionTransfer:populated:xml` | `<eTreatmentType>7</eTreatmentType>` | `<eTreatmentType>7</eTreatmentType>` |
| `webserv.transfer_objects.ScheduleTemplateCodeTransfer:populated:xml` | — | `<code>A</code>` |

## Public Java API observations

The expanded 628-probe run selected Glassfish on both binaries and compared
337 main-side and 361 release-side type inventory records. It additionally flagged
26 types with removed or changed public members. The complete synthetic signature
record is in [the API findings](release-2026.08-main-release-api-findings.json).
It includes the intentionally deleted DBHandler, DBPreparedHandler, dbExtract and
VelocityUtils classes, and signature changes in reports, labs, JDBC and actions.
These are conservative compatibility candidates; checked exceptions, generics
and inheritance can require source review even when a JVM descriptor is compatible.
No API removal has been silently excluded. Both the removal and its supported
replacement/affected callers need a documented disposition.

Main-side rejected or malformed responses remain unqualified baseline cases,
even when the release improves them. They cannot silently yield complete comparison
evidence. The separate required local privacy check still fails on payload logging.

## Reproduce

Set `CARLOS_MAIN_PACKAGE` to the hash-verified published main Debian asset and
`CARLOS_TOMCAT_CORE` to the pinned Tomcat jar above. Build the WAR from the frozen
candidate source. Then use the existing verifier:

```sh
python3 scripts/ci/verify-release-evidence.py run \
  --check main-release-wire-compatibility \
  --artifact /absolute/path/to/carlos.war \
  --evidence /absolute/path/to/fresh-evidence
```

The result references hashed binary identities, changed-type inventory, complete
main/release observations and comparison details. Final verification rechecks all
referenced bytes and the artifact binding. A successful no-op, missing case,
changed field type, new payload exposure or changed public signature cannot pass.
Retain failed attempts. Rebuild and rerun after changing source or the plan.

Still required: installed A/B status/refusal and persistence checks, exported
report contents, package upgrade/reinstall/rollback behavior, changed views and
all explicitly owned source ranges. These 628 binary probes cannot prove that all
possible breaking changes are absent.
