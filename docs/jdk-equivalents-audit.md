# Behaviour-preserving JDK replacements (#5483)

The audit is against `release/2026.08`. Commons dependencies remain on the
classpath for their other callers and transitive consumers. Persisted encodings,
hashes, insertion order, stream ownership and exception handling remain part of
the contract.

| Migrated operation | Call sites | Compatibility coverage |
| --- | --- | --- |
| Standard padded, unchunked Base64 encoding | Doc2PDF, UtilMisc, LabDisplayHelper, MessageUploader, ClientImage, APISendGridEmailSender, XmlUtils, EmailLog, Hl7TextInfoDaoImpl, OmpO09Builder, SearchConfig, MsgCommxml | `JdkEncodingCompatibilityUnitTest`: all byte values, empty, padding boundaries, MIME line boundary, large binary payload, non-ASCII, reachable null contracts and retained lenient decoding; existing email/search/image tests exercise callers |
| SHA-256 bytes and lowercase hex; legacy MD5 stream keys | AnnotatedDocumentService, MeasurementFlowSheet, RptByExampleData, LabVersionComparator, FileUploadCheck, Flowsheet2Action | `ContentDigestsUnitTest`: UTF-8, empty, null, leading zeroes, binary/large streams, read errors and caller-owned stream lifecycle; existing annotation/lab/report/flowsheet tests exercise callers |
| Byte-stream copy with discarded return count | DocumentUpload2Action, ManageDocument2Action, EFormImageViewForPdfGenerationServlet, DisplayImage2Action, ProviderSignatureImage2Action, IHAPOIHandler, DownloadInInterceptor | `JdkStreamCompatibilityUnitTest`: memory/file input, empty, binary/large streams, more than 2 GiB, write errors, no close/flush |
| Read entire stream into bytes | EDocUtil | Same stream differential tests |
| Cohort multimap | Cds4ReportUIBean | `JdkCollectionCompatibilityUnitTest`: actual production append methods, duplicate/null values, insertion order within each bucket and absent buckets returning null |
| Ordered disease map | OntarioMD | Same collection tests; its only caller uses `Map` iteration, with no positional map API |
| Nullable mutable key/value pairs | RunClinicalReport2Action, ReportEvaluator, ClinicalReports.jsp | Same collection tests; `SimpleEntry` preserves nullable values and mutable entries; JSP compilation checks the producer/consumer type change |
| Fixed elapsed-time millisecond constants | ProgramAccessDAOImpl, ProgramProviderDAOImpl, ClientImageDAOImpl, PreventionManager, RuleBaseFactory, PopulationReport2Action, OpenAccessFilter | `RetainedCommonsSemanticsUnitTest`: `Duration` values equal the old long constants; these are elapsed durations, not calendar-day arithmetic |
| Calendar-day counts (not a constant swap) | util.DateUtils (`getDifDays`, `getNumberOfDaysBetweenTwoDates`, `calculateDayDifference`), PMmodule.utility.DateUtils (`getDifDays`) | Corrected in #6047: these count calendar days, so a fixed 24-hour divisor miscounted any span crossing a daylight saving change. They now count complete local days (`ZonedDateTime`, `ChronoUnit.DAYS`); answers are unchanged on spans without a change. `DateUtilsDayDifferenceUnitTest`, `PMmoduleDateUtilsDifDaysUnitTest` |

`ContentDigests` centralizes the JDK's checked algorithm lookup and streaming
digest loop. It uses `MessageDigest`, UTF-8 and `HexFormat`, and never closes a
caller-owned stream. MD5 remains exclusively the existing lab duplicate key;
changing that algorithm would stop matching stored rows.

## Retained after checking semantics

- **Every Commons Base64 decoder and Base32 use:** stored data needs the lenient
  decoder, including URL-safe symbols, missing padding and ignored punctuation.
  Standard and MIME JDK decoders do not reproduce those semantics.
- **MultiReadHttpServletRequest.copyLarge:** its offset/length overload enforces
  `MAX_BODY_SIZE` and detects overflow. Unbounded `transferTo` would remove a
  security limit.
- **MEDITECHHandler.copy:** this is an `InputStream` to `Writer` overload with
  UTF-8 decoding, not a byte copy.
- **MeasurementFlowSheet.ListOrderedMap:** uses indexed insertion and
  `mapIterator`; a `LinkedHashMap` is not a drop-in equivalent.
- **closeQuietly sites:** AddEditDocument2Action, MeasurementFlowSheet,
  CreateBillingReport2Action, FHIRCommunicationRequestHandler, RxWebService,
  ISO36612 and ISO36612DaoImpl silently swallow only close-time IOExceptions.
  Plain try-with-resources propagates or suppresses them, and moving a catch
  changes logging or return paths. Some scopes also close both a wrapper and
  its underlying stream. No direct JDK substitution preserves these contracts;
  the retained-semantics test demonstrates the difference. These require a
  separately specified lifecycle change or a compatibility adapter.
- **Date formatting:** these sites accept `Date`/`Calendar` values, arbitrary
  report patterns or historical stored dates without an enforced modern-ISO
  range. FastDateFormat's hybrid Gregorian calendar and calendar chronology
  differ from java.time's proleptic ISO chronology. The tests demonstrate
  1500-01-01 and a Thai Buddhist calendar producing different text with the
  proposed instant/zone formatter. `java.sql.Date.toInstant()` is also not a
  valid general conversion. Formatting was therefore retained, as were all
  parsing, truncation and same-day calls. Pattern getters used for parsing
  were left alone.
- **FilenameUtils, FileUtils, StringUtils, Guava ASCII operations, HTTP/XML and
  other libraries explicitly excluded by the issue:** unchanged.

The hard requirement to preserve behaviour takes precedence over replacing a
call merely because the issue's initial inventory lists its file. The five new
compatibility test classes supplement the full suite and `-Pjspc` checks.

## Existing analysis debt

SonarCloud's PR analysis raised the pre-existing cognitive complexity of
`Hl7TextInfoDaoImpl.findLabAndDocsViaMagic(..., acknowledgedSince)` while
reanalysing this file. Its query body is unchanged from release base
`b4c0dfc94e6`; the Base64 replacement is in another method. That one method
has a specific `java:S3776` deferral so this encoding migration does not also
restructure permission-sensitive inbox SQL. This does not suppress SQL/security
analysis, other rules, or complexity checks elsewhere. The original finding is
`AaGUx4Dmtp6QPeC6m-s5` in the SonarCloud analysis of PR #5497.
