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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Exports every Hospital Report Manager (HRM) report linked to the selected patient as one CDS
 * 5.2 Reports entry ({@link CdsExportHospitalReportMapper}), in the Reports source scope after the
 * native and imported documents. Ordinary charts are never held for their HRM reports: a report
 * whose stored message file cannot be read is exported from the details CARLOS recorded, and a
 * stored file that is not a readable HRM message is attached as stored.
 *
 * <p>Every captured HRM row is accounted with the report it belongs to; a link that names a report
 * that no longer exists shows nothing in the chart and is accounted as such. The patient is held
 * only for a link it cannot attribute exactly (a patient or report number spelled other than its
 * canonical decimal form) or a sign-off state other than 0 or 1. A message that holds several
 * reports is exported as one Reports entry for each, and a report whose context is longer than
 * Notes gets a companion entry ({@link CdsExportReportContext}); {@link #identities} reads and
 * maps every report the same way to tell the inventory which entries to expect.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportHospitalReportOccurrenceService {
    private final CdsExportNativeCaptureService captures;
    private final CdsExportStagingFactory stores;

    public CdsExportHospitalReportOccurrenceService(
            CdsExportNativeCaptureService captures, CdsExportStagingFactory stores) {
        this.captures = Objects.requireNonNull(captures);
        this.stores = Objects.requireNonNull(stores);
    }

    /** True when the capture scope holds the HRM reports (Reports was selected). */
    public static boolean captured(Set<String> tables) {
        return tables.contains(CdsExportHospitalReportSources.REPORTS);
    }

    /** The captured rows of the patient's HRM reports, grouped by report. */
    private record Planned(
            Map<Integer, CdsExportHospitalReportMapper.Source> reports,
            List<CdsExportCapturedTextRowDto> dangling) {
        @Override
        public String toString() {
            return "Planned[protected]";
        }
    }

    /** The Reports identities the inventory must expect: one per linked HRM report. */
    public List<CdsExportIdentityDto> identities(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}")) invalid();
        if (!captured(tables)) return List.of();
        var scope = source.capture().tables().get(CdsExportHospitalReportSources.REPORTS);
        if (scope == null) invalid();
        if (scope.rows() == 0) return List.of();
        var ids = new TreeMap<Integer, CdsExportIdentityDto>();
        var read =
                new CdsExportCapturedTextRowsReader(captures)
                        .forEach(
                                lease,
                                patient,
                                tables,
                                CdsExportHospitalReportSources.REPORTS,
                                source.manifest(),
                                bounds.fields(),
                                bounds.bytes(),
                                67_108_864,
                                control,
                                row -> {
                                    control.checkpoint();
                                    int id = id(row, "id");
                                    if (ids.size() >= bounds.nativeRows()) limit();
                                    if (ids.putIfAbsent(id, identity(patientKey, id)) != null)
                                        invalid();
                                });
        if (!read.equals(source.capture()) || ids.size() != scope.rows()) invalid();
        // The further reports of a message that holds several, and the companion record of any
        // entry whose context is longer than Notes: decided by reading and mapping each report
        // exactly as append does.
        var all = new ArrayList<>(ids.values());
        var planned = plan(lease, patient, tables, source, bounds, control);
        if (!planned.reports().keySet().equals(ids.keySet())) invalid();
        var providers =
                new CdsExportPublicProviderReader(captures)
                        .read(lease, patient, tables, source, bounds, control);
        var zone =
                new CdsExportClinicalZoneService(captures)
                        .read(
                                lease,
                                patient,
                                tables,
                                source,
                                bounds.fields(),
                                bounds.bytes(),
                                control)
                        .zone();
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var mapper = new CdsExportHospitalReportMapper();
        Runnable authorize =
                () -> control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        var captured = new CdsExportCapturedValues(store, source.capture().values());
        try {
            for (var entry : planned.reports().entrySet()) {
                control.checkpoint();
                int id = entry.getKey();
                var message = message(entry.getValue(), captured, control, authorize);
                for (int part = 0;
                        part < CdsExportHospitalReportMapper.parts(message.parsed());
                        part++) {
                    var result =
                            mapper.map(
                                    entry.getValue(),
                                    message.parsed(),
                                    part,
                                    content(patientKey, id, part, message),
                                    providers,
                                    zone);
                    if (part > 0) all.add(identity(patientKey, id, part));
                    if (result.companion() != null) all.add(companion(patientKey, id, part));
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            // The failure that ended the reading is the one reported; a failure of the closing
            // re-authentication travels with it.
            try {
                captured.close();
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        // Re-authenticates the captured values member the messages were read from; a failure
        // here refuses the result.
        captured.close();
        return List.copyOf(all);
    }

    /** A stored report's message: parsed, or unreadable (then attached as stored), or neither. */
    private record Message(CdsExportHospitalReportMessage.Parsed parsed, boolean unreadable) {
        @Override
        public String toString() {
            return "Message[protected]";
        }
    }

    private static Message message(
            CdsExportHospitalReportMapper.Source report,
            CdsExportCapturedValues captured,
            CdsExportRunControl control,
            Runnable authorize)
            throws IOException {
        var file = report.file();
        if (file.content() == null || file.content().bytes() <= 0) return new Message(null, false);
        // A file too large to parse in memory is not refused: it is attached exactly as stored.
        if (file.content().bytes() > CdsExportHospitalReportMessage.MAXIMUM_BYTES)
            return new Message(null, true);
        byte[] bytes;
        try (var input = captured.open(file.content(), control::checkpoint, authorize)) {
            bytes = input.readAllBytes();
        }
        try {
            if (bytes.length != file.content().bytes()) invalid();
            return new Message(CdsExportHospitalReportMessage.parse(bytes), false);
        } catch (CdsExportHospitalReportMessage.Unreadable notAMessage) {
            return new Message(null, true);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** How one report of a stored message is exported, and where its attachment goes. */
    private static CdsExportHospitalReportMapper.Content content(
            String patientKey, int id, int part, Message message) {
        if (message.unreadable())
            return new CdsExportHospitalReportMapper.Content(
                    CdsExportHospitalReportMapper.ContentKind.MESSAGE_FILE,
                    attachmentPath(
                            patientKey,
                            "cds-export-hrm-message-file-v1",
                            Integer.toString(id),
                            "xml"),
                    "xml");
        if (message.parsed() == null || message.parsed().reports().isEmpty())
            return CdsExportHospitalReportMapper.Content.none();
        var report = message.parsed().reports().get(part);
        if (report.binary() != null && report.binary().length > 0) {
            byte[] binary = report.binary();
            byte[] prefix =
                    Arrays.copyOf(
                            binary, Math.min(binary.length, CdsExportReportFileFormats.PREFIX));
            String declared = report.extension() == null ? "" : report.extension().strip();
            while (declared.startsWith(".")) declared = declared.substring(1);
            String extension =
                    CdsExportReportFileFormats.extension("report." + declared, null, prefix);
            return new CdsExportHospitalReportMapper.Content(
                    CdsExportHospitalReportMapper.ContentKind.BINARY,
                    attachmentPath(
                            patientKey,
                            "cds-export-hrm-report-file-v1",
                            part == 0 ? Integer.toString(id) : id + ":" + (part + 1),
                            extension),
                    extension);
        }
        return report.text() != null
                ? CdsExportHospitalReportMapper.Content.text()
                : CdsExportHospitalReportMapper.Content.none();
    }

    /**
     * Emits every linked HRM report as a Reports entry, with its staged attachment and receipt.
     *
     * @return the number of reports emitted
     */
    // Cleanup only throws without a primary failure; otherwise its error is suppressed on that
    // failure.
    @SuppressWarnings("java:S1143")
    public long append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        Objects.requireNonNull(output);
        if (patientKey == null || !patientKey.matches("[0-9a-f]{64}")) invalid();
        if (!captured(tables)) return 0;
        patient.require(lease);
        control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        if (!lease.document().command().categories().contains(CdsCategory.REPORTS)
                || !tables.containsAll(CdsExportHospitalReportSources.REQUIRED)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        // A chart without HRM links has nothing to read: no further pass over the capture.
        var scopes = source.capture().tables();
        if (scopes.get(CdsExportHospitalReportSources.LINKS).rows() == 0) {
            for (String table :
                    List.of(
                            CdsExportHospitalReportSources.REPORTS,
                            CdsExportHospitalReportSources.COMMENTS,
                            CdsExportHospitalReportSources.SUBCLASSES,
                            CdsExportHospitalReportSources.ROUTES,
                            CdsExportHospitalReportSources.FILES))
                if (scopes.get(table).rows() != 0) invalid();
            return 0;
        }
        var planned = plan(lease, patient, tables, source, bounds, control);
        // A link naming a report that no longer exists: the chart shows nothing for it.
        var danglingFields =
                new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var link : planned.dangling()) {
            var state = new CdsExportNativeFieldAssessments(link, danglingFields);
            for (String column : link.fields().keySet())
                state.operational(column, "HRM_LINK_TO_NO_STORED_REPORT_NOT_SHOWN_IN_CHART");
        }
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        planned.dangling(), danglingFields, 1_000_000, control::checkpoint);
        if (planned.reports().isEmpty()) {
            captures.authorizeSources(lease, patient, tables, control);
            return 0;
        }
        var providers =
                new CdsExportPublicProviderReader(captures)
                        .read(lease, patient, tables, source, bounds, control);
        var zone =
                new CdsExportClinicalZoneService(captures)
                        .read(
                                lease,
                                patient,
                                tables,
                                source,
                                bounds.fields(),
                                bounds.bytes(),
                                control)
                        .zone();
        var store =
                stores.openExisting(
                        lease.runId(),
                        lease.epoch(),
                        lease.document().storage(),
                        control::checkpoint);
        var captured = new CdsExportCapturedValues(store, source.capture().values());
        var attachments = new ArrayList<CdsExportStagedFileDto>();
        var mapper = new CdsExportHospitalReportMapper();
        Runnable authorize =
                () -> control.authorizeCategory(patient.patient(), CdsCategory.REPORTS, false);
        boolean complete = false;
        Throwable primary = null;
        long emitted = 0;
        try {
            for (var entry : planned.reports().entrySet()) {
                control.checkpoint();
                int id = entry.getKey();
                var report = entry.getValue();
                var file = report.file();
                var message = message(report, captured, control, authorize);
                // One entry for each report the message holds; the first is the chart's record.
                for (int part = 0;
                        part < CdsExportHospitalReportMapper.parts(message.parsed());
                        part++) {
                    var content = content(patientKey, id, part, message);
                    // Mapped before anything is staged: a report that holds the patient costs no
                    // copy.
                    var result =
                            mapper.map(report, message.parsed(), part, content, providers, zone);
                    new CdsExportNativeFieldAssessmentValidator()
                            .requireRepresentable(
                                    result.rows(),
                                    result.fields(),
                                    1_000_000,
                                    control::checkpoint);
                    CdsExportStagedFileDto copied = null;
                    if (content.kind() == CdsExportHospitalReportMapper.ContentKind.BINARY) {
                        authorize.run();
                        byte[] decoded = message.parsed().reports().get(part).binary();
                        copied = store.stage("REPORT_ATTACHMENT", bytes -> bytes.write(decoded));
                        attachments.add(copied);
                        if (copied.plaintextBytes() != decoded.length
                                || !copied.plaintextSha256().equals(sha256(decoded))) invalid();
                    } else if (content.kind()
                            == CdsExportHospitalReportMapper.ContentKind.MESSAGE_FILE) {
                        authorize.run();
                        copied =
                                store.stage(
                                        "REPORT_ATTACHMENT",
                                        bytes -> {
                                            try (var input =
                                                    captured.open(
                                                            file.content(),
                                                            control::checkpoint,
                                                            authorize)) {
                                                transfer(input, bytes, control::checkpoint);
                                            }
                                        });
                        attachments.add(copied);
                        if (copied.plaintextBytes() != file.content().bytes()
                                || !copied.plaintextSha256().equals(file.content().sha256()))
                            invalid();
                    }
                    var identity = part == 0 ? identity(patientKey, id) : identity(patientKey, id, part);
                    authorize.run();
                    if (copied != null)
                        output.attachment(
                                identity,
                                new CdsExportPackageMemberDto(content.filePath(), copied));
                    xml.emit(
                            new CdsExportXmlComposer.Section(
                                    identity, body -> mapper.write(result, body)));
                    authorize.run();
                    // A context longer than Notes travels in the companion record, which
                    // accounts the fields it carries.
                    output.accept(
                            new CdsExportClinicalOccurrenceService.Receipt(
                                    identity,
                                    source.manifest().plaintextSha256(),
                                    CdsExportHospitalReportSources.REPORTS,
                                    Integer.toString(id),
                                    null,
                                    result.companion() == null
                                            ? result.fields()
                                            : CdsExportReportContext.relocated(result.fields()),
                                    Set.of(),
                                    Set.of()));
                    emitted++;
                    if (result.companion() != null) {
                        var companion = companion(patientKey, id, part);
                        authorize.run();
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        companion,
                                        body ->
                                                CdsExportReportContext.write(
                                                        result.companion(), body)));
                        authorize.run();
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        companion,
                                        source.manifest().plaintextSha256(),
                                        CdsExportHospitalReportSources.REPORTS,
                                        Integer.toString(id),
                                        null,
                                        CdsExportReportContext.carried(result.fields()),
                                        Set.of(),
                                        Set.of()));
                        emitted++;
                    }
                }
            }
            // Re-authenticates the whole captured values member before any staged attachment can
            // be accepted with this patient's output.
            captured.close();
            captures.authorizeSources(lease, patient, tables, control);
            patient.require(lease);
            authorize.run();
            complete = true;
            return emitted;
        } catch (IOException | RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!complete) {
                try {
                    captured.close();
                } catch (IOException | RuntimeException cleanup) {
                    if (primary != null) primary.addSuppressed(cleanup);
                    else throw cleanup;
                }
                for (var attachment : attachments.reversed())
                    try {
                        store.discardUnpublished(attachment);
                    } catch (IOException cleanup) {
                        if (primary != null) primary.addSuppressed(cleanup);
                        else throw cleanup;
                    }
            }
        }
    }

    /**
     * Reads every HRM row of the authenticated capture and groups it by report. Every comment,
     * subclass, route and file belongs to exactly one captured report; every report has at least
     * one link of this patient.
     */
    private Planned plan(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var expected = Map.of(
                CdsExportHospitalReportSources.LINKS, Plan.HRM_REPORT_LINKS,
                CdsExportHospitalReportSources.REPORTS, Plan.HRM_REPORTS,
                CdsExportHospitalReportSources.COMMENTS, Plan.HRM_REPORT_COMMENTS,
                CdsExportHospitalReportSources.SUBCLASSES, Plan.HRM_REPORT_SUBCLASSES,
                CdsExportHospitalReportSources.ROUTES, Plan.HRM_REPORT_ROUTES);
        long total = 0;
        for (var entry : expected.entrySet()) {
            var scope = source.capture().tables().get(entry.getKey());
            if (scope == null || !scope.columns().equals(Set.copyOf(entry.getValue().columns())))
                invalid();
            total = Math.addExact(total, scope.rows());
        }
        if (total > bounds.indexRows()
                || source.capture().tables().get(CdsExportHospitalReportSources.REPORTS).rows()
                        > bounds.nativeRows()) limit();
        var byTable = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        long[] memory = {0};
        var read =
                new CdsExportCapturedTextRowsReader(captures)
                        .forEachTable(
                                lease,
                                patient,
                                tables,
                                CdsExportHospitalReportSources.ROWS,
                                source.manifest(),
                                bounds.fields(),
                                bounds.bytes(),
                                67_108_864,
                                control,
                                row -> {
                                    control.checkpoint();
                                    var plan = expected.get(row.table());
                                    if (plan == null
                                            || !row.fields().keySet().equals(Set.copyOf(plan.columns()))
                                            || !row.exactNumbers().isEmpty()) invalid();
                                    if (!CdsExportPatientTableSourceReader.key(id(row, "id"))
                                            .equals(row.recordKey())) invalid();
                                    memory[0] = Math.addExact(memory[0], 1024L);
                                    for (String value : row.fields().values())
                                        if (value != null)
                                            memory[0] =
                                                    Math.addExact(
                                                            memory[0],
                                                            64L
                                                                    + value.getBytes(
                                                                                    StandardCharsets
                                                                                            .UTF_8)
                                                                            .length);
                                    if (memory[0] > bounds.indexBytes()) limit();
                                    byTable.computeIfAbsent(row.table(), ignored -> new ArrayList<>())
                                            .add(row);
                                });
        if (!read.equals(source.capture())) invalid();
        String owner = Integer.toString(patient.patient());
        var reports = new TreeMap<Integer, CdsExportCapturedTextRowDto>();
        for (var row : byTable.getOrDefault(CdsExportHospitalReportSources.REPORTS, List.of()))
            if (reports.putIfAbsent(id(row, "id"), row) != null) invalid();
        var links = new TreeMap<Integer, List<CdsExportCapturedTextRowDto>>();
        var dangling = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var link : byTable.getOrDefault(CdsExportHospitalReportSources.LINKS, List.of())) {
            // A legacy spelling of the patient or report number is not the link CARLOS writes
            // (HRMReportParser.routeReportToDemographic); it cannot be attributed exactly.
            if (!owner.equals(link.fields().get("demographicNo")))
                review("CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW");
            String report = link.fields().get("hrmDocumentId");
            if (report == null || report.isEmpty()) {
                dangling.add(link);
                continue;
            }
            Integer number = canonical(report);
            if (number == null) review("CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW");
            if (!reports.containsKey(number)) dangling.add(link);
            else links.computeIfAbsent(number, ignored -> new ArrayList<>()).add(link);
        }
        if (!links.keySet().equals(reports.keySet())) invalid();
        var comments = children(byTable, CdsExportHospitalReportSources.COMMENTS, reports);
        var subClasses = children(byTable, CdsExportHospitalReportSources.SUBCLASSES, reports);
        var routes = children(byTable, CdsExportHospitalReportSources.ROUTES, reports);
        var files =
                new CdsExportCapturedHospitalReportFiles()
                        .read(captures, lease, patient, tables, source, bounds, control);
        if (!files.keySet().equals(reports.keySet())) invalid();
        var result = new TreeMap<Integer, CdsExportHospitalReportMapper.Source>();
        for (var entry : reports.entrySet()) {
            int id = entry.getKey();
            result.put(
                    id,
                    new CdsExportHospitalReportMapper.Source(
                            patient.patient(),
                            entry.getValue(),
                            links.get(id),
                            comments.getOrDefault(id, List.of()),
                            subClasses.getOrDefault(id, List.of()),
                            routes.getOrDefault(id, List.of()),
                            files.get(id)));
        }
        return new Planned(result, dangling);
    }

    /**
     * The rows of one child table by the report they name. The capture read exactly the rows of
     * the patient's reports; a report number spelled other than its canonical decimal form
     * cannot be attributed exactly and holds the patient.
     */
    private static Map<Integer, List<CdsExportCapturedTextRowDto>> children(
            Map<String, List<CdsExportCapturedTextRowDto>> byTable,
            String table,
            Map<Integer, CdsExportCapturedTextRowDto> reports)
            throws IOException {
        var result = new TreeMap<Integer, List<CdsExportCapturedTextRowDto>>();
        for (var row : byTable.getOrDefault(table, List.of())) {
            Integer report = canonical(row.fields().get("hrmDocumentId"));
            if (report == null) review("CDS_EXPORT_HRM_REPORT_LINK_REQUIRES_REVIEW");
            if (!reports.containsKey(report)) invalid();
            result.computeIfAbsent(report, ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private static Integer canonical(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}")) return null;
        long number = Long.parseLong(value);
        return number > Integer.MAX_VALUE ? null : (int) number;
    }

    private static int id(CdsExportCapturedTextRowDto row, String column) throws IOException {
        Integer id = canonical(row.fields().get(column));
        if (id == null) invalid();
        return id;
    }

    static CdsExportIdentityDto identity(String patientKey, int id) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsImportKeys.hash(
                        "carlos-native-occurrence-v1",
                        CdsExportHospitalReportSources.REPORTS,
                        Integer.toString(id)));
    }

    /** The identity of a further report ({@code part} 1 onward) of a message that holds several. */
    static CdsExportIdentityDto identity(String patientKey, int id, int part) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsImportKeys.hash(
                        "carlos-native-occurrence-v1",
                        CdsExportHospitalReportSources.REPORTS,
                        id + ":" + (part + 1)));
    }

    /** The identity of the companion record of one entry of a stored report. */
    static CdsExportIdentityDto companion(String patientKey, int id, int part) {
        return new CdsExportIdentityDto(
                patientKey,
                CdsCategory.REPORTS,
                CdsExportHospitalReportMapper.companionId(Integer.toString(id), part));
    }

    private static String attachmentPath(
            String patientKey, String purpose, String id, String extension) {
        return "Reports/" + CdsImportKeys.hash(purpose, patientKey, id) + "." + extension;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void transfer(InputStream input, OutputStream output, Runnable checkpoint)
            throws IOException {
        byte[] buffer = new byte[65536];
        try {
            for (int n; (n = input.read(buffer)) != -1; ) {
                checkpoint.run();
                if (n == 0) invalid();
                output.write(buffer, 0, n);
            }
        } finally {
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private static void review(String code) throws CdsExportMappingReviewException {
        throw new CdsExportMappingReviewException(code);
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_HRM_REPORT_OCCURRENCE_INVALID");
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
