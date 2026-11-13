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
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCareElementDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Original care parents and all child observations share one private source assembly; unknown/changed native care requires review. */
@Service
public class CdsExportCareOccurrenceService implements CdsExportCategoryOccurrenceAssembler {
    private static final Set<String> TABLES =
            Set.of(
                    "measurements",
                    "measurementsExt",
                    "measurementsDeleted",
                    "measurementType",
                    "demographic");
    private static final Pattern CHILD =
            Pattern.compile("^([A-Za-z]+)\\[([1-9][0-9]{0,17})\\](?=/|$)");
    private final CdsExportRetainedValuesService values;
    private final CdsExportRetainedReceiptVerificationService verification;
    private final CdsExportNativeRowReceiptReader witnesses;
    private final CdsExportNativeCaptureService captures;
    private final CdsExportCapturedTextRowsReader rows;

    public CdsExportCareOccurrenceService(
            CdsExportRetainedValuesService values,
            CdsExportRetainedReceiptVerificationService verification,
            CdsExportNativeRowReceiptReader witnesses,
            CdsExportNativeCaptureService captures) {
        this.values = Objects.requireNonNull(values);
        this.verification = Objects.requireNonNull(verification);
        this.witnesses = Objects.requireNonNull(witnesses);
        this.captures = Objects.requireNonNull(captures);
        rows = new CdsExportCapturedTextRowsReader(captures);
    }

    @Override
    public CdsCategory category() {
        return CdsCategory.CARE_ELEMENTS;
    }

    @Override
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
        Objects.requireNonNull(output);
        patient.require(lease);
        control.authorizeCategory(patient.patient(), category(), false);
        var tables = Set.copyOf(requiredTables);
        if (patientKey == null
                || !patientKey.matches("[0-9a-f]{64}")
                || !lease.document().command().categories().contains(category())
                || !tables.containsAll(TABLES)
                || !tables.equals(source.capture().tables().keySet())) invalid();
        new CdsExportMergeScopeValidator(captures)
                .requireUnmerged(
                        lease, patient, tables, source, bounds.fields(), bounds.bytes(), control);
        var captured = captureCareRows(lease, patient, tables, source, bounds, control);
        var current = captured.current();
        var exact = captured.exact();
        long[] totals = {0, 0, 0};
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
                    var dependencies = retainedDependencies(index, bounds, control);
                    var parents = dependencies.parents();
                    var expectedChildren = dependencies.expectedChildren();
                    var owned = new HashSet<String>();
                    var consumed = new HashSet<String>();
                    var childDone = new HashSet<String>();
                    var dictionary = new HashSet<String>();
                    for (var parent : parents.entrySet()) {
                        control.checkpoint();
                        String address = parent.getKey();
                        requireParentEvidence(patient.patient(), address, index, checked, exact);
                        var assembled =
                                importedParent(
                                        patient.patient(),
                                        address,
                                        parent.getValue(),
                                        dependencies,
                                        index,
                                        checked,
                                        retained,
                                        current,
                                        exact,
                                        new Claims(owned, consumed, childDone, dictionary),
                                        streams,
                                        control::checkpoint);
                        // Every observation of it was deleted in CARLOS: nothing to export.
                        if (assembled.content() == null) continue;
                        var content = assembled.content();
                        var assessments = assembled.assessments();
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-retained-occurrence-v1", address));
                        streams.requireActive();
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(new CdsExportXmlComposer.Section(identity, content.content()));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        "cds_import_unit",
                                        address.substring(37),
                                        address,
                                        assessments,
                                        content.retainedFields(),
                                        content.replacedFields()));
                        control.authorizeCategory(patient.patient(), category(), false);
                        totals[0]++;
                        totals[1] = Math.addExact(totals[1], assembled.sourceFields());
                    }
                    for (var nativeSource :
                            new CdsExportNativeCarePlanner()
                                    .plan(index, current, control::checkpoint)) {
                        if (nativeSource.deleted()) {
                            // Accounted, never exported; the extensions the deletion left
                            // behind are owned here so the completeness gate still closes.
                            var retired =
                                    CdsExportNativeCareMapper.retired(
                                            patient.patient(), nativeSource);
                            new CdsExportNativeFieldAssessmentValidator()
                                    .requireRepresentable(
                                            retiredRows(nativeSource),
                                            retired,
                                            1_000_000,
                                            control::checkpoint);
                            for (var extension : nativeSource.extensions())
                                if (!owned.add(key(extension.table(), id(extension)))) invalid();
                            continue;
                        }
                        if (!lease.document()
                                .command()
                                .categories()
                                .contains(CdsCategory.NEW_CATEGORY)) review();
                        var mapped =
                                new CdsExportNativeCareMapper()
                                        .map(
                                                patient.patient(),
                                                nativeSource.observation(),
                                                nativeSource.extensions(),
                                                nativeSource.definitions());
                        for (var row : mapped.rows()) {
                            String rowKey = key(row.table(), id(row));
                            if (row.table().equals("measurementType")) dictionary.add(rowKey);
                            else if (!owned.add(rowKey)) invalid();
                        }
                        if (nativeSource.element() != null
                                && !nativeSource.element().equals(mapped.element())) review();
                        new CdsExportNativeFieldAssessmentValidator()
                                .requireRepresentable(
                                        mapped.rows(),
                                        mapped.fields(),
                                        1_000_000,
                                        control::checkpoint);
                        if (mapped.element() == null) continue;
                        var identity =
                                new CdsExportIdentityDto(
                                        patientKey,
                                        category(),
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1",
                                                nativeSource.table(),
                                                nativeSource.id()));
                        streams.requireActive();
                        control.authorizeCategory(patient.patient(), category(), false);
                        xml.emit(
                                new CdsExportXmlComposer.Section(
                                        identity,
                                        body ->
                                                new CdsExportNativeCareMapper()
                                                        .writeCare(mapped, body)));
                        output.accept(
                                new CdsExportClinicalOccurrenceService.Receipt(
                                        identity,
                                        index.sourceManifestSha256(),
                                        nativeSource.table(),
                                        nativeSource.id(),
                                        null,
                                        mapped.fields(),
                                        Set.of(),
                                        Set.of()));
                        control.authorizeCategory(patient.patient(), category(), false);
                        totals[0]++;
                        totals[2]++;
                    }
                    var expectedNative = new HashSet<String>();
                    current.forEach(
                            (key, row) -> {
                                if (Set.of("measurements", "measurementsExt").contains(row.table()))
                                    expectedNative.add(key);
                            });
                    var expectedDictionary = new HashSet<String>();
                    current.forEach(
                            (key, row) -> {
                                if (row.table().equals("measurementType"))
                                    expectedDictionary.add(key);
                            });
                    var expectedSource = new HashSet<String>();
                    for (var field : retained)
                        if (field.category() == category()) expectedSource.add(field.id());
                    if (!childDone.equals(expectedChildren)
                            || !owned.equals(expectedNative)
                            || !dictionary.equals(expectedDictionary)) review();
                    if (!consumed.equals(expectedSource)) invalid();
                    streams.requireActive();
                    captures.authorizeSources(lease, patient, tables, control);
                    patient.require(lease);
                    control.authorizeCategory(patient.patient(), category(), false);
                    control.checkpoint();
                });
        return new CdsExportClinicalOccurrenceService.Result(
                source.manifest().plaintextSha256(),
                category(),
                totals[0],
                totals[0] - totals[2],
                totals[2],
                totals[1]);
    }

    /** Claims shared by every imported parent and the native passes of one patient's assembly. */
    record Claims(
            Set<String> owned, Set<String> consumed, Set<String> childDone, Set<String> dictionary) {
        @Override
        public String toString() {
            return "Claims[protected]";
        }
    }

    /**
     * @param content the retained element with its changes, or null when every observation of the
     *     parent was deleted in CARLOS and the element is not exported
     * @param sourceFields retained source fields read and verified for the exported element
     */
    record ImportedParent(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            CdsExportRetainedSectionWriter.Prepared content,
            long sourceFields) {
        @Override
        public String toString() {
            return "ImportedParent[protected]";
        }
    }

    /**
     * One applied imported CareElements parent. Each child is bound to its unchanged sealed
     * observation, or, when CARLOS deleted that observation (the only way a clinician corrects an
     * imported care value), its element is removed and the receipt lists its source fields as
     * replaced. A parent whose every observation was deleted is not exported and its source fields
     * are claimed as removed ({@link #removedImportedParents} gives the inventory the same answer).
     */
    static ImportedParent importedParent(
            int patient,
            String address,
            CdsExportCapturedTextRowDto parentRow,
            Dependencies dependencies,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> retained,
            Map<String, CdsExportCapturedTextRowDto> current,
            Map<String, CdsExportNativeRowReceiptDto> exact,
            Claims claims,
            CdsExportRetainedSectionWriter.Values streams,
            Runnable checkpoint)
            throws IOException {
        var metadata = parentRow.fields();
        var units = dependencies.units();
        var children = dependencies.children();
        var owned = claims.owned();
        var consumed = claims.consumed();
        var childDone = claims.childDone();
        var dictionary = claims.dictionary();
        var fields =
                new CdsExportRetainedOccurrenceFields()
                        .fieldsFor(
                                patient,
                                CdsCategory.CARE_ELEMENTS,
                                address,
                                index,
                                checked,
                                retained,
                                checkpoint);
        String
                root =
                        CdsExportRetainedOccurrencePath.root(
                                fields.getFirst(), CdsCategory.CARE_ELEMENTS),
                lineage = metadata.get("lineage_key");
        if (!address.substring(37)
                .equals(CdsImportKeys.hash(lineage, root, "NATIVE", "0")))
            invalid();
        var roots = new HashSet<String>();
        for (var field : fields) {
            if (!root.equals(
                    CdsExportRetainedOccurrencePath.root(field, CdsCategory.CARE_ELEMENTS)))
                invalid();
            String path =
                    field.sourcePath().length() == root.length()
                            ? ""
                            : field.sourcePath().substring(root.length() + 1);
            if (path.isEmpty()) continue;
            var match = CHILD.matcher(path);
            if (!match.find()
                    || !CdsCareScalarContract.ELEMENTS.contains(match.group(1)))
                invalid();
            roots.add(match.group());
        }
        var prepared = new TreeMap<String, CdsCareElementDto>();
        var assessments =
                new LinkedHashMap<
                        CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        var actualRows = new ArrayList<CdsExportCapturedTextRowDto>();
        // Child elements whose imported observation was deleted in CARLOS.
        var removedRoots = new TreeSet<String>();
        for (var child : children.entrySet())
            if (child.getValue().getFirst().equals(address)) {
                checkpoint.run();
                if (!childDone.add(child.getKey())) review();
                // No UI edits a measurement in place; a clinician corrects an
                // imported one by deleting it (EctDeleteData2Action moves the row
                // to measurementsDeleted) and records a new value. The deleted
                // value is not exported, as a deleted native one is not; any
                // other change to an imported observation is still unexplained.
                boolean deleted =
                        !checked.unchangedUnits().contains(child.getKey());
                if (deleted
                        && !importedObservationDeleted(
                                index.references().get(child.getKey()), current))
                    review();
                var unit = units.get(child.getKey());
                var info = unit.fields();
                if (!Objects.equals(
                                metadata.get("patient_key"),
                                info.get("patient_key"))
                        || !Objects.equals(lineage, info.get("lineage_key")))
                    invalid();
                String ordinal = info.get("ordinal");
                try {
                    long n = Long.parseLong(ordinal);
                    if (n < 1 || !Long.toString(n).equals(ordinal)) invalid();
                } catch (NumberFormatException malformed) {
                    invalid();
                }
                var candidate =
                        new CdsExportRetainedPreparationReader()
                                .care(index, child.getKey())
                                .value();
                var matching = new ArrayList<String>();
                for (String childRoot : roots)
                    if (childRoot.startsWith(candidate.element() + "[")
                            && child.getKey()
                                    .substring(37)
                                    .equals(
                                            CdsImportKeys.hash(
                                                    lineage,
                                                    root + "/" + childRoot,
                                                    "_CARE",
                                                    ordinal)))
                        matching.add(childRoot);
                if (matching.size() != 1
                        || removedRoots.contains(matching.getFirst())
                        || prepared.containsKey(matching.getFirst())) review();
                String childRoot = matching.getFirst();
                if (deleted) {
                    // Its element is removed from the retained parent below and
                    // the receipt lists its source fields as replaced; the
                    // deleted row and the extensions it left behind are
                    // accounted by the deleted-version pass.
                    if (index.references().get(child.getKey()).stream()
                                    .filter(ref -> ref.table().equals("measurements"))
                                    .count()
                            != 1) review();
                    removedRoots.add(childRoot);
                    continue;
                }
                prepared.put(childRoot, candidate);
                var refs = index.references().get(child.getKey());
                var compared = checked.units().get(child.getKey());
                if (refs == null
                        || compared == null
                        || !refs.equals(
                                compared.stream()
                                        .map(
                                                CdsExportRetainedReceiptVerificationDto
                                                                .CheckedReference
                                                        ::expected)
                                        .toList())) invalid();
                var primary =
                        refs.stream()
                                .filter(ref -> ref.table().equals("measurements"))
                                .toList();
                if (primary.size() != 1) review();
                String nativeId = primary.getFirst().id();
                var observation = current.get(key("measurements", nativeId));
                if (observation == null) review();
                var extensions = new ArrayList<CdsExportCapturedTextRowDto>();
                for (var ref : refs) {
                    String nativeKey = key(ref.table(), ref.id());
                    if (!Set.of("measurements", "measurementsExt")
                                    .contains(ref.table())
                            || !owned.add(nativeKey)
                            || !current.containsKey(nativeKey)
                            || !ref.digest().equals(exact.get(nativeKey).digest()))
                        review();
                    if (ref.table().equals("measurementsExt"))
                        extensions.add(current.get(nativeKey));
                }
                bind(
                        patient,
                        observation,
                        extensions,
                        candidate,
                        root + "/" + childRoot);
                assess(observation, extensions, candidate.element(), assessments);
                actualRows.add(observation);
                actualRows.addAll(extensions);
                for (var definition : current.values())
                    if (definition.table().equals("measurementType")
                            && Objects.equals(
                                    definition.fields().get("type"),
                                    observation.fields().get("type"))
                            && Objects.equals(
                                    definition.fields().get("measuringInstruction"),
                                    observation
                                            .fields()
                                            .get("measuringInstruction"))
                            && dictionary.add(
                                    key(definition.table(), id(definition)))) {
                        var scoped =
                                new CdsExportNativeFieldAssessments(
                                        definition, assessments);
                        for (String column : definition.fields().keySet())
                            scoped.operational(
                                    column,
                                    "LOCAL_VOCABULARY_WITH_FROZEN_PATIENT_CODE_VALUE_AND_INSTRUCTIONS");
                        actualRows.add(definition);
                    }
            }
        var planned = new HashSet<>(prepared.keySet());
        planned.addAll(removedRoots);
        if (!planned.equals(roots)
                || planned.size() != prepared.size() + removedRoots.size())
            review();
        var removedFields = new HashMap<String, Set<String>>();
        var keptFields = new ArrayList<CdsExportRetainedFieldDto>();
        for (var field : fields) {
            String path =
                    field.sourcePath().length() == root.length()
                            ? ""
                            : field.sourcePath().substring(root.length() + 1);
            var match = CHILD.matcher(path);
            String childRoot = match.find() ? match.group() : null;
            if (childRoot != null && removedRoots.contains(childRoot))
                removedFields
                        .computeIfAbsent(childRoot, ignored -> new HashSet<>())
                        .add(field.id());
            else keptFields.add(field);
        }
        if (!removedFields.keySet().equals(removedRoots)) invalid();
        if (prepared.isEmpty() && !removedRoots.isEmpty()) {
            // Every observation of this imported CareElements was deleted: the
            // element is not exported, and its source fields are accounted as
            // removed (the inventory leaves this occurrence out the same way).
            if (!keptFields.isEmpty()
                    && keptFields.stream()
                            .anyMatch(
                                    field ->
                                            field.sourcePath().length()
                                                    != root.length())) invalid();
            for (var field : fields)
                if (!consumed.add(field.id())) invalid();
            return new ImportedParent(Map.of(), null, 0);
        }
        var options =
                new CdsExportRetainedImportOptionsReader()
                        .fromCaptured(index, address.substring(0, 36));
        var sourceValues =
                new CdsExportCareRetainedSource()
                        .readVerified(
                                keptFields,
                                options,
                                prepared,
                                streams,
                                checkpoint);
        var changes = new ArrayList<>(sourceValues.changes());
        for (var removed : removedFields.entrySet())
            changes.add(
                    new CdsExportRetainedSectionWriter.Change(
                            removed.getKey(), removed.getValue(), null));
        var content =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(
                                fields,
                                streams,
                                changes,
                                checkpoint);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        actualRows, assessments, 1_000_000, checkpoint);
        for (String field : sourceValues.sourceFields())
            if (!consumed.add(field)) invalid();
        for (var removed : removedFields.values())
            for (String field : removed) if (!consumed.add(field)) invalid();
        return new ImportedParent(
                assessments, content, sourceValues.sourceFields().size());
    }

    static void bind(
            int patient,
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            CdsCareElementDto prepared,
            String path)
            throws IOException {
        String nativeId = id(row);
        var value = row.fields();
        String instruction =
                switch (prepared.measurementType()) {
                    case "CDS_GOALS" -> "Source documented goals";
                    case "CDS_EXAM" -> "Source exam performed; result not supplied";
                    case "CDS_CARE_VALUE" -> "Uninterpreted source care value";
                    default ->
                            prepared.unit() == null ? "Source assertion" : "in " + prepared.unit();
                };
        String comments =
                prepared.nativeOverflow()
                        ? "CDS_COMPLETE_VALUE_IN_SOURCE"
                        : prepared.uninterpreted() ? "CDS_UNINTERPRETED_SOURCE_VALUE" : "";
        if (!Integer.toString(patient).equals(value.get("demographicNo"))
                || !prepared.measurementType().equals(value.get("type"))
                || !prepared.value().equals(value.get("dataField"))
                || !instruction.equals(value.get("measuringInstruction"))
                || !comments.equals(value.get("comments"))
                || value.get("dateObserved") == null) invalid();
        if (value.get("appointmentNo") != null && !value.get("appointmentNo").equals("0")) review();
        var expected = new HashMap<String, String>();
        expected.put("cdsCareElement", prepared.element());
        expected.put("date_precision", "FULL_DATE");
        expected.put("cdsSourceDate", prepared.date().original());
        expected.put("cdsSourcePath", path);
        if (prepared.unit() != null) expected.put("unit", prepared.unit());
        if (prepared.code() != null) expected.put("loinc", prepared.code());
        if (prepared.nativeOverflow()) expected.put("cdsNativeOverflow", "true");
        if (prepared.uninterpreted()) expected.put("cdsUninterpreted", "true");
        if (prepared.sourceValueDigest() != null)
            expected.put("cdsValueDigest", prepared.sourceValueDigest());
        if (prepared.date().appliedShiftDays() != 0)
            expected.put("cdsShiftDays", Integer.toString(prepared.date().appliedShiftDays()));
        var actual = new HashMap<String, String>();
        for (var extension : extensions) {
            if (!nativeId.equals(extension.fields().get("measurement_id"))
                    || actual.containsKey(extension.fields().get("keyval"))) invalid();
            actual.put(extension.fields().get("keyval"), extension.fields().get("val"));
        }
        if (!expected.equals(actual)) review();
    }

    static void requireParentEvidence(
            int patient,
            String address,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            Map<String, CdsExportNativeRowReceiptDto> exact)
            throws IOException {
        var parentRefs = index.references().get(address);
        var parentProof = checked.units().get(address);
        if (parentRefs == null
                || parentRefs.size() != 1
                || !"demographic".equals(parentRefs.getFirst().table())
                || !Integer.toString(patient).equals(parentRefs.getFirst().id())
                || parentProof == null
                || parentProof.size() != 1
                || !parentRefs.equals(
                        parentProof.stream()
                                .map(
                                        CdsExportRetainedReceiptVerificationDto.CheckedReference
                                                ::expected)
                                .toList())
                || !exact.get(key("demographic", Integer.toString(patient)))
                        .digest()
                        .equals(parentProof.getFirst().currentDigest())) invalid();
    }

    record Dependencies(
            Map<String, CdsExportCapturedTextRowDto> units,
            Map<String, CdsExportCapturedTextRowDto> parents,
            Map<String, List<String>> children,
            Set<String> expectedChildren) {
        @Override
        public String toString() {
            return "Dependencies[protected]";
        }
    }

    private Dependencies retainedDependencies(
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var units = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        var parents = new TreeMap<String, CdsExportCapturedTextRowDto>();
        for (var row : index.tables().getOrDefault("cds_import_unit", Map.of()).values()) {
            String address = row.fields().get("run_id") + ":" + row.fields().get("unit_key");
            if (!index.appliedUnits().contains(address)) continue;
            if (units.putIfAbsent(address, row) != null) invalid();
            if (category().wireName().equals(row.fields().get("category")))
                parents.put(address, row);
        }
        if (parents.size() > bounds.nativeRows()) limit();
        var children = new HashMap<String, List<String>>();
        for (var edge : index.tables().getOrDefault("cds_import_dependency", Map.of()).values()) {
            control.checkpoint();
            String run = edge.fields().get("run_id"),
                    child = run + ":" + edge.fields().get("unit_key"),
                    parent = run + ":" + edge.fields().get("depends_on_unit_key");
            if (!parents.containsKey(parent)
                    || !units.containsKey(child)
                    || !"CareElements_CARE".equals(units.get(child).fields().get("category")))
                continue;
            String expected = CdsImportKeys.hash(run, child.substring(37), parent.substring(37));
            if (!expected.equals(edge.recordKey()) || !expected.equals(edge.fields().get("id")))
                invalid();
            var linked = children.computeIfAbsent(child, ignored -> new ArrayList<>());
            linked.add(parent);
            if (linked.size() != 1) review();
        }
        var expectedChildren = new HashSet<String>();
        for (var entry : units.entrySet())
            if ("CareElements_CARE".equals(entry.getValue().fields().get("category")))
                expectedChildren.add(entry.getKey());
        if (!children.keySet().equals(expectedChildren)) review();
        return new Dependencies(units, parents, children, expectedChildren);
    }

    /**
     * Applied imported CareElements parents whose every observation was deleted in CARLOS. They
     * are not exported and their retained source fields are accounted as removed by the assembly
     * ({@link #importedParent}); the inventory expects neither the occurrence nor those fields.
     */
    Set<String> removedImportedParents(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!lease.document().command().categories().contains(CdsCategory.CARE_ELEMENTS))
            return Set.of();
        if (!tables.containsAll(TABLES)) invalid();
        if (source.capture().tables().get("measurementsDeleted").rows() == 0) return Set.of();
        control.authorizeCategory(patient.patient(), category(), false);
        var captured = captureCareRows(lease, patient, tables, source, bounds, control);
        var result =
                removedImportedParents(
                        retainedDependencies(index, bounds, control), index, captured.current());
        control.authorizeCategory(patient.patient(), category(), false);
        return result;
    }

    static Set<String> removedImportedParents(
            Dependencies dependencies,
            CdsExportRetainedIndexDto index,
            Map<String, CdsExportCapturedTextRowDto> current) {
        var result = new TreeSet<String>();
        for (String parent : dependencies.parents().keySet()) {
            boolean any = false, all = true;
            for (var child : dependencies.children().entrySet())
                if (child.getValue().getFirst().equals(parent)) {
                    any = true;
                    all &=
                            importedObservationDeleted(
                                    index.references().get(child.getKey()), current);
                }
            if (any && all) result.add(parent);
        }
        return Set.copyOf(result);
    }

    /** Complete source membership for inventory/supporting data, without reading mapper output. */
    List<CdsExportNativeCarePlanner.Occurrence> nativeSources(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportRetainedIndexDto index,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        if (!lease.document().command().categories().contains(CdsCategory.CARE_ELEMENTS))
            return List.of();
        if (!tables.containsAll(TABLES)) invalid();
        if (source.capture().tables().get("measurements").rows() == 0
                && source.capture().tables().get("measurementsDeleted").rows() == 0)
            return List.of();
        control.authorizeCategory(patient.patient(), category(), false);
        var captured = captureCareRows(lease, patient, tables, source, bounds, control);
        var result =
                new CdsExportNativeCarePlanner()
                        .plan(index, captured.current(), control::checkpoint);
        control.authorizeCategory(patient.patient(), category(), false);
        return result;
    }

    private record CapturedCare(
            Map<String, CdsExportCapturedTextRowDto> current,
            Map<String, CdsExportNativeRowReceiptDto> exact) {
        @Override
        public String toString() {
            return "CapturedCare[protected]";
        }
    }

    private CapturedCare captureCareRows(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportClinicalOccurrenceService.Bounds bounds,
            CdsExportRunControl control)
            throws IOException {
        var current = new TreeMap<String, CdsExportCapturedTextRowDto>();
        var exact = new HashMap<String, CdsExportNativeRowReceiptDto>();
        var budget = new Budget(bounds);
        var scanned =
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
                            String id = id(row);
                            if (!row.fields().keySet().equals(columns(row.table()))
                                    || !row.exactNumbers().isEmpty()) invalid();
                            budget.add(row);
                            if (current.putIfAbsent(key(row.table(), id), row) != null) invalid();
                        });
        if (!scanned.equals(source.capture())) invalid();
        var proof =
                witnesses.scan(
                        lease,
                        patient,
                        tables,
                        source,
                        bounds.fields(),
                        bounds.bytes(),
                        control,
                        row -> {
                            if (!TABLES.contains(row.table())) return;
                            // Witnesses must match the rows already admitted by the budget.
                            if (exact.size() >= current.size()) invalid();
                            if (exact.putIfAbsent(key(row.table(), row.id()), row) != null)
                                invalid();
                        });
        if (!proof.sourceManifestSha256().equals(source.manifest().plaintextSha256())
                || !current.keySet().equals(exact.keySet())) invalid();
        for (var entry : current.entrySet()) verify(entry.getValue(), exact.get(entry.getKey()));
        for (String table : TABLES)
            if (current.values().stream().filter(row -> row.table().equals(table)).count()
                    != source.capture().tables().get(table).rows()) invalid();
        var demographic = current.get(key("demographic", Integer.toString(patient.patient())));
        if (demographic == null || source.capture().tables().get("demographic").rows() != 1)
            invalid();
        requireOwners(patient.patient(), current);
        return new CapturedCare(current, exact);
    }

    /**
     * One patient's care capture budget. The observation count (current and deleted rows) is
     * bounded by {@code nativeRows}; extension and vocabulary rows, which a chronic chart can
     * multiply, count only toward the retained-row memory bound ({@code indexBytes}). A chart
     * over either bound is a fact about that patient: it is held for review and named, and the
     * job continues, instead of stopping every patient with RESOURCE_LIMIT.
     */
    static final class Budget {
        private final long observations, bytes;
        private long seen, used;

        Budget(CdsExportClinicalOccurrenceService.Bounds bounds) {
            observations = bounds.nativeRows();
            bytes = bounds.indexBytes();
        }

        void add(CdsExportCapturedTextRowDto row) throws CdsExportMappingReviewException {
            if (Set.of("measurements", "measurementsDeleted").contains(row.table())) seen++;
            used = Math.addExact(used, 1024L + row.fields().size() * 256L);
            for (String value : row.fields().values())
                if (value != null)
                    used =
                            Math.addExact(
                                    used, value.getBytes(StandardCharsets.UTF_8).length + 64L);
            if (seen > observations || used > bytes)
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED");
        }
    }

    /**
     * Every captured extension belongs to exactly one of this patient's observations: a current
     * measurement, or the measurementsDeleted row whose originalId it still carries. An id that
     * names both, or two deleted rows, cannot be attributed and holds the patient for review.
     */
    static void requireOwners(int patient, Map<String, CdsExportCapturedTextRowDto> current)
            throws IOException {
        var deletedOwners = new HashMap<String, Integer>();
        for (var row : current.values()) {
            if (Set.of("measurements", "measurementsDeleted").contains(row.table())
                    && !Integer.toString(patient).equals(row.fields().get("demographicNo")))
                invalid();
            if (row.table().equals("measurementsDeleted")) {
                String original = row.fields().get("originalId");
                if (original == null || !original.matches("[1-9][0-9]{0,18}")) invalid();
                deletedOwners.merge(original, 1, Integer::sum);
            }
        }
        for (var row : current.values()) {
            if (!row.table().equals("measurementsExt")) continue;
            String owner = row.fields().get("measurement_id");
            boolean live = current.containsKey(key("measurements", owner));
            int deleted = owner == null ? 0 : deletedOwners.getOrDefault(owner, 0);
            if (!live && deleted == 0) invalid();
            if (live && deleted > 0 || deleted > 1)
                throw new CdsExportMappingReviewException(
                        "CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS");
        }
    }

    /**
     * True when an imported care child's observation is no longer a current measurement but a
     * captured measurementsDeleted row carries its id as originalId.
     */
    static boolean importedObservationDeleted(
            List<CdsExportRetainedIndexDto.Reference> refs,
            Map<String, CdsExportCapturedTextRowDto> current) {
        if (refs == null) return false;
        for (var ref : refs) {
            if (!ref.table().equals("measurements")
                    || current.containsKey(key("measurements", ref.id()))) continue;
            for (var row : current.values())
                if (row.table().equals("measurementsDeleted")
                        && ref.id().equals(row.fields().get("originalId"))) return true;
        }
        return false;
    }

    private static List<CdsExportCapturedTextRowDto> retiredRows(
            CdsExportNativeCarePlanner.Occurrence occurrence) {
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(occurrence.observation());
        rows.addAll(occurrence.extensions());
        return rows;
    }

    private static void assess(
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            String element,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> result) {
        var main = new CdsExportNativeFieldAssessments(row, result);
        for (String column : row.fields().keySet()) {
            if (Set.of("type", "dataField", "measuringInstruction", "dateObserved", "comments")
                    .contains(column))
                main.set(
                        column,
                        row.fields().get(column) == null || row.fields().get(column).isEmpty()
                                ? Outcome.ABSENT
                                : Outcome.MAPPED,
                        "CareElements/" + element,
                        "UNCHANGED_SEALED_CARE_PROJECTION_WITH_COMPLETE_TYPED_SOURCE");
            else
                main.operational(
                        column,
                        "LOCAL_IDENTITY_ENTRY_PROVENANCE_AND_EMPTY_APPOINTMENT_CONTEXT_NEVER_SOURCE_AUTHOR");
        }
        for (var extension : extensions) {
            var fields = new CdsExportNativeFieldAssessments(extension, result);
            fields.operational("id", "LOCAL_EXTENSION_IDENTITY");
            fields.operational("measurement_id", "EXACT_PATIENT_OBSERVATION_OWNER");
            fields.operational("keyval", "SEALED_CARE_METADATA_ROLE");
            fields.set(
                    "val",
                    Outcome.MAPPED,
                    "CareElements/" + element,
                    "UNCHANGED_SOURCE_TEMPORAL_UNIT_CODE_AND_COMPLETE_VALUE_BINDING");
        }
    }

    private static Set<String> columns(String table) throws IOException {
        if (table.equals("demographic")) {
            var columns = new HashSet<>(CdsExportDemographicRowDto.COLUMNS);
            columns.add("demographic_no");
            return Set.copyOf(columns);
        }
        return Set.copyOf(
                switch (table) {
                    case "measurements" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_CURRENT.columns();
                    case "measurementsExt" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_EXT.columns();
                    case "measurementType" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENT_TYPES.columns();
                    case "measurementsDeleted" ->
                            CdsExportPatientTableReadSupport.Plan.MEASUREMENTS_DELETED.columns();
                    default -> throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
                });
    }

    private static String id(CdsExportCapturedTextRowDto row) throws IOException {
        String value =
                row.fields().get(row.table().equals("demographic") ? "demographic_no" : "id");
        try {
            long id = Long.parseLong(value);
            if (id < 1
                    || !Long.toString(id).equals(value)
                    || !(row.table().equals("demographic")
                                    ? String.format(Locale.ROOT, "%010d", id)
                                    : CdsExportPatientTableSourceReader.key(id))
                            .equals(row.recordKey())) invalid();
        } catch (NumberFormatException malformed) {
            invalid();
        }
        return value;
    }

    private static void verify(
            CdsExportCapturedTextRowDto row, CdsExportNativeRowReceiptDto witness)
            throws IOException {
        if (witness == null
                || witness.state() == null
                || witness.partial()
                || !Objects.equals(witness.digest(), witness.state().digest())
                || !row.table().equals(witness.table())
                || !id(row).equals(witness.id())
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

    private static String key(String table, String id) {
        return table + ":" + id;
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
    }

    private static void review() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
    }

    private static void limit() {
        throw new CdsExportReviewException(CdsExportReviewException.Reason.RESOURCE_LIMIT);
    }
}
