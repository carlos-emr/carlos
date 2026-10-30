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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsBase64ValueParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native documents retain each supplied representation and category-local source metadata. */
@Service
public class CdsReportImportService implements CdsCategoryImportService {
    private record Representation(
            String variant,
            String extension,
            String mime,
            String digest,
            long length,
            CdsDocumentPublicationService.Source source,
            boolean mediaReviewRequired,
            List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto>
                    diagnostics) {}

    private final CdsFieldParser fields;
    private final CdsSnapshotService snapshots;
    private final CdsDocumentPublicationService publication;
    private final CdsClinicalNormalizationService normalization;
    private final CdsImportConfigurationService configuration;
    private final CdsNativePreparationService preparation;
    private final CdsImportLedgerService ledger;
    private final CdsReportMediaService mediaInspection;
    private final DocumentDao documentDao;
    private final CtlDocumentDao ctlDocumentDao;

    public CdsReportImportService(
            CdsFieldParser fields,
            CdsSnapshotService snapshots,
            CdsDocumentPublicationService publication,
            CdsClinicalNormalizationService normalization,
            CdsImportConfigurationService configuration,
            CdsNativePreparationService preparation,
            CdsImportLedgerService ledger,
            CdsReportMediaService mediaInspection,
            DocumentDao documentDao,
            CtlDocumentDao ctlDocumentDao) {
        this.fields = fields;
        this.snapshots = snapshots;
        this.publication = publication;
        this.normalization = normalization;
        this.configuration = configuration;
        this.preparation = preparation;
        this.ledger = ledger;
        this.mediaInspection = mediaInspection;
        this.documentDao = documentDao;
        this.ctlDocumentDao = ctlDocumentDao;
    }

    @Override
    public Set<String> sections() {
        return Set.of("Reports");
    }

    @Override
    public void validate(
            CdsRecordDto record,
            CdsImportCommand command,
            CdsSnapshotDto snapshot,
            String xmlRelative)
            throws IOException {
        representations(record, snapshot, xmlRelative);
        // Valid source timing remains authoritative even when an optional native clock
        // cannot represent it. The writer records that source-only disposition visibly.
        normalization.date(record, "EventDateTime", command);
        normalization.date(record, "ReceivedDateTime", command);
        normalization.date(record, "SentDateTime", command);
    }

    @Override
    public void prepare(
            String run,
            long epoch,
            String unit,
            CdsRecordDto record,
            CdsImportCommand command,
            CdsSnapshotDto snapshot,
            String xmlRelative)
            throws IOException {
        List<CdsReportNativeDto.Variant> variants = new ArrayList<>(2);
        for (Representation representation : representations(record, snapshot, xmlRelative)) {
            publication.publish(
                    run,
                    epoch,
                    unit,
                    representation.variant(),
                    representation.extension(),
                    representation.digest(),
                    representation.length(),
                    representation.source());
            variants.add(
                    new CdsReportNativeDto.Variant(
                            representation.variant(),
                            representation.extension(),
                            representation.mime(),
                            representation.digest(),
                            representation.length(),
                            representation.mediaReviewRequired(),
                            representation.diagnostics()));
        }
        var values =
                new LinkedHashMap<>(
                        fields.selectedTexts(
                                record,
                                Map.of(
                                        "Class",
                                        50,
                                        "SubClass",
                                        60,
                                        "SourceFacility",
                                        120,
                                        "Format",
                                        50,
                                        "SourceAuthorPhysician/AuthorFreeText",
                                        120,
                                        "SourceAuthorPhysician/AuthorName/FirstName",
                                        50,
                                        "SourceAuthorPhysician/AuthorName/LastName",
                                        50)));
        String author = values.get("SourceAuthorPhysician/AuthorFreeText");
        String first = values.get("SourceAuthorPhysician/AuthorName/FirstName"),
                last = values.get("SourceAuthorPhysician/AuthorName/LastName");
        if (author != null && (first != null || last != null))
            throw new CdsImportException("REPORT_AUTHOR_CHOICE_CONFLICT");
        if (author == null && (first != null || last != null)) {
            author =
                    first == null || first.isEmpty()
                            ? last
                            : last == null || last.isEmpty() ? first : first + " " + last;
            values.put("SourceAuthorFormat", "structured");
        }
        if (author != null) values.put("NativeSourceAuthor", author);
        if ("Binary".equals(values.get("Format"))) {
            fields.forEachField(
                    record,
                    field -> {
                        if (CdsFieldParser.unindexed(field.path()).equals("Content/TextContent"))
                            values.put("ContentEncoding", "base64-from-text");
                    });
        }
        preparation.store(
                run,
                epoch,
                unit,
                record.effectiveSha256(),
                command,
                new CdsReportNativeDto(
                        values,
                        variants,
                        normalization.date(record, "EventDateTime", command),
                        normalization.date(record, "ReceivedDateTime", command),
                        normalization.date(record, "SentDateTime", command)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(
            String run,
            long epoch,
            String unit,
            int demographicNo,
            CdsRecordDto record,
            CdsImportCommand command)
            throws IOException {
        CdsImportUnitService.requireActive(run, epoch, unit);
        CdsReportNativeDto prepared =
                preparation.load(
                        run,
                        epoch,
                        unit,
                        record.effectiveSha256(),
                        command,
                        CdsReportNativeDto.class);
        Map<String, String> values = prepared.values();
        List<CdsReportNativeDto.Variant> variants = prepared.variants();
        dateNotice(run, epoch, unit, "EventDateTime", prepared.event());
        dateNotice(run, epoch, unit, "ReceivedDateTime", prepared.received());
        dateNotice(run, epoch, unit, "SentDateTime", prepared.sent());
        if ("base64-from-text".equals(values.get("ContentEncoding"))) {
            notice(run, epoch, unit, "CDS_REPORT_BASE64_TEXT_DECODED", "Content/TextContent");
        }
        if ("structured".equals(values.get("SourceAuthorFormat")))
            notice(
                    run,
                    epoch,
                    unit,
                    "CDS_REPORT_SOURCE_AUTHOR_NAME_FORMATTED",
                    "SourceAuthorPhysician/AuthorName");
        Date contentTime = timestamp(prepared.event(), run, epoch, unit, "EventDateTime");
        Date observationDay = calendarDate(prepared.event(), run, epoch, unit, "EventDateTime");
        Date receivedDay = calendarDate(prepared.received(), run, epoch, unit, "ReceivedDateTime");
        Date sentTime = timestamp(prepared.sent(), run, epoch, unit, "SentDateTime");
        Map<String, String> targets = new LinkedHashMap<>();
        int index = 0;
        for (CdsReportNativeDto.Variant variant : variants) {
            for (var diagnostic : variant.diagnostics())
                notice(run, epoch, unit, diagnostic.code(), diagnostic.field());
            String filename =
                    publication.reference(
                            run,
                            epoch,
                            unit,
                            variant.name(),
                            variant.extension(),
                            variant.digest());
            Document document = new Document();
            document.setDocfilename(filename);
            String subclass = values.get("SubClass");
            document.setDocdesc(
                    (subclass == null || subclass.isBlank() ? "Imported report" : subclass)
                            + (variants.size() == 1
                                    ? ""
                                    : " (" + variant.name() + " representation)")
                            + (variant.mediaReviewRequired()
                                    ? " (source media review required)"
                                    : ""));
            document.setDoctype("CDS");
            document.setDocClass(values.get("Class"));
            document.setDocSubClass(subclass);
            document.setDoccreator(command.providerNo());
            document.setResponsible("");
            document.setReviewer("");
            document.setPublic1(0);
            document.setStatus(Document.STATUS_ACTIVE);
            document.setContenttype(variant.mime());
            document.setProgramId(command.programId());
            document.setRestrictToProgram(command.programId() != null);
            document.setUpdatedatetime(new Date());
            // Operational page-count cache: 0 means not yet counted, not a clinical page claim.
            document.setNumberofpages(0);
            document.setSourceFacility(values.get("SourceFacility"));
            String source = values.get("NativeSourceAuthor");
            if (source != null && source.codePointCount(0, source.length()) <= 60)
                document.setSource(source);
            else if (source != null)
                notice(
                        run,
                        epoch,
                        unit,
                        "CDS_REPORT_AUTHOR_CATEGORY_RETAINED",
                        "SourceAuthorPhysician");
            CdsTemporalDto event = prepared.event();
            // Partial dates remain labelled logical values in the report module, without false
            // native days.
            document.setContentdatetime(contentTime);
            document.setCdsContentPrecision(event.precision().name());
            document.setObservationdate(observationDay);
            document.setReceivedDate(receivedDay);
            document.setSentDateTime(sentTime);
            documentDao.persist(document);
            documentDao.flush();
            CtlDocument link = new CtlDocument();
            link.setId(new CtlDocumentPK("demographic", demographicNo, document.getDocumentNo()));
            link.setStatus("A");
            ctlDocumentDao.persist(link);
            targets.put("document#" + index, document.getId().toString());
            targets.put("ctl_document#" + index, demographicNo + "_" + document.getId());
            index++;
        }
        if (targets.isEmpty()) throw new CdsImportException("REPORT_REPRESENTATION_MISSING");
        return targets;
    }

    private List<Representation> representations(
            CdsRecordDto record, CdsSnapshotDto snapshot, String xmlRelative) throws IOException {
        String declaredType = fields.firstText(record, "FileExtensionAndVersion", 50);
        String format = fields.firstToken(record, "Format", 50);
        if ("Binary".equals(format) && (declaredType == null || declaredType.isBlank())) {
            throw new CdsImportException("REPORT_BINARY_MEDIA_TYPE_REQUIRED");
        }
        CdsFieldDto[] content = new CdsFieldDto[1];
        fields.forEachField(
                record,
                field -> {
                    String path = CdsFieldParser.unindexed(field.path());
                    if (path.equals("Content/Media") || path.equals("Content/TextContent")) {
                        if (content[0] != null)
                            throw new CdsImportException("REPORT_CONTENT_CHOICE_CONFLICT");
                        content[0] = field;
                    }
                });
        List<Representation> result = new ArrayList<>(2);
        if (content[0] != null) {
            CdsFieldDto selected = content[0];
            boolean text = !selected.kind().equals("BINARY");
            var corrected =
                    text
                            ? fields.correctedLogicalText(record, selected, 65536)
                            : java.util.Optional.<String>empty();
            byte[] correctedBytes =
                    corrected.isPresent()
                            ? corrected.get().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                            : null;
            CdsDocumentPublicationService.Source logicalSource =
                    correctedBytes == null
                            ? () -> fields.openValue(record, selected)
                            : () -> new java.io.ByteArrayInputStream(correctedBytes);
            String logicalDigest =
                    correctedBytes == null
                            ? selected.sha256()
                            : CdsPaths.hex(CdsPaths.sha256().digest(correctedBytes));
            long logicalLength = correctedBytes == null ? selected.length() : correctedBytes.length;
            if (text && "Binary".equals(format)) {
                // The guide declares Binary content to be base64. Decode strictly, retaining the
                // original textual source field independently; no content sniffing or permissive
                // MIME decoder.
                long bytes = 0;
                var digest = CdsPaths.sha256();
                try (var decoded = CdsBase64ValueParser.decode(logicalSource.open())) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = decoded.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted())
                            throw new CdsImportException("IMPORT_CANCELLED");
                        bytes = Math.addExact(bytes, count);
                        if (bytes > configuration.limits().maxBinaryBytes())
                            throw new CdsImportException("REPORT_BINARY_CAPACITY_EXCEEDED");
                        digest.update(buffer, 0, count);
                    }
                }
                CdsDocumentPublicationService.Source decodedSource =
                        () -> CdsBase64ValueParser.decode(logicalSource.open());
                var inspected =
                        mediaInspection.inspect(
                                declaredType, bytes, "Content/TextContent", decodedSource);
                result.add(
                        representation(
                                "embedded",
                                CdsPaths.hex(digest.digest()),
                                bytes,
                                decodedSource,
                                inspected));
            } else {
                var inspected =
                        text
                                ? mediaInspection.xmlText(logicalLength, declaredType)
                                : mediaInspection.inspect(
                                        declaredType,
                                        logicalLength,
                                        "Content/Media",
                                        logicalSource);
                if (!text && "Text".equals(format))
                    inspected = mediaInspection.binaryUnderTextDeclaration(inspected);
                result.add(
                        representation(
                                "embedded",
                                logicalDigest,
                                logicalLength,
                                logicalSource,
                                inspected));
            }
        }
        String external = fields.firstText(record, "FilePath", 250);
        if (external != null && !external.isBlank()) {
            if (snapshot == null || xmlRelative == null)
                throw new CdsImportException("REPORT_SNAPSHOT_CONTEXT_MISSING");
            CdsSnapshotFileDto file = snapshots.resolveReference(snapshot, xmlRelative, external);
            CdsDocumentPublicationService.Source externalSource =
                    () -> controlledExternal(file.path());
            var inspected =
                    mediaInspection.inspect(
                            declaredType, file.length(), "FilePath", externalSource);
            if ("Text".equals(format) && !inspected.mime().startsWith("text/plain"))
                inspected = mediaInspection.binaryUnderTextDeclaration(inspected);
            result.add(
                    representation(
                            "referenced", file.sha256(), file.length(), externalSource, inspected));
        }
        if (result.isEmpty()) throw new CdsImportException("REPORT_REPRESENTATION_MISSING");
        return result;
    }

    private java.io.InputStream controlledExternal(java.nio.file.Path path) throws IOException {
        fields.checkpoint();
        return new java.io.FilterInputStream(
                Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            @Override
            public int read() throws IOException {
                fields.checkpoint();
                return in.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                fields.checkpoint();
                return in.read(bytes, offset, length);
            }
        };
    }

    private Date timestamp(CdsTemporalDto temporal, String run, long epoch, String unit, String path) {
        if (!temporal.exactCalendarDate()
                || temporal.precision() == CdsTemporalDto.Precision.FULL_DATE) return null;
        try {
        if (temporal.offsetDateTime() != null)
            return CdsTemporalDto.timestampStorageInstant(temporal.offsetDateTime().toInstant()
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS), ZoneId.systemDefault());
        String zone = configuration.value("cds.import.clinicalZone", "");
        if (zone.isEmpty()) {
            notice(run, epoch, unit, "CDS_REPORT_TIME_ZONE_SOURCE_ONLY", path);
            return null;
        }
        return Date.from(
                temporal.timestampStorageAnchor(ZoneId.of(zone))
                        .toInstant()
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        } catch (IllegalArgumentException unavailable) {
            notice(run, epoch, unit, "CDS_REPORT_NATIVE_TIMESTAMP_SOURCE_ONLY", path);
            return null;
        }
    }

    private Date calendarDate(CdsTemporalDto temporal, String run, long epoch, String unit, String path) {
        if (!temporal.exactCalendarDate()) return null;
        try { return temporal.dateStorageAnchor(); }
        catch (io.github.carlos_emr.carlos.commn.model.CdsClinicalException unavailable) {
            notice(run, epoch, unit, "CDS_REPORT_NATIVE_DATE_SOURCE_ONLY", path);
            return null;
        }
    }

    private void dateNotice(String run, long epoch, String unit, String path, CdsTemporalDto date) {
        if (date.appliedShiftDays() != 0)
            notice(run, epoch, unit, "CDS_SYNTHETIC_DATE_SHIFT_APPLIED", path);
        if (date.precision() == CdsTemporalDto.Precision.YEAR
                || date.precision() == CdsTemporalDto.Precision.MONTH) {
            notice(run, epoch, unit, "CDS_PARTIAL_DATE_RETAINED", path);
        }
        if (date.offsetDateTime() != null && date.offsetDateTime().getNano() != 0
                || date.localDateTime() != null && date.localDateTime().getNano() != 0) {
            notice(run, epoch, unit, "CDS_REPORT_SUBSECOND_PRECISION_RETAINED_IN_SOURCE", path);
        }
        if (path.equals("ReceivedDateTime")
                && date.exactCalendarDate()
                && date.precision() != CdsTemporalDto.Precision.FULL_DATE) {
            notice(run, epoch, unit, "CDS_REPORT_RECEIVED_TIME_RETAINED_IN_SOURCE", path);
        }
        if (path.equals("SentDateTime") && date.precision() == CdsTemporalDto.Precision.FULL_DATE) {
            notice(run, epoch, unit, "CDS_REPORT_SENT_DATE_ONLY_RETAINED_IN_SOURCE", path);
        }
    }

    private void notice(String run, long epoch, String unit, String code, String path) {
        ledger.diagnostic(
                run,
                epoch,
                ledger.unit(run, unit).getPatientKey(),
                unit,
                "WARNING",
                code,
                "NORMALIZATION",
                path,
                summary(code),
                null,
                false);
    }

    private static Representation representation(
            String variant,
            String digest,
            long length,
            CdsDocumentPublicationService.Source source,
            CdsReportMediaService.Result inspected) {
        return new Representation(
                variant,
                inspected.extension(),
                inspected.mime(),
                digest,
                length,
                source,
                inspected.reviewRequired(),
                inspected.diagnostics());
    }

    private static String summary(String code) {
        return switch (code) {
            case "CDS_REPORT_MEDIA_DECLARATION_MISMATCH" ->
                    "The report bytes do not match the declared media header; complete bytes remain"
                            + " an opaque document requiring source media review";
            case "CDS_REPORT_TEXT_ENCODING_REQUIRES_REVIEW" ->
                    "The external or decoded text encoding is not established as UTF8; original"
                            + " bytes remain an opaque document requiring encoding review";
            case "CDS_REPORT_TEXT_ENCODING_INFERRED_UTF8" ->
                    "The report bytes validate as UTF8 but source encoding was unspecified; native"
                            + " text interpretation requires review and original bytes remain"
                            + " unchanged";
            case "CDS_REPORT_TEXT_MEDIA_DECLARATION_RETAINED" ->
                    "The source media declaration differs from exported XML text; both remain"
                            + " available for review";
            case "CDS_REPORT_BINARY_FORMAT_DECLARATION_RETAINED" ->
                    "The source marks decoded binary content as Text; complete bytes and that"
                            + " declaration remain available for review";
            case "CDS_REPORT_MEDIA_UNRECOGNIZED_SOURCE_RETAINED" ->
                    "The report media type is unsupported or unrecognized; complete original bytes"
                            + " remain an opaque document requiring source media review";
            case "CDS_REPORT_MEDIA_FROM_BOUNDED_HEADER" ->
                    "Native media type follows a bounded recognized file header; the original"
                            + " versioned or unsupported declaration remains in report details";
            case "CDS_REPORT_EMPTY_REPRESENTATION" ->
                    "The source report representation contains no bytes and requires review; its"
                            + " presence and metadata remain accounted for";
            case "CDS_REPORT_SUBSECOND_PRECISION_RETAINED_IN_SOURCE" ->
                    "The native timestamp is explicitly reduced to whole seconds; complete source"
                            + " subsecond precision remains in report details";
            case "CDS_REPORT_RECEIVED_TIME_RETAINED_IN_SOURCE" ->
                    "The native received-date destination stores a calendar date; complete received"
                            + " time remains in report details";
            case "CDS_REPORT_SENT_DATE_ONLY_RETAINED_IN_SOURCE" ->
                    "The source supplies a sent date without a clock; it remains in report details"
                            + " without an invented native timestamp";
            default ->
                    "Source meaning retained in the report category with original values and"
                            + " explicit precision";
        };
    }
}
