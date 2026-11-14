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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Coordinates imported laboratory projections and ordinary native HL7 observations.
 * Changed imported mirrors require review; native results have separate structural membership and complete supporting context.
 * An acknowledgement recorded in CARLOS on an imported result is appended to its retained
 * occurrence as one more ResultReviewer; an inbox comment on it travels in the NewCategory record
 * {@link #importedComments}.
 * This occurrence component alone does not qualify the complete laboratory source catalogue.
 *
 * <p>A chart is read whole when its report rows and inbox routes fit one section window, by the
 * passes that always read it, and window by window otherwise ({@link
 * CdsExportSectionWindowPlanner}); no number of reports refuses a chart. The owners of a window
 * are imported results, in the order of their import units, then native reports, in the order of
 * their numbers as text: the order of the occurrences. A window holds the rows of its reports
 * (the structured result, the message, its metadata and patient routing), their witnesses, their
 * inbox routes and its native reports parsed. Held for the whole chart: the retained index and
 * fields of an imported chart, the result id and report number of each imported result (a
 * reference to an id the index holds) and, for a chart read in windows, the number of each
 * native report. Every row is owned by exactly one occurrence: each window proves it for the
 * rows it loaded, and the rows taken by all windows are counted against the capture. The
 * inventory, the NewCategory records and the Reports entries of embedded documents receive the
 * native reports the same way ({@link #forEachNativeSource}, {@link #forEachImportedComments}).
 */
@Service
public class CdsExportLaboratoryOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    @Override
    public CdsCategory category() {
        return CdsCategory.LABORATORY_RESULTS;
    }

    private static final CdsCategory CATEGORY = CdsCategory.LABORATORY_RESULTS;
    private static final Set<String> TABLES =
            Set.of("cds_laboratory_result", "hl7TextMessage", "hl7TextInfo", "patientLabRouting");
    /** Inbox routes of the patient's HL7 reports and the public providers they name. */
    static final String ROUTES = "providerLabRouting";

    private static final Set<String> REVIEW_TABLES = Set.of(ROUTES, "provider");

    /** Reads the public providers the inbox routes name; a test supplies its own. */
    @FunctionalInterface
    interface Providers {
        Map<String, CdsExportCapturedTextRowDto> read(
                CdsExportLeaseDto lease,
                CdsExportCohortService.Patient patient,
                Set<String> tables,
                CdsExportPatientCaptureDto source,
                CdsExportClinicalOccurrenceService.Bounds bounds,
                CdsExportRunControl control)
                throws IOException;
    }

    /**
     * Receives every native report of a chart, one at a time, in the order of the reports'
     * LaboratoryResults ({@link #forEachNativeSource}).
     */
    interface NativeSources {
        /**
         * Whether this assembly reads the report's inbox routes. A window whose reports want
         * none costs no pass over the routes.
         */
        default boolean routed(NativeSource source) {
            return true;
        }

        /**
         * @param routes the report's inbox routes in row order; empty when {@link #routed} said
         *     they are not wanted
         */
        void accept(NativeSource source, List<CdsExportCapturedTextRowDto> routes)
                throws IOException;
    }

    /** Receives every {@link ImportedComments} of a chart in the order of the result ids. */
    @FunctionalInterface
    interface CommentedResults {
        void accept(ImportedComments comments) throws IOException;
    }

    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;
    private final CdsExportClinicalZoneService zones;
    private final Providers providerRows;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportLaboratoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures) {
        this(
                values,
                verification,
                witnesses,
                captures,
                new CdsExportCapturedTextRowsReader(captures),
                new CdsExportClinicalZoneService(captures),
                new CdsExportPublicProviderReader(captures)::read);
    }

    CdsExportLaboratoryOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures,
            CdsExportCapturedTextRowsReader rows,
            CdsExportClinicalZoneService zones,
            Providers providerRows) {
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        this.rows = Objects.requireNonNull(rows);
        this.zones = Objects.requireNonNull(zones);
        this.providerRows = Objects.requireNonNull(providerRows);
    }

    public CdsExportClinicalOccurrenceService.Result append(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        var result = new CdsExportClinicalOccurrenceService.Result[1];
        xml.source(
                emitter ->
                        result[0] =
                                assemble(
                                        lease,
                                        patient,
                                        patientKey,
                                        requiredTables,
                                        source,
                                        payloadManifest,
                                        bounds,
                                        control,
                                        emitter,
                                        output));
        return Objects.requireNonNull(result[0]);
    }

    private CdsExportClinicalOccurrenceService.Result assemble(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            String patientKey,
            Set<String> requiredTables,
            CdsExportPatientCaptureDto source,
            CdsExportStagedFileDto payloadManifest,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CdsExportXmlComposer.SectionEmitter xml,
            CdsExportClinicalOccurrenceService.Receipts output)
            throws IOException {
        patient.require(lease);
        Objects.requireNonNull(output);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        var tables = Set.copyOf(requiredTables);
        if (!lease.document().command().categories().contains(CATEGORY)
                || patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !tables.containsAll(TABLES)
                || !tables.containsAll(REVIEW_TABLES)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var limits = bounds.window();
        long reportRows = reportRows(source), routeRows = routeScope(source).rows();
        // The chart is read whole when it fits one window, by the passes that always read it
        // (the four report tables, their witnesses, the inbox routes), and window by window
        // otherwise (CdsExportSectionWindowPlanner): the reports of one window are held at a time.
        Loaded[] whole = {null};
        var wholeBudget = CdsExportSectionWindowPlanner.wholeBudget(reportRows, limits);
        if (reportRows <= limits.rows() && routeRows <= limits.rows())
            whole[0] =
                    load(
                            lease,
                            patient,
                            tables,
                            source,
                            bounds,
                            control,
                            Selection.ALL,
                            wholeBudget,
                            null,
                            true);
        var providers =
                routeRows == 0
                        ? Map.<String, CdsExportCapturedTextRowDto>of()
                        : providerRows.read(lease, patient, tables, source, bounds, control);
        // The clinic zone places acknowledgement times; read once, when a route exists.
        ZoneId[] clinic = {null};
        long[] counts = {0, 0, 0};
        boolean supportingRecords =
                lease.document().command().categories().contains(CdsCategory.NEW_CATEGORY);
        values.withValues(
                lease,
                patient,
                tables,
                source.manifest(),
                payloadManifest,
                bounds.fields(),
                bounds.bytes(),
                bounds.indexRows(),
                bounds.indexBytes(),
                control,
                (index, retained, streams) -> {
                    if (!index.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
                        invalid();
                    var checked =
                            verification.verify(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    index,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    1_000_000,
                                    control);
                    // The applied imported results in the order of their import units, which is
                    // the order of their occurrences: references to rows the index holds.
                    var units = new ArrayList<CdsExportCapturedTextRowDto>();
                    for (var unit :
                            index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
                        control.checkpoint();
                        if (!CATEGORY.wireName().equals(unit.fields().get("category"))) continue;
                        if (index.appliedUnits().contains(address(unit))) units.add(unit);
                    }
                    var imported = importedMessages(index);
                    var chart =
                            new Chart(
                                    lease,
                                    patient,
                                    patientKey,
                                    tables,
                                    source,
                                    bounds,
                                    control,
                                    xml,
                                    output,
                                    index,
                                    checked,
                                    retained,
                                    streams,
                                    providers,
                                    clinic,
                                    counts,
                                    supportingRecords,
                                    imported);
                    // A report that is parsed is held parsed with its window: counted before
                    // anything is emitted, so a chart that does not fit is read in windows.
                    if (whole[0] != null && !parsedFits(whole[0], imported, wholeBudget))
                        whole[0] = null;
                    if (whole[0] != null) {
                        emit(chart, units, whole[0], true);
                    } else {
                        // The first window of a chart that was tried whole is half of it.
                        int first =
                                reportRows <= limits.rows() && routeRows <= limits.rows()
                                        ? (int) Math.max(1, reportRows / 8)
                                        : limits.rows();
                        CdsExportSectionWindowPlanner.forEachWindowOf(
                                limits,
                                first,
                                owners -> {
                                    for (int i = 0; i < units.size(); i++) owners.add("u" + i);
                                    for (String id :
                                            nativeIds(
                                                    lease, patient, tables, source, bounds,
                                                    control, imported)) owners.add("n" + id);
                                },
                                (window, budget) -> {
                                    var windowUnits = new ArrayList<CdsExportCapturedTextRowDto>();
                                    var keys = new HashSet<String>();
                                    var labs = new HashSet<String>();
                                    for (int i = 0; i < units.size(); i++) {
                                        if (!window.contains("u" + i)) continue;
                                        var unit = units.get(i);
                                        windowUnits.add(unit);
                                        // The rows a unit names, and every row of the report
                                        // it names: a row it leaves out is still found.
                                        for (var ref :
                                                index.references()
                                                        .getOrDefault(address(unit), List.of())) {
                                            if (!TABLES.contains(ref.table())) continue;
                                            keys.add(key(ref.table(), ref.id()));
                                            if (ref.table().equals("hl7TextMessage"))
                                                labs.add(ref.id());
                                        }
                                    }
                                    var loaded =
                                            load(
                                                    lease,
                                                    patient,
                                                    tables,
                                                    source,
                                                    bounds,
                                                    control,
                                                    new Selection(keys, labs, window),
                                                    budget,
                                                    imported,
                                                    routeRows > 0);
                                    if (loaded == null) return false;
                                    long emitted = emit(chart, windowUnits, loaded, false);
                                    if (emitted != window.size()) invalid();
                                    return true;
                                });
                        // Every row of the four tables was taken by the window of its report: a
                        // row whose report is in no window is taken by none, and the count falls
                        // short.
                        for (String table : TABLES)
                            if (chart.taken[slot(table)]
                                    != source.capture().tables().get(table).rows()) review();
                        // A route left over names a report the chart does not hold.
                        if (chart.routesTaken != routeRows) invalid();
                    }
                    var expectedFields = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == CATEGORY) expectedFields.add(field.id());
                    if (!chart.consumed.equals(expectedFields)) invalid();
                    if (chart.unowned) review();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.authorizeCategory(patient.patient(), CATEGORY, true);
                    control.checkpoint();
                });
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                CATEGORY,
                counts[0],
                counts[0] - counts[2],
                counts[2],
                counts[1]);
    }

    /** What every window of one chart's LaboratoryResults shares. */
    private static final class Chart {
        final CdsExportLeaseDto lease;
        final CdsExportCohortService.Patient patient;
        final String patientKey;
        final Set<String> tables;
        final CdsExportPatientCaptureDto source;
        final CdsExportClinicalOccurrenceService.Bounds bounds;
        final CdsExportRunControl control;
        final CdsExportXmlComposer.SectionEmitter xml;
        final CdsExportClinicalOccurrenceService.Receipts output;
        final CdsExportRetainedIndexDto index;
        final CdsExportRetainedReceiptVerificationDto checked;
        final List<CdsExportRetainedFieldDto> retained;
        final CdsExportRetainedSectionWriter.Values streams;
        final Map<String, CdsExportCapturedTextRowDto> providers;
        final ZoneId[] clinic;
        final long[] counts;
        final boolean supportingRecords;
        final Set<String> imported;
        /** Retained fields the imported results used: references to ids the fields hold. */
        final Set<String> consumed = new HashSet<>();
        /**
         * Structured results and report numbers the imported results own, over every window:
         * references to ids the index holds. No result and no report is owned twice.
         */
        final Set<String> ownedResults = new HashSet<>(), ownedLabs = new HashSet<>();
        /** Rows of each of the four tables, and inbox routes, the windows took. */
        final long[] taken = new long[4];
        long routesTaken;
        /** A row or route of the chart read whole that no occurrence owns: review, at the end. */
        boolean unowned;

        Chart(
                CdsExportLeaseDto lease,
                CdsExportCohortService.Patient patient,
                String patientKey,
                Set<String> tables,
                CdsExportPatientCaptureDto source,
                CdsExportClinicalOccurrenceService.Bounds bounds,
                CdsExportRunControl control,
                CdsExportXmlComposer.SectionEmitter xml,
                CdsExportClinicalOccurrenceService.Receipts output,
                CdsExportRetainedIndexDto index,
                CdsExportRetainedReceiptVerificationDto checked,
                List<CdsExportRetainedFieldDto> retained,
                CdsExportRetainedSectionWriter.Values streams,
                Map<String, CdsExportCapturedTextRowDto> providers,
                ZoneId[] clinic,
                long[] counts,
                boolean supportingRecords,
                Set<String> imported) {
            this.lease = lease;
            this.patient = patient;
            this.patientKey = patientKey;
            this.tables = tables;
            this.source = source;
            this.bounds = bounds;
            this.control = control;
            this.xml = xml;
            this.output = output;
            this.index = index;
            this.checked = checked;
            this.retained = retained;
            this.streams = streams;
            this.providers = providers;
            this.clinic = clinic;
            this.counts = counts;
            this.supportingRecords = supportingRecords;
            this.imported = imported;
        }

        @Override
        public String toString() {
            return "CdsExportLaboratoryOccurrenceService.Chart[protected]";
        }
    }

    /**
     * Emits the occurrences of one window: its imported results in the order of their units,
     * then its native reports in the order of their numbers. Everything the window loaded must
     * be owned by one of them.
     *
     * @return the imported results and native reports emitted
     */
    private long emit(
            Chart chart, List<CdsExportCapturedTextRowDto> units, Loaded loaded, boolean whole)
            throws IOException {
        var lease = chart.lease;
        var patient = chart.patient;
        var tables = chart.tables;
        var source = chart.source;
        var bounds = chart.bounds;
        var control = chart.control;
        var index = chart.index;
        var checked = chart.checked;
        var streams = chart.streams;
        var current = loaded.rows;
        var exact = loaded.witnesses;
        var routes = loaded.routes;
        var owned = new HashSet<String>();
        // Every inbox route is accounted by the occurrence of the report it routes.
        var routed = new HashSet<String>();
        // The metadata and routing rows of each report number, in place of a pass over the
        // window for every result.
        var related = new HashMap<String, List<String>>();
        for (var candidate : current.values())
            if (candidate.table().equals("hl7TextInfo")
                    || candidate.table().equals("patientLabRouting"))
                related.computeIfAbsent(candidate.fields().get("lab_no"), ignored -> new ArrayList<>())
                        .add(key(candidate.table(), nativeId(candidate)));
        for (var unit : units) {
            control.checkpoint();
            String address = address(unit);
            var references = index.references().get(address);
            var comparisons = checked.units().get(address);
            if (references == null
                    || comparisons == null
                    || !references.equals(
                            comparisons.stream()
                                    .map(
                                            CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                    ::expected)
                                    .toList())) invalid();
            if (!checked.unchangedUnits().contains(address)) review();
            var resultRefs =
                    references.stream()
                            .filter(ref -> ref.table().equals("cds_laboratory_result"))
                            .toList();
            if (resultRefs.size() != 1) review();
            var row = current.get(key("cds_laboratory_result", resultRefs.getFirst().id()));
            if (row == null) review();
            String lab = row.fields().get("nativeLabNo");
            var expected = new HashSet<String>();
            expected.add(key(row.table(), nativeId(row)));
            if (lab != null) {
                expected.add(key("hl7TextMessage", lab));
                expected.addAll(related.getOrDefault(lab, List.of()));
            }
            var actual = new HashSet<String>();
            for (var ref : references) {
                String k = key(ref.table(), ref.id());
                if (!TABLES.contains(ref.table())
                        || !actual.add(k)
                        || !current.containsKey(k)
                        || !ref.digest().equals(exact.get(k).digest())
                        || !owned.add(k)
                        || ref.table().equals("cds_laboratory_result")
                                && !chart.ownedResults.add(ref.id())
                        || ref.table().equals("hl7TextMessage") && !chart.ownedLabs.add(ref.id()))
                    review();
            }
            if (!expected.equals(actual)
                    || lab != null && actual.size() != 4
                    || lab == null && actual.size() != 1) review();
            var sourceValue =
                    retained(
                            patient.patient(),
                            address,
                            row,
                            index,
                            checked,
                            chart.retained,
                            streams,
                            control);
            var fields = sourceValue.fields();
            var assessments =
                    new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>(
                            assess(current, actual));
            var assessedRows = new ArrayList<>(actual.stream().map(current::get).toList());
            // The retained occurrence keeps the reviewers the sending EMR recorded.
            // A provider who acknowledged the result in CARLOS afterwards is one more
            // ResultReviewer appended after them (the element is unbounded), so every
            // imported element keeps its exact bytes; an inbox comment travels whole
            // in the result's NewCategory supporting record.
            var importedRoutes =
                    lab == null
                            ? List.<CdsExportCapturedTextRowDto>of()
                            : routes.getOrDefault(lab, List.of());
            for (var route : importedRoutes) if (!routed.add(nativeId(route))) invalid();
            var importedReviews = CdsExportInboxReviewMapper.Reviews.NONE;
            if (!importedRoutes.isEmpty()) {
                if (chart.clinic[0] == null)
                    chart.clinic[0] =
                            zones.read(
                                            lease,
                                            patient,
                                            tables,
                                            source,
                                            bounds.fields(),
                                            bounds.bytes(),
                                            control)
                                    .zone();
                importedReviews =
                        CdsExportInboxReviewMapper.laboratory()
                                .map(importedRoutes, List.of(), chart.providers, chart.clinic[0]);
            }
            // A commented route travels in a NewCategory record; without NewCategory
            // it is decided OPERATIONAL (not selected) and the chart is not held.
            assessments.putAll(
                    CdsExportDemographicSupportingMapper.withSelection(
                            importedReviews.fields(), chart.supportingRecords));
            assessedRows.addAll(importedReviews.rows());
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(assessedRows, assessments, 1_000_000, control::checkpoint);
            var changes =
                    new ArrayList<CdsExportRetainedSectionWriter.Change>(sourceValue.changes());
            changes.addAll(appendedReviewers(fields, importedReviews));
            var content = content(fields, streams, changes, control::checkpoint);
            for (String field : sourceValue.sourceFields())
                if (!chart.consumed.add(field)) invalid();
            var identity =
                    new CdsExportIdentityDto(
                            chart.patientKey,
                            CATEGORY,
                            CdsImportKeys.hash(
                                    "carlos-native-occurrence-v1", row.table(), nativeId(row)));
            control.authorizeCategory(patient.patient(), CATEGORY, true);
            streams.requireActive();
            chart.xml.emit(new CdsExportXmlComposer.Section(identity, content.content()));
            chart.output.accept(
                    new CdsExportClinicalOccurrenceService.Receipt(
                            identity,
                            index.sourceManifestSha256(),
                            row.table(),
                            nativeId(row),
                            address,
                            assessments,
                            content.retainedFields(),
                            content.replacedFields()));
            control.authorizeCategory(patient.patient(), CATEGORY, true);
            chart.counts[0]++;
            chart.counts[1] = Math.addExact(chart.counts[1], sourceValue.sourceFields().size());
        }
        var nativeSources =
                nativeSources(patient.patient(), current, chart.imported, control::checkpoint);
        // Without NewCategory a report's source record is not written: its values
        // with no laboratory element are decided OPERATIONAL (not selected) in each
        // result's receipt, and the chart is exported, never held.
        // Embedded documents are exported as Reports entries by the Reports
        // assembler. A job that left Reports out exports the chart all the same: the
        // inventory names each such document as not exported
        // (CdsExportPatientInventoryService.LABORATORY_DOCUMENT_NOT_EXPORTED_LABEL).
        if (!nativeSources.isEmpty() && chart.clinic[0] == null)
            chart.clinic[0] =
                    zones.read(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    bounds.fields(),
                                    bounds.bytes(),
                                    control)
                            .zone();
        var zone = chart.clinic[0];
        for (var nativeSource : nativeSources) {
            var mapper = new CdsExportNativeLaboratoryMapper();
            var nativeRoutes = routes.getOrDefault(nativeSource.id(), List.of());
            for (var route : nativeRoutes) if (!routed.add(nativeId(route))) invalid();
            var reviews =
                    CdsExportInboxReviewMapper.laboratory()
                            .map(nativeRoutes, List.of(), chart.providers, zone);
            var assessments =
                    CdsExportDemographicSupportingMapper.withSelection(
                            CdsExportNativeLaboratoryMapper.assessments(nativeSource, reviews),
                            chart.supportingRecords);
            var assessedRows = new ArrayList<>(nativeSource.rows());
            assessedRows.addAll(reviews.rows());
            new CdsExportNativeFieldAssessmentValidator()
                    .requireRepresentable(assessedRows, assessments, 1_000_000, control::checkpoint);
            for (var row : nativeSource.rows())
                if (!owned.add(key(row.table(), nativeId(row)))) invalid();
            var mapped = mapper.map(nativeSource, zone, reviews);
            if (mapped.size() != nativeSource.parsed().observations().size()) invalid();
            for (var value : mapped) {
                String id = nativeSource.id() + ":" + value.ordinal();
                var identity =
                        new CdsExportIdentityDto(
                                chart.patientKey,
                                CATEGORY,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "hl7TextMessage", id));
                streams.requireActive();
                control.authorizeCategory(patient.patient(), CATEGORY, true);
                chart.xml.emit(
                        new CdsExportXmlComposer.Section(
                                identity, body -> mapper.write(value, body)));
                chart.output.accept(
                        new CdsExportClinicalOccurrenceService.Receipt(
                                identity,
                                index.sourceManifestSha256(),
                                "hl7TextMessage",
                                id,
                                null,
                                assessments,
                                Set.of(),
                                Set.of()));
                control.authorizeCategory(patient.patient(), CATEGORY, true);
                chart.counts[0]++;
                chart.counts[2]++;
            }
        }
        // Every row and every route the window loaded is owned by one of its occurrences. A
        // chart read whole reports it after the retained fields are compared, as it always did.
        long routeCount = 0;
        for (var list : routes.values()) routeCount += list.size();
        if (!owned.equals(current.keySet()) || routed.size() != routeCount) {
            if (!whole) review();
            chart.unowned = true;
        }
        for (var row : current.values()) chart.taken[slot(row.table())]++;
        chart.routesTaken = Math.addExact(chart.routesTaken, routeCount);
        return (long) units.size() + nativeSources.size();
    }

    /** The retained fields of one imported result, its source fields and their changes. */
    record Retained(
            List<CdsExportRetainedFieldDto> fields,
            Set<String> sourceFields,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        Retained {
            fields = List.copyOf(fields);
            sourceFields = Set.copyOf(sourceFields);
            changes = List.copyOf(changes);
        }

        @Override
        public String toString() {
            return "CdsExportLaboratoryOccurrenceService.Retained[protected]";
        }
    }

    /**
     * Reads the retained source of one imported result and proves it against the values the
     * import prepared and the structured row CARLOS holds. A test of the windows supplies its
     * own.
     */
    Retained retained(
            int patient,
            String address,
            CdsExportCapturedTextRowDto row,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> retained,
            CdsExportRetainedSectionWriter.Values streams,
            CdsExportRunControl control)
            throws IOException {
        var prepared = new CdsExportRetainedPreparationReader().laboratory(index, address).value();
        if (!address.substring(37).equals(row.fields().get("id"))
                || !address.substring(0, 36).equals(row.fields().get("runId"))) invalid();
        var sourceValue =
                new CdsExportLaboratoryRetainedSource()
                        .read(
                                patient,
                                address,
                                index,
                                checked,
                                retained,
                                streams,
                                control);
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CATEGORY,
                                address,
                                index,
                                checked,
                                retained,
                                control::checkpoint);
        String root = CdsExportRetainedOccurrencePath.root(fields.getFirst(), CATEGORY);
        if (!root.equals(row.fields().get("sourcePath"))) invalid();
        for (var scalar : CdsExportLaboratoryRetainedSource.NATIVE_SCALARS.entrySet())
            if (!Objects.equals(
                    prepared.values().get(scalar.getValue()),
                    row.fields().get(scalar.getKey()))) invalid();
        String flag =
                prepared.values().get("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum");
        if (flag == null)
            flag =
                    prepared.values()
                            .get("ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText");
        if (!Objects.equals(flag, row.fields().get("abnormalFlag"))
                || !nativeDate(
                        prepared.collection(),
                        row.fields().get("collectionDate"),
                        row.fields().get("collectionPrecision"))
                || !nativeDate(
                        prepared.requisition(),
                        row.fields().get("requisitionDate"),
                        row.fields().get("requisitionPrecision"))) invalid();
        return new Retained(fields, sourceValue.sourceFields(), sourceValue.changes());
    }

    /** The content of one imported result with its changes; a test of the windows supplies its own. */
    CdsExportRetainedSectionWriter.Prepared content(
            List<CdsExportRetainedFieldDto> fields,
            CdsExportRetainedSectionWriter.Values streams,
            List<CdsExportRetainedSectionWriter.Change> changes,
            Runnable checkpoint)
            throws IOException {
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(fields, streams, changes, checkpoint);
    }

    /**
     * One imported laboratory result whose CARLOS inbox routes carry a comment: its structured
     * result id, its report number and those routes, in row order. The NewCategory assembler
     * writes each as a {@code CARLOS.ImportedLaboratoryReview.v1} record; the inventory expects
     * one occurrence for each.
     */
    record ImportedComments(String result, String lab, List<CdsExportCapturedTextRowDto> routes) {
        ImportedComments {
            routes = List.copyOf(routes);
        }

        @Override
        public String toString() {
            return "ImportedComments[protected]";
        }
    }

    /**
     * One ResultReviewer addition after the imported ones for each acknowledgement recorded in
     * CARLOS on an imported result. Additions never touch an imported element.
     */
    static List<CdsExportRetainedSectionWriter.Change> appendedReviewers(
            List<CdsExportRetainedFieldDto> fields, CdsExportInboxReviewMapper.Reviews reviews)
            throws IOException {
        var changes = new ArrayList<CdsExportRetainedSectionWriter.Change>();
        long next = CdsExportInboxReviewMapper.nextOrdinal(fields, CATEGORY, "ResultReviewer");
        for (var reviewer : reviews.reviewers())
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            "ResultReviewer[" + next++ + "]",
                            Set.of(),
                            body -> CdsExportNativeLaboratoryMapper.reviewerContent(reviewer, body)));
        return List.copyOf(changes);
    }

    /** The NewCategory identity of an imported result's CARLOS inbox comments. */
    static String importedCommentsId(String result) {
        return CdsImportKeys.hash("carlos-native-occurrence-v1", "cds_laboratory_result", result);
    }

    /**
     * The applied imported results with commented CARLOS inbox routes, from the retained index
     * (each unit names its structured result and, for a result linked to an HL7 report, that
     * report) and the captured routes ({@link #routes}).
     */
    static List<ImportedComments> importedComments(
            CdsExportRetainedIndexDto index, Map<String, List<CdsExportCapturedTextRowDto>> routes)
            throws IOException {
        var result = new TreeMap<String, ImportedComments>();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            if (!CATEGORY.wireName().equals(unit.fields().get("category"))) continue;
            String address = unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) continue;
            String structured = null, lab = null;
            for (var reference : index.references().getOrDefault(address, List.of())) {
                if (reference.table().equals("cds_laboratory_result")) structured = reference.id();
                if (reference.table().equals("hl7TextMessage")) lab = reference.id();
            }
            if (structured == null || lab == null) continue;
            var carried = CdsExportInboxReviewMapper.carried(routes.getOrDefault(lab, List.of()));
            if (!carried.isEmpty()
                    && result.putIfAbsent(structured, new ImportedComments(structured, lab, carried))
                            != null) invalid();
        }
        return List.copyOf(result.values());
    }

    record NativeSource(
            String id,
            CdsExportCapturedTextRowDto message,
            CdsExportCapturedTextRowDto information,
            CdsExportCapturedTextRowDto routing,
            CdsExportNativeLaboratoryParser.Parsed parsed) {
        List<CdsExportCapturedTextRowDto> rows() {
            return List.of(message, information, routing);
        }

        @Override
        public String toString() {
            return "NativeSource[protected]";
        }
    }

    static List<NativeSource> nativeSources(
            int patient,
            Map<String, CdsExportCapturedTextRowDto> current,
            CdsExportRetainedIndexDto index,
            Runnable checkpoint)
            throws IOException {
        return nativeSources(patient, current, importedMessages(index), checkpoint);
    }

    /**
     * The native reports among {@code current}, parsed, in the order of their numbers as text.
     *
     * @param current every report row of the chart, or the rows of one window's reports
     * @param imported the report numbers an applied import unit names
     */
    private static List<NativeSource> nativeSources(
            int patient,
            Map<String, CdsExportCapturedTextRowDto> current,
            Set<String> imported,
            Runnable checkpoint)
            throws IOException {
        requireOwnership(patient, current);
        var mirrors = new HashSet<String>();
        var information = new HashMap<String, CdsExportCapturedTextRowDto>();
        var routing = new HashMap<String, CdsExportCapturedTextRowDto>();
        for (var row : current.values()) {
            if (row.table().equals("cds_laboratory_result")
                    && row.fields().get("nativeLabNo") != null)
                mirrors.add(row.fields().get("nativeLabNo"));
            if (row.table().equals("hl7TextInfo")) information.put(row.fields().get("lab_no"), row);
            if (row.table().equals("patientLabRouting"))
                routing.put(row.fields().get("lab_no"), row);
        }
        var sources = new ArrayList<NativeSource>();
        for (var row : current.values()) {
            checkpoint.run();
            if (!row.table().equals("hl7TextMessage")) continue;
            String id = nativeId(row);
            if (mirrors.contains(id)) {
                if (!imported.contains(id)) review();
                else continue;
            }
            if (imported.contains(id)) review();
            var parsed =
                    new CdsExportNativeLaboratoryParser()
                            .parse(row.fields().get("message"), checkpoint);
            sources.add(new NativeSource(id, row, information.get(id), routing.get(id), parsed));
        }
        sources.sort(Comparator.comparing(NativeSource::id));
        return List.copyOf(sources);
    }

    /**
     * Feeds {@code visitor} every native HL7 report of the chart, parsed, in the order of the
     * reports' LaboratoryResults (their numbers as text), with the inbox routes it asks for. The
     * chart is read whole when it fits one window and window by window otherwise ({@link
     * CdsExportSectionWindowPlanner}); every row a window holds is checked against its witness
     * before the visitor sees its report. Nothing is read when LaboratoryResults was not
     * selected: the reports are not captured then.
     *
     * <p>A chart read whole has the ownership of every report row checked here, as it always
     * had. A chart read in windows has it checked for the reports of each window; the rows of
     * its imported results are checked by the LaboratoryResults assembly, which every job that
     * reaches this method also runs.
     *
     * @return the number of native reports visited
     */
    long forEachNativeSource(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            NativeSources visitor)
            throws IOException {
        Objects.requireNonNull(visitor);
        if (!lease.document().command().categories().contains(CATEGORY)) return 0;
        patient.require(lease);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        if (!tables.containsAll(TABLES)
                || !tables.equals(source.capture().tables().keySet())
                || !source.manifest().plaintextSha256().equals(index.sourceManifestSha256()))
            invalid();
        new CdsExportMergeScopeValidator(rows)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var imported = importedMessages(index);
        var limits = bounds.window();
        long reportRows = reportRows(source);
        var routeScope = source.capture().tables().get(ROUTES);
        long routeRows = routeScope == null ? 0 : routeScope.rows();
        long[] visited = {0};
        CdsExportSectionWindowPlanner.WindowWork work =
                (window, budget) -> {
                    var selection =
                            window.whole() ? Selection.ALL : new Selection(Set.of(), Set.of(), window);
                    var loaded =
                            load(
                                    lease, patient, tables, source, bounds, control, selection,
                                    budget, imported, false);
                    if (loaded == null) return false;
                    var sources =
                            nativeSources(
                                    patient.patient(), loaded.rows, imported, control::checkpoint);
                    if (!window.whole() && sources.size() != window.size()) invalid();
                    boolean routed = false;
                    for (var report : sources) routed |= visitor.routed(report);
                    Map<String, List<CdsExportCapturedTextRowDto>> routes = Map.of();
                    if (routed) {
                        routes =
                                readRoutes(
                                        lease, patient, tables, source, bounds, control, selection,
                                        budget);
                        if (routes == null) return false;
                    }
                    // Nothing was handed to the visitor before this point: a window found too
                    // large is read again smaller.
                    for (var report : sources) {
                        control.checkpoint();
                        visitor.accept(
                                report,
                                visitor.routed(report)
                                        ? routes.getOrDefault(report.id(), List.of())
                                        : List.of());
                        visited[0]++;
                    }
                    return true;
                };
        boolean fits = reportRows <= limits.rows() && routeRows <= limits.rows();
        if (!fits
                || !work.run(
                        CdsExportSectionWindowPlanner.whole(reportRows),
                        CdsExportSectionWindowPlanner.wholeBudget(reportRows, limits)))
            CdsExportSectionWindowPlanner.forEachWindowOf(
                    limits,
                    fits ? (int) Math.max(1, reportRows / 8) : limits.rows(),
                    owners -> {
                        for (String id :
                                nativeIds(lease, patient, tables, source, bounds, control, imported))
                            owners.add("n" + id);
                    },
                    work);
        control.authorizeCategory(patient.patient(), CATEGORY, true);
        return visited[0];
    }

    /**
     * Feeds {@code visitor} every applied imported result whose CARLOS inbox routes carry a
     * comment ({@link ImportedComments}), in the order of the result ids. The routes are read
     * whole when they fit one window and window by window otherwise.
     */
    void forEachImportedComments(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            CommentedResults visitor)
            throws IOException {
        Objects.requireNonNull(visitor);
        // Each unit names its structured result and, for a result linked to an HL7 report, that
        // report: references to ids the index holds, in the order of the result ids.
        var pairs = new ArrayList<String[]>();
        for (var unit : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            if (!CATEGORY.wireName().equals(unit.fields().get("category"))) continue;
            String address = address(unit);
            if (!index.appliedUnits().contains(address)) continue;
            String structured = null, lab = null;
            for (var reference : index.references().getOrDefault(address, List.of())) {
                if (reference.table().equals("cds_laboratory_result")) structured = reference.id();
                if (reference.table().equals("hl7TextMessage")) lab = reference.id();
            }
            if (structured != null && lab != null) pairs.add(new String[] {structured, lab});
        }
        pairs.sort(Comparator.comparing(pair -> pair[0]));
        var limits = bounds.window();
        long routeRows = routeScope(source).rows();
        String[] last = {null};
        CdsExportSectionWindowPlanner.WindowWork work =
                (window, budget) -> {
                    var labs = new HashSet<String>();
                    if (!window.whole())
                        for (int i = 0; i < pairs.size(); i++)
                            if (window.contains(Integer.toString(i))) labs.add(pairs.get(i)[1]);
                    var routes =
                            readRoutes(
                                    lease,
                                    patient,
                                    tables,
                                    source,
                                    bounds,
                                    control,
                                    window.whole() ? Selection.ALL : new Selection(Set.of(), labs, null),
                                    budget);
                    if (routes == null) return false;
                    for (int i = 0; i < pairs.size(); i++) {
                        if (!window.contains(Integer.toString(i))) continue;
                        control.checkpoint();
                        var pair = pairs.get(i);
                        var carried =
                                CdsExportInboxReviewMapper.carried(
                                        routes.getOrDefault(pair[1], List.of()));
                        if (carried.isEmpty()) continue;
                        // One result is commented once, whichever unit names it.
                        if (pair[0].equals(last[0])) invalid();
                        last[0] = pair[0];
                        visitor.accept(new ImportedComments(pair[0], pair[1], carried));
                    }
                    return true;
                };
        if (routeRows > limits.rows()
                || !work.run(
                        CdsExportSectionWindowPlanner.whole(routeRows),
                        CdsExportSectionWindowPlanner.wholeBudget(routeRows, limits)))
            CdsExportSectionWindowPlanner.forEachWindowOf(
                    limits,
                    limits.rows(),
                    owners -> {
                        for (int i = 0; i < pairs.size(); i++) owners.add(Integer.toString(i));
                    },
                    work);
    }

    /** The report numbers an applied import unit of any category names. */
    private static Set<String> importedMessages(CdsExportRetainedIndexDto index) {
        var imported = new HashSet<String>();
        for (var address : index.appliedUnits())
            for (var ref : index.references().getOrDefault(address, List.of()))
                if (ref.table().equals("hl7TextMessage")) imported.add(ref.id());
        return imported;
    }

    private static String address(CdsExportCapturedTextRowDto unit) {
        return unit.fields().get("run_id") + ":" + unit.fields().get("unit_key");
    }

    private static long reportRows(CdsExportPatientCaptureDto source) {
        long total = 0;
        for (String table : TABLES)
            total = Math.addExact(total, source.capture().tables().get(table).rows());
        return total;
    }

    private static CdsExportCapturedTableDto routeScope(CdsExportPatientCaptureDto source)
            throws IOException {
        var scope = source.capture().tables().get(ROUTES);
        if (scope == null
                || !scope.columns()
                        .equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.LABORATORY_REVIEWERS
                                                .columns()))) invalid();
        return scope;
    }

    private static int slot(String table) throws IOException {
        return switch (table) {
            case "cds_laboratory_result" -> 0;
            case "hl7TextMessage" -> 1;
            case "hl7TextInfo" -> 2;
            case "patientLabRouting" -> 3;
            default -> throw new IOException("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        };
    }

    /**
     * The numbers of the chart's native reports (the reports no applied import unit names) in
     * the order of their LaboratoryResults, which is the order of the numbers as text: one pass
     * over the report messages that keeps each number, about sixty bytes each while they are
     * sorted.
     */
    private List<String> nativeIds(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Set<String> imported)
            throws IOException {
        var natives = new ArrayList<String>();
        var listed =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        "hl7TextMessage",
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            control.checkpoint();
                            if (!row.table().equals("hl7TextMessage")) invalid();
                            String id = nativeId(row);
                            if (!imported.contains(id)) natives.add(id);
                        });
        if (!listed.equals(source.capture())) invalid();
        natives.sort(Comparator.naturalOrder());
        return natives;
    }

    /**
     * The rows one window keeps: every row of the chart ({@link #ALL}), or the rows the window's
     * import units name with every row of the reports they name, and every row of the window's
     * native reports.
     */
    private record Selection(
            Set<String> keys, Set<String> labs, CdsExportSectionWindowPlanner.Window natives) {
        static final Selection ALL = new Selection(null, null, null);

        boolean whole() {
            return labs == null;
        }

        boolean lab(String lab) {
            return labs == null
                    || lab != null
                            && (labs.contains(lab)
                                    || natives != null && natives.contains("n" + lab));
        }

        boolean takes(CdsExportCapturedTextRowDto row, String id) {
            if (labs == null) return true;
            return switch (row.table()) {
                case "hl7TextMessage" -> lab(id);
                case "cds_laboratory_result" ->
                        keys.contains(key(row.table(), id))
                                || lab(row.fields().get("nativeLabNo"));
                default -> keys.contains(key(row.table(), id)) || lab(row.fields().get("lab_no"));
            };
        }

        @Override
        public String toString() {
            return "CdsExportLaboratoryOccurrenceService.Selection[protected]";
        }
    }

    /** The rows, witnesses and inbox routes one window holds, each row checked against its witness. */
    private static final class Loaded {
        final SortedMap<String, CdsExportCapturedTextRowDto> rows = new TreeMap<>();
        final Map<String, CdsExportNativeRowReceiptDto> witnesses = new HashMap<>();
        Map<String, List<CdsExportCapturedTextRowDto>> routes = Map.of();

        @Override
        public String toString() {
            return "CdsExportLaboratoryOccurrenceService.Loaded[protected]";
        }
    }

    /** Counts the reports that will be parsed, about twice their message each. */
    private static boolean parsedFits(
            Loaded loaded, Set<String> imported, CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        for (var row : loaded.rows.values())
            if (row.table().equals("hl7TextMessage") && !imported.contains(nativeId(row)))
                if (!budget.add(3, parsedSize(row))) return false;
        return true;
    }

    private static long parsedSize(CdsExportCapturedTextRowDto message) {
        String text = message.fields().get("message");
        return text == null ? 0 : 2L * text.length();
    }

    /**
     * Reads the rows {@code selection} keeps from the four report tables, their witnesses and
     * (when {@code withRoutes}) their inbox routes, and checks each row against its witness. In
     * every pass every row of the tables is checked for its shape and its owner, whether or not
     * the window keeps it.
     *
     * @param imported the report numbers import units name, to count the reports that will be
     *     parsed; null when the caller counts them itself
     * @return null when the window passed its budget: nothing of it is kept
     */
    private Loaded load(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Selection selection,
            CdsExportSectionWindowPlanner.Budget budget,
            Set<String> imported,
            boolean withRoutes)
            throws IOException {
        var loaded = new Loaded();
        var current = loaded.rows;
        var exact = loaded.witnesses;
        String owner = Integer.toString(patient.patient());
        long[] seen = new long[5];
        boolean[] held = {false};
        var layouts = new HashMap<String, Set<String>>();
        var read =
                rows.forEachTable(
                        lease,
                        patient,
                        tables,
                        TABLES,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            control.checkpoint();
                            String id = nativeId(row);
                            var columns = layouts.get(row.table());
                            if (columns == null) {
                                columns = Set.copyOf(plan(row.table()).columns());
                                layouts.put(row.table(), columns);
                            }
                            if (!row.fields().keySet().equals(columns)
                                    || !row.exactNumbers().isEmpty()) invalid();
                            seen[slot(row.table())]++;
                            if (!selection.whole()) {
                                // A row no window keeps is still this patient's, or the chart
                                // is refused as it is when read whole (requireOwnership).
                                if (row.table().equals("patientLabRouting")
                                        && (!owner.equals(row.fields().get("demographic_no"))
                                                || !"HL7".equals(row.fields().get("lab_type"))))
                                    held[0] = true;
                                if (row.table().equals("cds_laboratory_result")) {
                                    if (!owner.equals(row.fields().get("demographicNo"))) invalid();
                                    boolean blocked = "Y".equals(row.fields().get("sourceBlocked"));
                                    if ((row.fields().get("nativeLabNo") == null) != blocked)
                                        held[0] = true;
                                }
                            }
                            // A window found too large keeps nothing more; it is read again
                            // with fewer reports.
                            if (budget.exceeded() || !selection.takes(row, id)) return;
                            long size = 1024L + row.fields().size() * 256L;
                            for (var value : row.fields().values())
                                if (value != null)
                                    size =
                                            Math.addExact(
                                                    size,
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            if (!budget.add(0, size)) return;
                            if (imported != null
                                    && row.table().equals("hl7TextMessage")
                                    && !imported.contains(id)
                                    && !budget.add(3, parsedSize(row))) return;
                            if (current.putIfAbsent(key(row.table(), id), row) != null) invalid();
                        });
        if (!read.equals(source.capture())) invalid();
        if (budget.exceeded()) return null;
        var scanned =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        witness -> {
                            if (!TABLES.contains(witness.table())) return;
                            seen[4]++;
                            if (budget.exceeded()) return;
                            String k = key(witness.table(), witness.id());
                            // The witness of a row this window keeps. Read whole, every
                            // witness is one, and one with no row refuses the chart below.
                            if (!selection.whole() && !current.containsKey(k)) return;
                            if (!budget.add(
                                    2,
                                    1024L
                                            + (witness.state() == null
                                                    ? 0
                                                    : witness.state().columns().size() * 256L)))
                                return;
                            if (exact.putIfAbsent(k, witness) != null) invalid();
                        });
        if (budget.exceeded()) return null;
        if (!scanned.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet()) verify(entry.getValue(), exact.get(entry.getKey()));
        long total = 0;
        for (String table : TABLES) {
            long stated = source.capture().tables().get(table).rows();
            if (seen[slot(table)] != stated) invalid();
            total = Math.addExact(total, stated);
        }
        // A chart read in windows: as many witnesses as rows, so a row with none is found in
        // the window that keeps it.
        if (!selection.whole() && seen[4] != total) invalid();
        if (held[0]) review();
        requireOwnership(patient.patient(), current);
        if (withRoutes) {
            var routes =
                    readRoutes(lease, patient, tables, source, bounds, control, selection, budget);
            if (routes == null) return null;
            if (selection.whole())
                for (String lab : routes.keySet())
                    if (!current.containsKey(key("hl7TextMessage", lab))) invalid();
            loaded.routes = routes;
        }
        return loaded;
    }

    /**
     * The inbox routes of the reports {@code selection} keeps, by report number, from the
     * authenticated capture ({@code Plan.LABORATORY_REVIEWERS}). Read like the public provider
     * lookup: the routes have no historical receipt, and each one is accounted by its report's
     * occurrence. Every route of the chart is checked in every pass.
     *
     * @return null when the window passed its budget
     */
    private Map<String, List<CdsExportCapturedTextRowDto>> readRoutes(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control,
            Selection selection,
            CdsExportSectionWindowPlanner.Budget budget)
            throws IOException {
        patient.require(lease);
        var scope = routeScope(source);
        var columns = scope.columns();
        var result = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        // Read whole, the ids of the routes; in windows, the capture's rising row keys prove
        // that no route repeats without holding an id for each.
        var ids = new HashSet<String>();
        String[] previous = {null};
        long[] count = {0};
        var read =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        ROUTES,
                        source.manifest(),
                        bounds.fields(),
                        bounds.bytes(),
                        67_108_864,
                        control,
                        row -> {
                            control.checkpoint();
                            if (!row.table().equals(ROUTES)
                                    || !row.fields().keySet().equals(columns)
                                    || !row.exactNumbers().isEmpty()
                                    || !"HL7".equals(row.fields().get("lab_type"))) invalid();
                            String id = nativeId(row);
                            if (selection.whole()
                                    ? !ids.add(id)
                                    : previous[0] != null
                                            && previous[0].compareTo(row.recordKey()) >= 0)
                                invalid();
                            previous[0] = row.recordKey();
                            count[0]++;
                            String lab = row.fields().get("lab_no");
                            try {
                                long number = Long.parseLong(lab);
                                if (number < 1 || !Long.toString(number).equals(lab)) invalid();
                            } catch (NumberFormatException malformed) {
                                invalid();
                            }
                            if (budget.exceeded() || !selection.lab(lab)) return;
                            long size = 1024L;
                            for (var value : row.fields().values())
                                if (value != null)
                                    size =
                                            Math.addExact(
                                                    size,
                                                    value.getBytes(StandardCharsets.UTF_8).length
                                                            + 64L);
                            if (!budget.add(1, size)) return;
                            result.computeIfAbsent(lab, ignored -> new ArrayList<>()).add(row);
                        });
        if (!read.equals(source.capture()) || count[0] != scope.rows()) invalid();
        if (budget.exceeded()) return null;
        var copy = new TreeMap<String, List<CdsExportCapturedTextRowDto>>();
        result.forEach((lab, list) -> copy.put(lab, List.copyOf(list)));
        return Collections.unmodifiableMap(copy);
    }

    private static CdsExportPatientTableReadSupport.Plan plan(String table) throws IOException {
        return switch (table) {
            case "cds_laboratory_result" ->
                    CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS;
            case "hl7TextMessage" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_MESSAGES;
            case "hl7TextInfo" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_INFORMATION;
            case "patientLabRouting" -> CdsExportPatientTableReadSupport.Plan.LABORATORY_ROUTING;
            default -> throw new IOException("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        };
    }

    private static String nativeId(CdsExportCapturedTextRowDto row) throws IOException {
        String id = row.fields().get(row.table().equals("hl7TextMessage") ? "lab_id" : "id");
        try {
            if (row.table().equals("cds_laboratory_result")) {
                if (id == null || !id.matches("[0-9a-f]{64}") || !id.equals(row.recordKey()))
                    invalid();
            } else {
                long number = Long.parseLong(id);
                if (number < 1
                        || !Long.toString(number).equals(id)
                        || !CdsExportPatientTableSourceReader.key(number).equals(row.recordKey()))
                    invalid();
            }
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return id;
    }

    private static void verify(
            CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !nativeId(row).equals(witness.id())
                || !row.recordKey().equals(witness.recordKey())
                || !row.fields()
                        .keySet()
                        .equals(
                                Set.copyOf(
                                        witness.state().columns().stream()
                                                .map(CdsNativeRowDigest.Column::name)
                                                .toList()))) invalid();
        for (int i = 0; i < witness.state().columns().size(); i++) {
            var column = witness.state().columns().get(i);
            var cell = witness.state().cells().get(i);
            String value = row.fields().get(column.name());
            if (cell.exactNumeric() != null || CdsExactNumeric.codec(column.type()) != null)
                invalid();
            if (value == null) {
                if (!cell.sqlNull()) invalid();
            } else {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                if (cell.sqlNull()
                        || bytes.length != cell.bytes()
                        || !HexFormat.of()
                                .formatHex(CdsImportKeys.digest().digest(bytes))
                                .equals(cell.sha256())) invalid();
            }
        }
    }

    static void requireOwnership(int patient, Map<String, CdsExportCapturedTextRowDto> current)
            throws IOException {
        var routes = new HashMap<String, String>();
        var information = new HashMap<String, String>();
        var results = new HashMap<String, String>();
        var messages = new HashSet<String>();
        for (var row : current.values()) {
            String id = nativeId(row);
            switch (row.table()) {
                case "hl7TextMessage" -> {
                    if (!messages.add(id)) invalid();
                }
                case "patientLabRouting" -> {
                    if (!Integer.toString(patient).equals(row.fields().get("demographic_no"))
                            || !"HL7".equals(row.fields().get("lab_type"))
                            || routes.putIfAbsent(row.fields().get("lab_no"), id) != null) review();
                }
                case "hl7TextInfo" -> {
                    if (information.putIfAbsent(row.fields().get("lab_no"), id) != null) review();
                }
                case "cds_laboratory_result" -> {
                    if (!Integer.toString(patient).equals(row.fields().get("demographicNo")))
                        invalid();
                    String lab = row.fields().get("nativeLabNo");
                    if (lab == null
                            ? !"Y".equals(row.fields().get("sourceBlocked"))
                            : "Y".equals(row.fields().get("sourceBlocked"))
                                    || results.putIfAbsent(lab, id) != null) review();
                }
                default -> invalid();
            }
        }
        if (!messages.equals(routes.keySet())
                || !messages.equals(information.keySet())
                || !messages.containsAll(results.keySet())) review();
    }

    private static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assess(
            Map<String, CdsExportCapturedTextRowDto> current, Set<String> selected) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (String key : selected) {
            var row = current.get(key);
            var assessment = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet()) {
                if (!row.table().equals("cds_laboratory_result")) {
                    assessment.operational(
                            column,
                            "UNCHANGED_GENERATED_LAB_MIRROR_WITH_COMPLETE_STRUCTURED_SOURCE");
                    continue;
                }
                String path = CdsExportLaboratoryRetainedSource.NATIVE_SCALARS.get(column);
                if (path == null)
                    path =
                            switch (column) {
                                case "abnormalFlag" -> "ResultNormalAbnormalFlag";
                                case "collectionDate", "collectionPrecision" ->
                                        "CollectionDateTime";
                                case "requisitionDate", "requisitionPrecision" ->
                                        "LabRequisitionDateTime";
                                default -> null;
                            };
                if (path == null)
                    assessment.operational(column, "LOCAL_IDENTITY_PROVENANCE_AND_NATIVE_LAB_LINK");
                else {
                    String value = row.fields().get(column);
                    assessment.set(
                            column,
                            value == null || value.isEmpty() ? Outcome.ABSENT : Outcome.MAPPED,
                            path,
                            "VERIFIED_UNCHANGED_PREPARED_LABORATORY_VALUE");
                }
            }
        }
        return Map.copyOf(result);
    }

    static boolean nativeDate(CdsTemporalDto prepared, String value, String precision)
            throws IOException {
        if (prepared == null || !prepared.precision().name().equals(precision)) return false;
        new CdsExportPreparedTemporalMapper().map(prepared, true);
        if (prepared.precision() == CdsTemporalDto.Precision.UNKNOWN) return value == null;
        if (prepared.appliedShiftDays() == 0) return Objects.equals(prepared.original(), value);
        if (value == null) return false;
        try {
            boolean clock =
                    prepared.precision() == CdsTemporalDto.Precision.LOCAL_DATE_TIME
                            || prepared.precision() == CdsTemporalDto.Precision.OFFSET_DATE_TIME;
            if (!clock && prepared.precision() != CdsTemporalDto.Precision.FULL_DATE) return false;
            var parsed =
                    CdsTemporal.parse(
                            null,
                            null,
                            clock ? null : value,
                            clock ? value : null,
                            "LaboratoryDate");
            return parsed.precision() == prepared.precision()
                    && Objects.equals(parsed.earliest(), prepared.earliest())
                    && Objects.equals(parsed.latest(), prepared.latest())
                    && Objects.equals(parsed.localDateTime(), prepared.localDateTime())
                    && Objects.equals(parsed.offsetDateTime(), prepared.offsetDateTime());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

}
