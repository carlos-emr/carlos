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
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.documentManager.DocumentLink;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives the native Reports loop end to end over a real encrypted capture: documents shaped as
 * CARLOS stores them (a scanned PDF from the document manager, an Add HTML and an Add Link
 * document whose content is the HTML kept in {@code document.docxml}, a soft-deleted document,
 * a deleted patient link and a scanner file with no extension) are captured, authenticated and
 * assembled into schema-valid CDS 5.2 Reports with their attachments.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportNativeReportAssemblyUnitTest {
    private static final String HASH = "a".repeat(64), PATIENT_KEY = "b".repeat(64);
    private static final String RUN = "33333333-3333-4333-8333-333333333333";
    private static final int PATIENT = 27;
    private static final byte[] PDF =
            "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII);

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
    private final List<Doc> documents = new ArrayList<>();
    /** Captured reviewer, route and provider rows by capture table, each in record-key order. */
    private final Map<String, TreeMap<String, Map<String, String>>> reviewerRows = new TreeMap<>();
    private CdsExportStagingService store, service;
    private CdsExportStagedFileDto valuesMember;
    private CdsExportLeaseDto lease;
    private Set<String> importedDocuments = Set.of();
    private boolean tamperAfterScans;
    private CdsExportClinicalOccurrenceService.Result result;

    /** One captured document: its row, its patient link and, when the capture read one, its file. */
    private record Doc(int id, Map<String, String> document, Map<String, String> link, byte[] file) {}

    @BeforeEach
    void setup() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 41);
        store =
                new CdsExportStagingService(
                        root, RUN, 1, new SecretKeySpec(key, "AES"), 64L << 20, 0, () -> {});
        service = spy(store);
        when(stores.openExisting(any(), anyLong(), any(), any())).thenReturn(service);
        when(patient.patient()).thenReturn(PATIENT);
        var command =
                new CdsExportCommand(
                        "report-assembly",
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

    /** A document uploaded or scanned through the document manager (EDocUtil.addDocumentSQL). */
    private static Map<String, String> uploaded(int id, String filename, String contentType) {
        var row = new TreeMap<String, String>();
        Plan.DOCUMENTS.columns().forEach(column -> row.put(column, null));
        row.put("document_no", Integer.toString(id));
        row.put("status", "A");
        row.put("docfilename", filename);
        row.put("contenttype", contentType);
        row.put("docxml", "");
        row.put("doctype", "consult");
        row.put("docdesc", "Consult letter " + id);
        row.put("doccreator", "999998");
        row.put("responsible", "");
        row.put("source", "");
        row.put("sourceFacility", "");
        row.put("public1", "0");
        row.put("abnormal", "0");
        row.put("restrictToProgram", "0");
        row.put("appointment_no", "0");
        row.put("number_of_pages", "1");
        row.put("observationdate", "2026-03-01");
        row.put("contentdatetime", "2026-03-05 10:14:00");
        row.put("updatedatetime", "2026-03-05 10:14:00");
        return row;
    }

    /** An Add HTML or Add Link document (AddEditHtml2Action): status H, content in docxml, no file. */
    private static Map<String, String> storedHtml(int id, String kind, String html) {
        var row = uploaded(id, "20260305101500" + kind, "text/html");
        row.put("status", "H");
        row.put("docxml", html);
        row.put("doctype", "others");
        row.put("docdesc", kind.equals("link") ? "Lab portal" + DocumentLink.DESCRIPTION_SUFFIX : "Discharge summary");
        return row;
    }

    private static Map<String, String> link(int id, String status) {
        var row = new TreeMap<String, String>();
        row.put("document_no", Integer.toString(id));
        row.put("module", "demographic");
        row.put("module_id", Integer.toString(PATIENT));
        row.put("status", status);
        return row;
    }

    private void add(int id, Map<String, String> document, String linkStatus, byte[] file) {
        documents.add(new Doc(id, document, link(id, linkStatus), file));
    }

    private TreeMap<String, Map<String, String>> rows(String table) {
        return reviewerRows.computeIfAbsent(table, ignored -> new TreeMap<>());
    }

    private void provider(String number, String first, String last, String ohip) {
        var row = new TreeMap<String, String>();
        CdsExportProviderRowDto.COLUMNS.forEach(column -> row.put(column, null));
        row.put("provider_no", number);
        row.put("first_name", first);
        row.put("last_name", last);
        row.put("ohip_no", ohip);
        rows("provider").put(CdsExportPublicProviderSourceReader.key(number), row);
    }

    /** An inbox route of a document (lab_type DOC), as the document inbox writes it. */
    private void route(int id, int document, String provider, String status, String time, String comment) {
        var row = new TreeMap<String, String>();
        Plan.DOCUMENT_ROUTES.columns().forEach(column -> row.put(column, null));
        row.put("id", Integer.toString(id));
        row.put("lab_no", Integer.toString(document));
        row.put("lab_type", "DOC");
        row.put("provider_no", provider);
        row.put("status", status);
        // A TIMESTAMP column: captured as the instant of this Toronto clinic time.
        row.put("timestamp", CdsExportLaboratoryReviewerMappingUnitTest.instant(time));
        row.put("comment", comment);
        rows(CdsExportDocumentRouteSourceReader.TABLE)
                .put(CdsExportPatientTableSourceReader.key(id), row);
    }

    private void review(int id, int document, String provider, String time) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("document_no", Integer.toString(document));
        row.put("provider_no", provider);
        row.put("date_reviewed", time);
        rows("document_review").put(CdsExportPatientTableSourceReader.key(id), row);
    }

    private void extraReviewer(int id, int document, String provider, String time) {
        var row = new TreeMap<String, String>();
        row.put("id", Integer.toString(id));
        row.put("documentNo", Integer.toString(document));
        row.put("reviewerProviderNo", provider);
        row.put("reviewDateTime", CdsExportLaboratoryReviewerMappingUnitTest.instant(time));
        rows("DocumentExtraReviewer").put(CdsExportPatientTableSourceReader.key(id), row);
    }

    private Map<String, CdsExportCapturedTableDto> scopes() {
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        long files = documents.stream().filter(doc -> doc.file() != null).count();
        scopes.put(
                "document",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENTS.columns()), documents.size(), HASH));
        scopes.put(
                "ctl_document",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENT_LINKS.columns()), documents.size(), HASH));
        scopes.put(
                CdsExportDocumentFileSourceReader.TABLE,
                new CdsExportCapturedTableDto(
                        Set.of("document_no", "filename", "content"), files, HASH));
        scopes.put("cds_import_file", new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        scopes.put(
                "cds_import_file_reference", new CdsExportCapturedTableDto(Set.of("id"), 0, HASH));
        scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DEMOGRAPHIC_MERGES.columns()), 0, HASH));
        scopes.put(
                "provider",
                new CdsExportCapturedTableDto(
                        CdsExportProviderRowDto.COLUMNS, rows("provider").size(), HASH));
        scopes.put(
                CdsExportDocumentRouteSourceReader.TABLE,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENT_ROUTES.columns()),
                        rows(CdsExportDocumentRouteSourceReader.TABLE).size(),
                        HASH));
        scopes.put(
                "document_review",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENT_REVIEWS.columns()),
                        rows("document_review").size(),
                        HASH));
        scopes.put(
                "DocumentExtraReviewer",
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DOCUMENT_EXTRA_REVIEWERS.columns()),
                        rows("DocumentExtraReviewer").size(),
                        HASH));
        scopes.put(
                "clinical_timezone",
                new CdsExportCapturedTableDto(Set.of("sha256", "tzdbVersion", "zoneId"), 1, HASH));
        return scopes;
    }

    private CdsExportPatientCaptureDto capture(Map<String, CdsExportCapturedTableDto> scopes)
            throws Exception {
        var zone = CdsExportClinicalZone.capture("America/Toronto");
        var index = new CdsExportStagedFileDto[1];
        var sorted = new ArrayList<>(documents);
        sorted.sort(Comparator.comparing(doc -> CdsExportPatientTableSourceReader.key(doc.id())));
        valuesMember =
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
                                                                        100_000,
                                                                        32L << 20,
                                                                        () -> {},
                                                                        writer -> {
                                                                            for (var entry :
                                                                                    scopes
                                                                                            .entrySet()) {
                                                                                writer.beginTable(
                                                                                        entry
                                                                                                .getKey(),
                                                                                        entry.getValue()
                                                                                                .columns(),
                                                                                        entry.getValue()
                                                                                                .rows());
                                                                                rows(
                                                                                        writer,
                                                                                        entry
                                                                                                .getKey(),
                                                                                        sorted,
                                                                                        zone);
                                                                                for (var row :
                                                                                        rows(
                                                                                                        entry
                                                                                                                .getKey())
                                                                                                .entrySet())
                                                                                    writer.row(
                                                                                            row
                                                                                                    .getKey(),
                                                                                            row
                                                                                                    .getValue());
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
        // The real capture reader replays the authenticated members; this store is not the
        // service's spied store, so only the occurrence service's own reads are counted.
        doAnswer(
                        call -> {
                            try (var valueInput = store.open(valuesMember);
                                    var indexInput = store.open(index[0])) {
                                new CdsExportNativeCaptureReader()
                                        .scan(
                                                valueInput,
                                                indexInput,
                                                plan,
                                                100_000,
                                                32L << 20,
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

    private static void rows(
            CdsExportNativeCaptureComposer.Writer writer,
            String table,
            List<Doc> sorted,
            CdsExportClinicalZone.Snapshot zone)
            throws IOException {
        switch (table) {
            case "document" -> {
                for (var doc : sorted)
                    writer.row(CdsExportPatientTableSourceReader.key(doc.id()), doc.document());
            }
            case "ctl_document" -> {
                for (var doc : sorted)
                    writer.row(CdsExportPatientTableSourceReader.key(doc.id()), doc.link());
            }
            case CdsExportDocumentFileSourceReader.TABLE -> {
                for (var doc : sorted)
                    if (doc.file() != null)
                        writer.row(
                                CdsExportPatientTableSourceReader.key(doc.id()),
                                row -> {
                                    row.text("document_no", Integer.toString(doc.id()));
                                    row.text("filename", doc.document().get("docfilename"));
                                    row.binary("content", new ByteArrayInputStream(doc.file()));
                                });
            }
            case "clinical_timezone" ->
                    writer.row(
                            "configuration",
                            Map.of(
                                    "sha256",
                                    zone.sha256(),
                                    "tzdbVersion",
                                    zone.tzdbVersion(),
                                    "zoneId",
                                    zone.zoneId()));
            default -> {
                /* Empty supporting tables. */
            }
        }
    }

    private static CdsExportNativeRowReceiptDto witness(
            String table, String id, String recordKey, Map<String, String> values) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String value = entry.getValue();
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    value.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        var state = new CdsNativeRowState(table, id, columns, cells);
        return new CdsExportNativeRowReceiptDto(table, id, recordKey, state.digest(), state);
    }

    private String assemble() throws Exception {
        var scopes = scopes();
        var source = capture(scopes);
        var receiptRows = new ArrayList<CdsExportNativeRowReceiptDto>();
        for (var doc : documents) {
            String key = CdsExportPatientTableSourceReader.key(doc.id());
            receiptRows.add(witness("document", Integer.toString(doc.id()), key, doc.document()));
            receiptRows.add(
                    witness("ctl_document", PATIENT + "_" + doc.id(), key, doc.link()));
        }
        when(witnesses.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var row : receiptRows)
                                ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                        .accept(row);
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH,
                                    Map.of(
                                            "document",
                                            (long) documents.size(),
                                            "ctl_document",
                                            (long) documents.size()),
                                    receiptRows.size(),
                                    0,
                                    Set.of());
                        });
        var references = new HashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        var applied = new HashSet<String>();
        if (!importedDocuments.isEmpty()) {
            String address = UUID.randomUUID() + ":" + "d".repeat(64);
            applied.add(address);
            references.put(
                    address,
                    importedDocuments.stream()
                            .map(id -> new CdsExportRetainedIndexDto.Reference("document", id, HASH))
                            .toList());
        }
        var index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        applied,
                        references,
                        Map.of());
        when(verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(
                                HASH, Map.of(), Set.of(), Set.of()));
        doAnswer(
                        call -> {
                            if (tamperAfterScans) {
                                // The values member changes on disk after every capture scan
                                // has authenticated it; no attachment may be published from it.
                                Path member = root.resolve(valuesMember.memberId() + ".stage");
                                byte[] encrypted = Files.readAllBytes(member);
                                encrypted[encrypted.length - 20] ^= 1;
                                Files.write(member, encrypted);
                            }
                            ((CdsExportRetainedValuesService.Work) call.getArgument(10))
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
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
        var occurrences =
                new CdsExportReportOccurrenceService(
                        values, verification, witnesses, captures, stores);
        var output = new ByteArrayOutputStream();
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        100_000, 32L << 20, 1000, 32L << 20, 100, 1000);
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
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(xml),
                        Integer.toString(PATIENT),
                        lease.document().command().categories(),
                        "2026.11.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
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

    private byte[] attachment(CdsExportPackageMemberDto member) throws Exception {
        try (var input = store.open(member.file())) {
            return input.readAllBytes();
        }
    }

    @Test
    void shouldExportAScannedPdfAndStoredHtmlDocuments_asTheChartShowsThem() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        String html = "<p>Discharge summary ü 😀</p>\r\n<p>Second line &amp; more</p>";
        add(92, storedHtml(92, "html", html), "H", null);
        String redirect = DocumentLink.toRedirectHtml("https://portal.example.org/report?id=1&x=2");
        add(93, storedHtml(93, "link", redirect), "H", null);
        String xml = assemble();
        var reports = reports(xml);
        assertThat(reports).hasSize(3);
        // Nothing in the document records how it arrived (scanned, faxed or uploaded): no Media.
        assertThat(xml).doesNotContain("<Media>");
        assertThat(reports.get(0))
                .contains(
                        "<Format>Binary</Format>",
                        "<FileExtensionAndVersion>pdf</FileExtensionAndVersion>",
                        "<FilePath>Reports/")
                .doesNotContain("<Content>");
        assertThat(reports.get(1))
                .contains(
                        "<Format>Text</Format>",
                        "<FileExtensionAndVersion>html</FileExtensionAndVersion>",
                        "<cdsd:TextContent>&lt;p&gt;Discharge summary ü 😀&lt;/p&gt;&#13;\n")
                .doesNotContain("<FilePath>");
        assertThat(reports.get(2))
                .contains("<Format>Text</Format>", "https://portal.example.org/report?id=1");
        assertThat(attachments).hasSize(1);
        assertThat(attachment(attachments.values().iterator().next())).isEqualTo(PDF);
        assertThat(receipts).hasSize(3);
        assertThat(result.nativeRows()).isEqualTo(3);
        assertThat(result.nativeOnlyRows()).isEqualTo(3);
        // The stored HTML is the report's content, so it is not repeated in its source context.
        var htmlField =
                receipts.get(1).fields().entrySet().stream()
                        .filter(entry -> entry.getKey().column().equals("docxml"))
                        .findFirst()
                        .orElseThrow();
        assertThat(htmlField.getValue().destination()).isEqualTo("Reports/Content");
    }

    @Test
    void shouldLeaveSoftDeletedDocumentsAndDeletedLinksOut_withEveryFieldAccounted()
            throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        var deleted = uploaded(94, "20260101090000old-letter.pdf", "application/pdf");
        deleted.put("status", "D");
        add(94, deleted, "A", null);
        add(95, uploaded(95, "20260101090100moved-letter.pdf", "application/pdf"), "D", null);
        String xml = assemble();
        assertThat(reports(xml)).hasSize(1);
        assertThat(xml).doesNotContain("old-letter", "moved-letter");
        assertThat(receipts).hasSize(1);
        assertThat(attachments).hasSize(1);
        assertThat(result.nativeRows()).isEqualTo(1);
    }

    @Test
    void shouldExportEveryReviewOfADocument_asReportReviewedAfterItsFirstReviewer()
            throws Exception {
        var document = uploaded(91, "20260305101400consult-letter.pdf", "application/pdf");
        document.put("reviewer", "101");
        document.put("reviewdatetime", "2026-03-06 09:00:00");
        add(91, document, "A", PDF);
        provider("101", "Ada", "First", "123456");
        provider("102", "Bea", "Extra", "234567");
        provider("103", "Cy", "Inbox", "12345");
        // The first reviewer again on the same day repeats the first element and is not written.
        review(501, 91, "101", "2026-03-06 15:30:00");
        extraReviewer(601, 91, "102", "2026-03-07 10:15:00");
        route(701, 91, "103", "A", "2026-03-08 11:45:00", "Booked a follow-up");
        route(702, 91, "0", "N", "2026-03-05 10:14:00", "");
        String xml = assemble();
        var report = reports(xml).getFirst();
        assertThat(report.split("<ReportReviewed>", -1)).hasSize(4);
        assertThat(report)
                .contains(
                        "<cdsd:FirstName>Ada</cdsd:FirstName>",
                        "<ReviewingOHIPPhysicianId>234567</ReviewingOHIPPhysicianId>",
                        "<cdsd:FullDate>2026-03-07</cdsd:FullDate>",
                        "<cdsd:LastName>Inbox</cdsd:LastName>",
                        "<cdsd:FullDate>2026-03-08</cdsd:FullDate>",
                        // Exact times (a DATETIME as stored, a TIMESTAMP as its instant) and the
                        // inbox comment are kept in the source context.
                        "2026-03-06 15:30:00",
                        CdsExportLaboratoryReviewerMappingUnitTest.instant("2026-03-07 10:15:00"),
                        "Booked a follow-up")
                .doesNotContain("<ReviewingOHIPPhysicianId>12345<");
        assertThat(receipts).hasSize(1);
        var fields = receipts.getFirst().fields();
        assertThat(fields.values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        // Every reviewer, route and provider row is accounted in the report's receipt.
        assertThat(fields.keySet())
                .extracting(CdsExportNativeFieldKeyDto::table)
                .contains(
                        "document_review",
                        "DocumentExtraReviewer",
                        CdsExportDocumentRouteSourceReader.TABLE,
                        "provider");
        assertThat(
                        fields.entrySet().stream()
                                .filter(
                                        e ->
                                                e.getKey().table()
                                                                .equals(
                                                                        CdsExportDocumentRouteSourceReader
                                                                                .TABLE)
                                                        && e.getKey().column().equals("status"))
                                .map(e -> e.getValue().rule()))
                .containsExactlyInAnyOrder(
                        "ACKNOWLEDGED_INBOX_STATUS", "UNCLAIMED_INBOX_ROUTING_PLACEHOLDER");
    }

    @Test
    void shouldAccountTheReviewersOfARemovedDocument_withoutExportingThem() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        var deleted = uploaded(94, "20260101090000old-letter.pdf", "application/pdf");
        deleted.put("status", "D");
        add(94, deleted, "A", null);
        provider("102", "Bea", "Extra", "234567");
        extraReviewer(601, 94, "102", "2026-03-07 10:15:00");
        review(501, 94, "102", "2026-03-07 10:20:00");
        route(701, 94, "102", "A", "2026-03-08 11:45:00", "Removed by mistake?");
        String xml = assemble();
        assertThat(reports(xml)).hasSize(1);
        assertThat(xml).doesNotContain("Bea", "Removed by mistake?", "old-letter");
        assertThat(receipts).hasSize(1);
    }

    @Test
    void shouldHoldOnlyThisPatient_forAReviewByAProviderWithNoRecord() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        extraReviewer(601, 91, "777", "2026-03-07 10:15:00");
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        assertThat(attachments).isEmpty();
    }

    @Test
    void shouldDeclareTheStoredContentType_whenTheScannerFileHasNoExtension() throws Exception {
        add(96, uploaded(96, "20260305101600scan0001", "application/pdf"), "A", PDF);
        String xml = assemble();
        assertThat(xml)
                .contains("<FileExtensionAndVersion>pdf</FileExtensionAndVersion>");
        assertThat(attachments.keySet()).singleElement().asString().endsWith(".pdf");
    }

    @Test
    void shouldExportAnUnrecognisedFile_asOpaqueBinaryInsteadOfHoldingThePatient()
            throws Exception {
        byte[] heic = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        add(97, uploaded(97, "20260305101700photo.heic", "image/heic"), "A", heic);
        String xml = assemble();
        assertThat(xml)
                .contains(
                        "<Format>Binary</Format>",
                        "<FileExtensionAndVersion>bin</FileExtensionAndVersion>",
                        "photo.heic");
        assertThat(attachments.keySet()).singleElement().asString().endsWith(".bin");
    }

    @Test
    void shouldAccountEveryCapturedFieldOfEachExportedReport_inItsOwnReceipt() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        add(92, storedHtml(92, "html", "<p>Discharge summary</p>"), "H", null);
        var deleted = uploaded(94, "20260101090000old-letter.pdf", "application/pdf");
        deleted.put("status", "D");
        add(94, deleted, "A", null);
        assemble();
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::nativeId)
                .containsExactly("91", "92");
        for (var receipt : receipts) {
            assertThat(receipt.identity())
                    .isEqualTo(
                            new CdsExportIdentityDto(
                                    PATIENT_KEY,
                                    CdsCategory.REPORTS,
                                    CdsImportKeys.hash(
                                            "carlos-native-occurrence-v1",
                                            "document",
                                            receipt.nativeId())));
            assertThat(receipt.fields().values())
                    .noneMatch(
                            field ->
                                    field.outcome()
                                            == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
            assertThat(receipt.retainedFields()).isEmpty();
            assertThat(receipt.replacedFields()).isEmpty();
        }
        // Document and link rows of each report, plus the stored file of the scanned PDF only.
        var files =
                receipts.get(0).fields().entrySet().stream()
                        .filter(e -> e.getKey().table().equals(CdsExportDocumentFileSourceReader.TABLE))
                        .toList();
        assertThat(files).hasSize(3);
        assertThat(
                        files.stream()
                                .filter(e -> e.getKey().column().equals("content"))
                                .map(e -> e.getValue().destination()))
                .containsExactly("Reports/FilePath");
        assertThat(receipts.get(1).fields().keySet())
                .noneMatch(key -> key.table().equals(CdsExportDocumentFileSourceReader.TABLE));
        assertThat(result.nativeRows()).isEqualTo(2);
        assertThat(result.importedRows()).isZero();
        assertThat(result.nativeOnlyRows()).isEqualTo(2);
        assertThat(result.retainedSourceFields()).isZero();
    }

    @Test
    void shouldHoldOnlyThisPatient_forAnEmptyFile() throws Exception {
        add(98, uploaded(98, "20260305101800empty.pdf", "application/pdf"), "A", new byte[0]);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_FILE_EMPTY");
        assertThat(attachments).isEmpty();
    }

    @Test
    void shouldHoldOnlyThisPatient_forAnHtmlDocumentWithNoStoredHtml() throws Exception {
        add(99, storedHtml(99, "html", ""), "H", null);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STATUS_REQUIRES_REVIEW");
    }

    @Test
    void shouldHoldOnlyThisPatient_forBlankStoredHtml() throws Exception {
        add(99, storedHtml(99, "html", " \r\n\t"), "H", null);
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_STORED_HTML_REQUIRES_REVIEW");
    }

    @Test
    void shouldReadEveryAttachment_throughOneAuthenticatedPassOfThePatientsValues()
            throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        byte[] second = Arrays.copyOf(PDF, PDF.length + 70_000);
        add(100, uploaded(100, "20260305101900second.pdf", "application/pdf"), "A", second);
        assemble();
        assertThat(attachments).hasSize(2);
        verify(service, never()).open(valuesMember);
        verify(service, times(1)).verifiedRanges(valuesMember);
        var copies = new ArrayList<byte[]>();
        for (var member : attachments.values()) copies.add(attachment(member));
        assertThat(copies).anySatisfy(bytes -> assertThat(bytes).isEqualTo(second));
    }

    @Test
    void shouldPublishNoAttachment_fromValuesChangedAfterTheirScans() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        tamperAfterScans = true;
        assertThatThrownBy(this::assemble).isInstanceOf(IOException.class);
        assertThat(attachments).isEmpty();
        try (var members = Files.list(root)) {
            // Only the capture's own value and index members remain; nothing was staged.
            assertThat(members.count()).isEqualTo(2);
        }
    }

    @Test
    void shouldNotReExportAnImportedDocument_asANativeReport() throws Exception {
        add(91, uploaded(91, "20260305101400consult-letter.pdf", "application/pdf"), "A", PDF);
        importedDocuments = Set.of("91");
        assertThatThrownBy(this::assemble)
                .isInstanceOf(CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(attachments).isEmpty();
    }
}
