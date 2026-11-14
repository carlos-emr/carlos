/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the HRM reports through the real Reports occurrence service over a real encrypted
 * capture: a text report, a binary (PDF) report, a report whose stored message is missing, a stored
 * file that is no HRM message and a link to a report that no longer exists, with comments,
 * subclasses and sign-offs. The output is schema-valid CDS 5.2, every attachment is the exact
 * content, and the inventory expects exactly the emitted identities.
 */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportHospitalReportAssemblyUnitTest {
    private static final String HASH = "a".repeat(64), PATIENT_KEY = "b".repeat(64);
    private static final String RUN = "44444444-4444-4444-8444-444444444444";
    private static final int PATIENT = 27;
    private static final byte[] PDF =
            "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> FILE_COLUMNS =
            Set.of("hrm_document_id", "report_file", "state", "content");

    @TempDir Path root;

    private final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
    private final CdsExportRetainedReceiptVerificationService verification =
            mock(CdsExportRetainedReceiptVerificationService.class);
    private final CdsExportNativeRowReceiptReader witnesses =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
    private final Map<String, CdsExportPackageMemberDto> attachments = new TreeMap<>();
    private final Map<String, TreeMap<String, Map<String, String>>> rows = new TreeMap<>();
    /** The captured message file of each report: its reference, state and bytes. */
    private final TreeMap<Integer, Object[]> files = new TreeMap<>();
    /** The installed window (8,192 reports, 64 MiB) unless a test states a small one. */
    private CdsExportClinicalOccurrenceService.Bounds bounds =
            new CdsExportClinicalOccurrenceService.Bounds(
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                    CdsExportClinicalOccurrenceService.Bounds.UNLIMITED);
    private CdsExportStagingService store;
    private CdsExportLeaseDto lease;
    private CdsExportClinicalOccurrenceService.Result result;
    private List<CdsExportIdentityDto> expected;

    @BeforeEach
    void setup() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 43);
        store =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 64L << 20, 0, () -> {});
        when(stores.openExisting(any(), anyLong(), any(), any())).thenReturn(store);
        when(patient.patient()).thenReturn(PATIENT);
        var command =
                new CdsExportCommand(
                        "hrm-assembly",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(PATIENT),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                        "recipient",
                        "audit");
        lease =
                new CdsExportLeaseDto(
                        RUN,
                        1,
                        HASH,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
    }

    private TreeMap<String, Map<String, String>> rows(String table) {
        return rows.computeIfAbsent(table, ignored -> new TreeMap<>());
    }

    private void add(CdsExportCapturedTextRowDto row) {
        rows(row.table()).put(row.recordKey(), new TreeMap<>(row.fields()));
    }

    private void provider(String number, String first, String last, String ohip) {
        add(CdsExportHospitalReportMapperUnitTest.provider(number, first, last, ohip));
    }

    /** A linked report with its row, its link and its captured message file. */
    private void report(int id, CdsExportHospitalReportFileSourceReader.State state, byte[] file) {
        add(
                CdsExportHospitalReportMapperUnitTest.reportRow(
                        id, CdsExportHospitalReportMapperUnitTest.report(id)));
        add(CdsExportHospitalReportMapperUnitTest.link(100 + id, id));
        files.put(id, new Object[] {"LabUpload.demo-" + id + ".xml.1690313901104", state, file});
    }

    private Map<String, CdsExportCapturedTableDto> scopes() {
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        scopes.put("document", new CdsExportCapturedTableDto(Set.copyOf(Plan.DOCUMENTS.columns()), 0, HASH));
        scopes.put(
                "ctl_document",
                new CdsExportCapturedTableDto(Set.copyOf(Plan.DOCUMENT_LINKS.columns()), 0, HASH));
        scopes.put(
                CdsExportDocumentFileSourceReader.TABLE,
                new CdsExportCapturedTableDto(Set.of("document_no", "filename", "content"), 0, HASH));
        scopes.put("cds_import_file", new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        scopes.put(
                "cds_import_file_reference", new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(Set.copyOf(Plan.DEMOGRAPHIC_MERGES.columns()), 0, HASH));
        scopes.put(
                CdsExportDocumentRouteSourceReader.TABLE,
                new CdsExportCapturedTableDto(Set.copyOf(Plan.DOCUMENT_ROUTES.columns()), 0, HASH));
        scopes.put(
                "document_review",
                new CdsExportCapturedTableDto(Set.copyOf(Plan.DOCUMENT_REVIEWS.columns()), 0, HASH));
        scopes.put(
                "DocumentExtraReviewer",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENT_EXTRA_REVIEWERS.columns()), 0, HASH));
        scopes.put(
                "provider",
                new CdsExportCapturedTableDto(
                        CdsExportProviderRowDto.COLUMNS, rows("provider").size(), HASH));
        scopes.put(
                "clinical_timezone",
                new CdsExportCapturedTableDto(Set.of("sha256", "tzdbVersion", "zoneId"), 1, HASH));
        for (var entry :
                Map.of(
                                "HRMDocumentToDemographic", Plan.HRM_REPORT_LINKS,
                                "HRMDocument", Plan.HRM_REPORTS,
                                "HRMDocumentComment", Plan.HRM_REPORT_COMMENTS,
                                "HRMDocumentSubClass", Plan.HRM_REPORT_SUBCLASSES,
                                "HRMDocumentToProvider", Plan.HRM_REPORT_ROUTES)
                        .entrySet())
            scopes.put(
                    entry.getKey(),
                    new CdsExportCapturedTableDto(
                            Set.copyOf(entry.getValue().columns()),
                            rows(entry.getKey()).size(),
                            HASH));
        scopes.put("hrm_report_files", new CdsExportCapturedTableDto(FILE_COLUMNS, files.size(), HASH));
        return scopes;
    }

    private CdsExportPatientCaptureDto capture(Map<String, CdsExportCapturedTableDto> scopes)
            throws Exception {
        var zone = CdsExportClinicalZone.capture("America/Toronto");
        var index = new CdsExportStagedFileDto[1];
        var valuesMember =
                store.stage(
                        "SOURCE_VALUES",
                        valueOutput ->
                                index[0] =
                                        store.stage(
                                                "SOURCE_INDEX",
                                                indexOutput ->
                                                        new CdsExportNativeCaptureComposer()
                                                                .compose(
                                                                        valueOutput,
                                                                        indexOutput,
                                                                        scopes.keySet(),
                                                                        10_000_000,
                                                                        1L << 30,
                                                                        () -> {},
                                                                        writer -> {
                                                                            for (var entry : scopes.entrySet()) {
                                                                                writer.beginTable(
                                                                                        entry.getKey(),
                                                                                        entry.getValue().columns(),
                                                                                        entry.getValue().rows());
                                                                                write(writer, entry.getKey(), zone);
                                                                                writer.endTable();
                                                                            }
                                                                        })));
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(scopes);
        when(generation.values()).thenReturn(valuesMember);
        when(generation.index()).thenReturn(index[0]);
        var plan = new TreeMap<String, CdsExportNativeCaptureReader.Table>();
        scopes.forEach(
                (table, scope) ->
                        plan.put(
                                table,
                                new CdsExportNativeCaptureReader.Table(
                                        scope.columns(), scope.rows())));
        doAnswer(
                        call -> {
                            try (var valueInput = store.open(valuesMember);
                                    var indexInput = store.open(index[0])) {
                                new CdsExportNativeCaptureReader()
                                        .scan(
                                                valueInput,
                                                indexInput,
                                                plan,
                                                10_000_000,
                                                1L << 30,
                                                () -> {},
                                                call.getArgument(7));
                            }
                            return generation;
                        })
                .when(captures)
                .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        return new CdsExportPatientCaptureDto(
                generation, new CdsExportStagedFileDto("1".repeat(64), "SOURCE_MANIFEST", HASH, 1));
    }

    private void write(
            CdsExportNativeCaptureComposer.Writer writer,
            String table,
            CdsExportClinicalZone.Snapshot zone)
            throws IOException {
        if (table.equals("clinical_timezone")) {
            writer.row(
                    "configuration",
                    Map.of(
                            "sha256",
                            zone.sha256(),
                            "tzdbVersion",
                            zone.tzdbVersion(),
                            "zoneId",
                            zone.zoneId()));
            return;
        }
        if (table.equals("hrm_report_files")) {
            for (var entry : files.entrySet()) {
                var file = entry.getValue();
                writer.row(
                        CdsExportPatientTableSourceReader.key(entry.getKey()),
                        row -> {
                            row.text("hrm_document_id", Integer.toString(entry.getKey()));
                            row.text("report_file", (String) file[0]);
                            row.text("state", file[1].toString());
                            if (file[2] == null) row.text("content", (String) null);
                            else row.binary("content", new ByteArrayInputStream((byte[]) file[2]));
                        });
            }
            return;
        }
        for (var row : rows(table).entrySet()) writer.row(row.getKey(), row.getValue());
    }

    private String assemble() throws Exception {
        var scopes = scopes();
        var source = capture(scopes);
        when(witnesses.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenReturn(
                        new CdsExportNativeRowReceiptsDto(
                                HASH, Map.of("document", 0L, "ctl_document", 0L), 0, 0, Set.of()));
        var index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                HASH, Map.of(), Set.of(), Set.of()));
        doAnswer(
                        call -> {
                            ((CdsExportRetainedValuesService.Work) call.getArgument(8))
                                    .use(
                                            index,
                                            List.of(),
                                            new CdsExportRetainedSectionWriter.Values() {
                                                public Reader text(CdsExportRetainedFieldDto f) {
                                                    throw new AssertionError();
                                                }

                                                public InputStream binary(
                                                        CdsExportRetainedFieldDto f) {
                                                    throw new AssertionError();
                                                }
                                            });
                            return null;
                        })
                .when(values)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), any(), any());
        var hospitalReports = new CdsExportHospitalReportOccurrenceService(captures, stores);
        var occurrences =
                new CdsExportReportOccurrenceService(
                        values, verification, witnesses, captures, stores, null, hospitalReports);
        var bounds = this.bounds;
        receipts.clear();
        attachments.clear();
        expected =
                hospitalReports.identities(
                        lease, patient, PATIENT_KEY, scopes.keySet(), source, bounds, control);
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        HASH,
                        PATIENT_KEY,
                        "2026.11.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        lease.document().command().categories(),
                        emitter -> {
                            emitter.emit(demographics());
                            result =
                                    occurrences.append(
                                            lease,
                                            patient,
                                            PATIENT_KEY,
                                            scopes.keySet(),
                                            source,
                                            new CdsExportStagedFileDto(
                                                    "4".repeat(64), "RETAINED_MANIFEST", HASH, 1),
                                            bounds,
                                            control,
                                            emitter,
                                            new CdsExportClinicalOccurrenceService.Receipts() {
                                                public void accept(
                                                        CdsExportClinicalOccurrenceService.Receipt
                                                                receipt) {
                                                    receipts.add(receipt);
                                                }

                                                public void attachment(
                                                        CdsExportIdentityDto report,
                                                        CdsExportPackageMemberDto member) {
                                                    attachments.put(member.relativePath(), member);
                                                }
                                            });
                        },
                        ignored -> {},
                        64L << 20,
                        () -> {});
        byte[] xml = output.toByteArray();
        var references = new ArrayList<CdsExportReportReferenceDto>();
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(xml),
                        Integer.toString(PATIENT),
                        lease.document().command().categories(),
                        "2026.11.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        references::add,
                        () -> {});
        assertThat(references)
                .extracting(CdsExportReportReferenceDto::relativePath)
                .containsExactlyInAnyOrderElementsOf(attachments.keySet());
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static CdsExportXmlComposer.Section demographics() throws IOException {
        var row = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> row.put(column, null));
        row.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        var demographic =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(PATIENT, row));
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(PATIENT_KEY, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                xml -> new CdsExportDemographicXmlWriter().write(demographic, xml));
    }

    private static List<String> reports(String xml) {
        var result = new ArrayList<String>();
        int at = 0;
        while ((at = xml.indexOf("<Reports>", at)) >= 0) {
            int end = xml.indexOf("</Reports>", at);
            result.add(xml.substring(at, end));
            at = end;
        }
        return result;
    }

    private byte[] attachment(String suffix) throws Exception {
        var member =
                attachments.entrySet().stream()
                        .filter(entry -> entry.getKey().endsWith(suffix))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        try (var input = store.open(member.file())) {
            return input.readAllBytes();
        }
    }

    private static byte[] binaryMessage() throws Exception {
        return CdsExportHospitalReportMapperUnitTest.fixture()
                .replace("<Format>Text</Format>", "<Format>Binary</Format>")
                .replace(
                        "<FileExtensionAndVersion>.txt</FileExtensionAndVersion>",
                        "<FileExtensionAndVersion>.pdf</FileExtensionAndVersion>")
                .replaceFirst(
                        "(?s)<cdsd:TextContent>.*?</cdsd:TextContent>",
                        "<cdsd:Media>" + Base64.getEncoder().encodeToString(PDF) + "</cdsd:Media>")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldExportEveryLinkedHrmReport_asASchemaValidReportsEntry() throws Exception {
        byte[] text = CdsExportHospitalReportMapperUnitTest.fixture().getBytes(StandardCharsets.UTF_8);
        byte[] garbage = "LabUpload truncated before the XML".getBytes(StandardCharsets.UTF_8);
        report(5, CdsExportHospitalReportFileSourceReader.State.READ, text);
        report(6, CdsExportHospitalReportFileSourceReader.State.READ, binaryMessage());
        report(7, CdsExportHospitalReportFileSourceReader.State.MISSING, null);
        report(8, CdsExportHospitalReportFileSourceReader.State.READ, garbage);
        // A link to a report that no longer exists shows nothing in the chart.
        add(CdsExportHospitalReportMapperUnitTest.link(199, 99));
        add(CdsExportHospitalReportMapperUnitTest.comment(201, 5, "0", "Booked a follow-up ultrasound"));
        add(CdsExportHospitalReportMapperUnitTest.comment(202, 6, "1", "Deleted comment"));
        add(CdsExportHospitalReportMapperUnitTest.subClass(301, 5, "1"));
        add(CdsExportHospitalReportMapperUnitTest.subClass(302, 7, "1"));
        add(CdsExportHospitalReportMapperUnitTest.route(401, 5, "101", "1", "2024-01-19 16:45:00"));
        add(CdsExportHospitalReportMapperUnitTest.route(402, 6, "101", "0", null));
        provider("101", "Ada", "Reviewer", "123456");
        String xml = assemble();
        var reports = reports(xml);
        assertThat(reports).hasSize(4);
        assertThat(reports.get(0))
                .contains(
                        "<Media>Download</Media>",
                        "<Format>Text</Format>",
                        "<cdsd:TextContent>SEED-HRM-ATTACHMENT-MARKER",
                        "<Class>Diagnostic Imaging Report</Class>",
                        "<SubClass>X-Ray</SubClass>",
                        "<cdsd:FullDateTime>2024-01-18T10:15:00-05:00</cdsd:FullDateTime>",
                        "<cdsd:LastName>Radiologist</cdsd:LastName>",
                        "<SourceFacility>DEMO</SourceFacility>",
                        "<ReportReviewed>",
                        "<ReviewingOHIPPhysicianId>123456</ReviewingOHIPPhysicianId>",
                        "<cdsd:FullDate>2024-01-19</cdsd:FullDate>",
                        "<SendingFacilityId>DEMO</SendingFacilityId>",
                        "<SendingFacilityReport>DEMO-DI-0001</SendingFacilityReport>",
                        "<AccompanyingMnemonic>CXR</AccompanyingMnemonic>",
                        "<HRMResultStatus>S</HRMResultStatus>",
                        "<MessageUniqueID>demo-hrm-diagnostic-imaging-0001</MessageUniqueID>",
                        "Booked a follow-up ultrasound",
                        "<RecipientName>");
        assertThat(reports.get(1))
                .contains(
                        "<Format>Binary</Format>",
                        "<FileExtensionAndVersion>.pdf</FileExtensionAndVersion>",
                        "<FilePath>Reports/")
                .doesNotContain("<Content>", "Deleted comment");
        assertThat(reports.get(2))
                .contains("<Format>Text</Format>", "could not be read (MISSING)", "ANK3LX")
                .doesNotContain("<Content>", "<FilePath>");
        assertThat(reports.get(3))
                .contains("<Format>Binary</Format>", "<FileExtensionAndVersion>xml</FileExtensionAndVersion>");
        assertThat(attachments).hasSize(2);
        assertThat(attachment(".pdf")).isEqualTo(PDF);
        assertThat(attachment(".xml")).isEqualTo(garbage);
        assertThat(receipts).hasSize(4);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::identity)
                .containsExactlyElementsOf(expected);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::table)
                .containsOnly("HRMDocument");
        assertThat(result.nativeRows()).isEqualTo(4);
        assertThat(result.nativeOnlyRows()).isEqualTo(4);
    }

    @Test
    void shouldExportEveryReportOfAMessageAndACompanion_asTheInventoryExpects() throws Exception {
        // Before: a message holding two reports held the chart
        // (CDS_EXPORT_HRM_REPORT_MULTIPLE_REPORTS_REQUIRES_REVIEW), and so did a comment that
        // takes Notes past 32,000 characters (CDS_EXPORT_HRM_REPORT_METADATA_CAPACITY).
        String twice =
                CdsExportHospitalReportMapperUnitTest.fixture()
                        .replaceFirst(
                                "(?s)(<ReportsReceived>.*?</ReportsReceived>)", "$1\n    $1");
        report(5, CdsExportHospitalReportFileSourceReader.State.READ, twice.getBytes(StandardCharsets.UTF_8));
        String typed = "Discussed at rounds. " + "y".repeat(40_000) + " the end";
        add(CdsExportHospitalReportMapperUnitTest.comment(201, 5, "0", typed));
        provider("101", "Ada", "Reviewer", "123456");
        String xml = assemble();
        var reports = reports(xml);
        // The chart's record of the message, its companion, and the message's second report.
        assertThat(reports).hasSize(3);
        assertThat(reports.get(0))
                .contains("holds 2 reports", "<cdsd:TextContent>SEED-HRM-ATTACHMENT-MARKER")
                .doesNotContain(typed);
        assertThat(reports.get(1))
                .contains("<SubClass>CARLOS context of a report</SubClass>", typed);
        assertThat(reports.get(2))
                .contains("Report 2 of 2 of one stored HRM message", "SEED-HRM-ATTACHMENT-MARKER");
        assertThat(expected).hasSize(3);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::identity)
                .containsExactlyInAnyOrderElementsOf(expected);
        // The comment's fields are accounted in the companion's Content.
        assertThat(receipts.get(1).fields().values())
                .isNotEmpty()
                .allMatch(value -> value.destination().equals("Reports/Content"));
        assertThat(result.nativeRows()).isEqualTo(3);
    }

    @Test
    void shouldHoldOnlyThisPatient_forALegacySpellingOfTheHrmPatientLink() throws Exception {
        report(5, CdsExportHospitalReportFileSourceReader.State.MISSING, null);
        var link = rows("HRMDocumentToDemographic").firstEntry().getValue();
        link.put("demographicNo", "0" + PATIENT);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW");
        assertThat(attachments).isEmpty();
    }

    @Test
    void shouldEmitNoHrmReport_forAChartWithoutHrmLinks() throws Exception {
        provider("101", "Ada", "Reviewer", "123456");
        String xml = assemble();
        assertThat(reports(xml)).isEmpty();
        assertThat(receipts).isEmpty();
        assertThat(expected).isEmpty();
    }

    /** The chart of the first test: four reports, their children and a link to no report. */
    private void mixedChart() throws Exception {
        byte[] text = CdsExportHospitalReportMapperUnitTest.fixture().getBytes(StandardCharsets.UTF_8);
        report(5, CdsExportHospitalReportFileSourceReader.State.READ, text);
        report(6, CdsExportHospitalReportFileSourceReader.State.READ, binaryMessage());
        report(7, CdsExportHospitalReportFileSourceReader.State.MISSING, null);
        report(
                8,
                CdsExportHospitalReportFileSourceReader.State.READ,
                "LabUpload truncated before the XML".getBytes(StandardCharsets.UTF_8));
        report(12, CdsExportHospitalReportFileSourceReader.State.MISSING, null);
        add(CdsExportHospitalReportMapperUnitTest.link(199, 99));
        add(CdsExportHospitalReportMapperUnitTest.comment(201, 5, "0", "Booked a follow-up ultrasound"));
        add(CdsExportHospitalReportMapperUnitTest.comment(202, 6, "1", "Deleted comment"));
        add(CdsExportHospitalReportMapperUnitTest.comment(203, 12, "0", "Seen"));
        add(CdsExportHospitalReportMapperUnitTest.subClass(301, 5, "1"));
        add(CdsExportHospitalReportMapperUnitTest.subClass(302, 7, "1"));
        add(CdsExportHospitalReportMapperUnitTest.route(401, 5, "101", "1", "2024-01-19 16:45:00"));
        add(CdsExportHospitalReportMapperUnitTest.route(402, 6, "101", "0", null));
        provider("101", "Ada", "Reviewer", "123456");
    }

    private CdsExportClinicalOccurrenceService.Bounds window(int rows, long bytes) {
        return bounds.withWindow(rows, bytes);
    }

    @Test
    void shouldEmitTheSameBytesAndReceipts_whenAChartIsReadInSmallWindows() throws Exception {
        mixedChart();
        String whole = assemble();
        var wholeReceipts = List.copyOf(receipts);
        var wholeExpected = List.copyOf(expected);
        var wholeAttachments = new TreeMap<String, byte[]>();
        for (var entry : attachments.entrySet())
            try (var input = store.open(entry.getValue().file())) {
                wholeAttachments.put(entry.getKey(), input.readAllBytes());
            }
        assertThat(reports(whole)).hasSize(5);
        // One report a window, two a window, and a byte limit that makes every window of more
        // than one report too large (each is read again smaller).
        for (var small : List.of(window(1, 64L << 20), window(2, 64L << 20), window(4, 1500))) {
            bounds = small;
            String windowed = assemble();
            assertThat(windowed).isEqualTo(whole);
            assertThat(receipts)
                    .usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyElementsOf(wholeReceipts);
            assertThat(expected).containsExactlyInAnyOrderElementsOf(wholeExpected);
            assertThat(attachments.keySet()).isEqualTo(wholeAttachments.keySet());
            for (var entry : attachments.entrySet())
                try (var input = store.open(entry.getValue().file())) {
                    assertThat(input.readAllBytes()).isEqualTo(wholeAttachments.get(entry.getKey()));
                }
        }
    }

    @Test
    void shouldExportEveryHrmReport_whenAChartHoldsMoreThanOneWindowOfThem() throws Exception {
        // Before: 8,193 stored reports stopped the job with RESOURCE_LIMIT (maxNativeRows), and
        // the captured file descriptors failed it at the same number.
        int count = CdsExportClinicalOccurrenceService.Bounds.WINDOW_ROWS + 1;
        for (int id = 1; id <= count; id++)
            report(id, CdsExportHospitalReportFileSourceReader.State.MISSING, null);
        add(CdsExportHospitalReportMapperUnitTest.comment(900_001, count, "0", "The last report"));
        provider("101", "Ada", "Reviewer", "123456");
        String xml = assemble();
        assertThat(reports(xml)).hasSize(count);
        assertThat(reports(xml).getLast()).contains("The last report");
        assertThat(receipts).hasSize(count);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::nativeId)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, count)
                                .mapToObj(Integer::toString)
                                .toList());
        assertThat(expected).hasSize(count);
        assertThat(result.nativeRows()).isEqualTo(count);
    }

    @Test
    void shouldRefuseAChildOfNoReport_whenAChartIsReadInWindows() throws Exception {
        mixedChart();
        add(CdsExportHospitalReportMapperUnitTest.comment(299, 77, "0", "Orphan"));
        bounds = window(2, 64L << 20);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_HRM_REPORT_OCCURRENCE_INVALID");
        assertThat(attachments).isEmpty();
    }

    @Test
    void shouldRefuseAReportWithoutALink_whenAChartIsReadInWindows() throws Exception {
        mixedChart();
        rows("HRMDocumentToDemographic")
                .values()
                .removeIf(link -> "12".equals(link.get("hrmDocumentId")));
        bounds = window(2, 64L << 20);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_HRM_REPORT_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAFileOfNoReportAndAReportWithoutAFile_whenAChartIsReadInWindows()
            throws Exception {
        mixedChart();
        files.put(77, files.remove(12));
        bounds = window(2, 64L << 20);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(IOException.class)
                .hasMessageMatching("CDS_EXPORT_(HRM_REPORT_OCCURRENCE|HOSPITAL_REPORT_CAPTURE)_INVALID");
    }

    @Test
    void shouldHoldThePatient_forALinkSpelledOtherwiseInAWindowThatDoesNotKeepIt() throws Exception {
        mixedChart();
        rows("HRMDocumentToDemographic").lastEntry().getValue().put("hrmDocumentId", "012");
        bounds = window(1, 64L << 20);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW");
        assertThat(attachments).isEmpty();
    }
}
