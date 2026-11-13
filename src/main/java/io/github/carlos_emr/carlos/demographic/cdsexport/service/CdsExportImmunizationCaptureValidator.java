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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.springframework.stereotype.Service;

/**
 * Independent finished-capture closure, not a mapper or publication proof. Classification is
 * recomputed from authenticated captured configuration/vocabulary/metadata. Every selected
 * parent must match its complete metadata row; every extension and precision row must belong
 * to a selected parent. Large values stream to discard while both source EOFs remain mandatory.
 * Vaccine rows (Immunizations) and screening rows (PastHealth) are separate logical tables of
 * the same partition: each captured group must hold exactly its own partition, never the other's.
 */
@Service
public class CdsExportImmunizationCaptureValidator {
    /** The vaccine closure; screenings use {@link CdsExportPreventionSources#SCREENING_REQUIRED}. */
    static final Set<String> REQUIRED =
            CdsExportPreventionSources.union(
                    CdsExportPreventionSources.SHARED,
                    CdsExportPreventionSources.IMMUNIZATION_TABLES);
    private static final Set<String> PARENTS =
            Set.of("preventions", CdsExportPreventionSources.SCREENINGS);
    private static final Set<String> EXTENSIONS =
            Set.of("preventionsExt", CdsExportPreventionSources.SCREENING_EXTENSIONS);
    private static final Set<String> PARENT =
            Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL.columns()),
            EXTENSION = Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT.columns()),
            PRECISION =
                    Set.copyOf(CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION.columns());
    private static final Map<String, Set<String>> COLUMNS =
            Map.of(
                    "preventions", PARENT,
                    "preventionsExt", EXTENSION,
                    "prevention_precision", PRECISION,
                    CdsExportPreventionSources.SCREENINGS, PARENT,
                    CdsExportPreventionSources.SCREENING_EXTENSIONS, EXTENSION,
                    CdsExportPreventionSources.SCREENING_PRECISION, PRECISION);
    private final CdsExportNativeCaptureService captures;
    private final CdsExportPreventionClassificationService classifications;
    private final CdsExportCapturedTextRowsReader rows;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportImmunizationCaptureValidator(
            CdsExportNativeCaptureService captures,
            CdsExportPreventionClassificationService classifications) {
        this(captures, classifications, new CdsExportCapturedTextRowsReader(captures));
    }

    CdsExportImmunizationCaptureValidator(
            CdsExportNativeCaptureService captures,
            CdsExportPreventionClassificationService classifications,
            CdsExportCapturedTextRowsReader rows) {
        this.captures = Objects.requireNonNull(captures);
        this.classifications = Objects.requireNonNull(classifications);
        this.rows = Objects.requireNonNull(rows);
    }

    public CdsExportPreventionScopePlanner.Result verify(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto configuration,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        return verify(
                lease,
                patient,
                tables,
                source,
                configuration,
                maximumFields,
                maximumBytes,
                control,
                false);
    }

    public CdsExportPreventionScopePlanner.Result verifyEmbedded(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control)
            throws IOException {
        return verify(
                lease, patient, tables, source, null, maximumFields, maximumBytes, control, true);
    }

    private CdsExportPreventionScopePlanner.Result verify(
            CdsExportLeaseDto lease,
            CdsExportCohortService.Patient patient,
            Set<String> tables,
            CdsExportPatientCaptureDto source,
            CdsExportPreventionConfigurationDto configuration,
            long maximumFields,
            long maximumBytes,
            CdsExportRunControl control,
            boolean embedded)
            throws IOException {
        patient.require(lease);
        control.checkpoint();
        if (!tables.equals(source.capture().tables().keySet())
                || !tables.containsAll(CdsExportPreventionSources.SHARED)) invalid();
        // A selected category captures its whole group; an unselected one captures none of it.
        boolean vaccines = group(tables, CdsExportPreventionSources.IMMUNIZATION_TABLES),
                screenings = group(tables, CdsExportPreventionSources.SCREENING_TABLES);
        if (!vaccines && !screenings) invalid();
        for (var entry : COLUMNS.entrySet())
            if (tables.contains(entry.getKey())
                    && !source.capture()
                            .tables()
                            .get(entry.getKey())
                            .columns()
                            .equals(entry.getValue())) invalid();
        var selection =
                embedded
                        ? classifications.classifyEmbedded(
                                lease,
                                patient,
                                tables,
                                source,
                                maximumFields,
                                maximumBytes,
                                control)
                        : classifications.classify(
                                lease,
                                patient,
                                tables,
                                source,
                                configuration,
                                maximumFields,
                                maximumBytes,
                                control);
        if (selection.patient() != patient.patient()
                || !selection.sourceManifestSha256().equals(source.manifest().plaintextSha256()))
            invalid();
        var metadata = new HashMap<Integer, Map<String, String>>();
        var actual =
                rows.forEach(
                        lease,
                        patient,
                        tables,
                        "preventions_scope",
                        source.manifest(),
                        maximumFields,
                        maximumBytes,
                        4096,
                        control,
                        row -> {
                            if (!row.table().equals("preventions_scope")
                                    || !row.fields()
                                            .keySet()
                                            .equals(CdsExportPreventionScopePlanner.COLUMNS)
                                    || !row.exactNumbers().isEmpty()
                                    || metadata.size() >= 10_000) invalid();
                            int id = integer(row.fields().get("id"));
                            if (!row.recordKey().equals(CdsExportPatientTableSourceReader.key(id))
                                    || !Integer.toString(patient.patient())
                                            .equals(row.fields().get("demographic_no"))
                                    || metadata.putIfAbsent(id, row.fields()) != null) invalid();
                        });
        if (!actual.equals(source.capture())
                || metadata.size() != selection.rows()
                || metadata.size() != source.capture().tables().get("preventions_scope").rows())
            invalid();
        var partition = new HashSet<Integer>(selection.immunizations());
        partition.addAll(selection.otherPreventions());
        partition.addAll(selection.reviews().keySet());
        if (!partition.equals(metadata.keySet())) invalid();
        try (var fields =
                new Fields(selection.immunizations(), selection.otherPreventions(), metadata)) {
            actual =
                    captures.scan(
                            lease,
                            patient,
                            tables,
                            source.manifest(),
                            maximumFields,
                            maximumBytes,
                            control,
                            fields);
            fields.finishRow();
            if (!actual.equals(source.capture())
                    || vaccines && !fields.parents.equals(selection.immunizations())
                    || screenings && !fields.screeningParents.equals(selection.otherPreventions()))
                invalid();
            for (String table : COLUMNS.keySet())
                if (tables.contains(table)
                        && fields.counts.getOrDefault(table, 0L)
                                != actual.tables().get(table).rows()) invalid();
        }
        captures.authorizeSources(lease, patient, tables, control);
        patient.require(lease);
        control.checkpoint();
        // Authenticate the entire capture and final authority before returning even an adverse
        // review.
        if (!selection.representable())
            throw new CdsExportReviewException(CdsExportReviewException.Reason.UNMAPPED_DATA);
        return selection;
    }

    private static boolean group(Set<String> tables, Set<String> group) throws IOException {
        if (tables.containsAll(group)) return true;
        if (!Collections.disjoint(tables, group)) invalid();
        return false;
    }

    private static final class Fields
            implements CdsExportNativeCaptureReader.Fields, AutoCloseable {
        private final Set<Integer> selected, screenings;
        private final Map<Integer, Map<String, String>> metadata;
        private final Set<Integer> parents = new HashSet<>(),
                screeningParents = new HashSet<>(),
                precisionIds = new HashSet<>();
        private final Map<String, Long> counts = new HashMap<>();
        private final Map<String, String> values = new HashMap<>();
        private final Set<String> headers = new HashSet<>();
        private String table, key;
        private CdsExportCapturedFieldDto pending;
        private ByteArrayOutputStream bytes;
        private boolean closed;

        Fields(
                Set<Integer> selected,
                Set<Integer> screenings,
                Map<Integer, Map<String, String>> metadata) {
            this.selected = selected;
            this.screenings = screenings;
            this.metadata = metadata;
        }

        @Override
        public OutputStream output(CdsExportCapturedFieldDto field) throws IOException {
            if (closed || pending != null) invalid();
            if (!COLUMNS.containsKey(field.table()) && !field.table().equals("partial_date"))
                return OutputStream.nullOutputStream();
            if (!field.table().equals(table) || !field.recordKey().equals(key)) {
                finishRow();
                table = field.table();
                key = field.recordKey();
            }
            if (!headers.add(field.column()) || field.exactNumeric() != null) invalid();
            pending = field;
            boolean small =
                    PARENTS.contains(table)
                            ? CdsExportPreventionScopePlanner.COLUMNS.contains(field.column())
                            : Set.of("id", "prevention_id", "table_id", "table_name")
                                    .contains(field.column());
            if (!small) return OutputStream.nullOutputStream();
            if (field.kind() == CdsExportCapturedFieldDto.Kind.BINARY || field.bytes() > 4096)
                invalid();
            bytes = new ByteArrayOutputStream((int) field.bytes());
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    if (pending != field || bytes == null || bytes.size() >= field.bytes())
                        invalid();
                    bytes.write(value);
                }

                @Override
                public void write(byte[] value, int offset, int length) throws IOException {
                    Objects.checkFromIndexSize(offset, length, value.length);
                    if (pending != field || bytes == null || length > field.bytes() - bytes.size())
                        invalid();
                    bytes.write(value, offset, length);
                }
            };
        }

        @Override
        public void verified(CdsExportCapturedFieldDto field) throws IOException {
            if (!COLUMNS.containsKey(field.table()) && !field.table().equals("partial_date"))
                return;
            if (closed || pending != field) invalid();
            if (bytes != null) {
                if (bytes.size() != field.bytes()) invalid();
                String text = null;
                if (field.kind() != CdsExportCapturedFieldDto.Kind.SQL_NULL)
                    try {
                        text =
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                                        .decode(ByteBuffer.wrap(bytes.toByteArray()))
                                        .toString();
                    } catch (CharacterCodingException failed) {
                        throw new IOException("CDS_EXPORT_IMMUNIZATION_CAPTURE_INVALID");
                    }
                values.put(field.column(), text);
                bytes = null;
            }
            pending = null;
        }

        void finishRow() throws IOException {
            if (closed || pending != null) invalid();
            if (table == null) return;
            int id = integer(values.get("id"));
            if (!key.equals(CdsExportPatientTableSourceReader.key(id))) invalid();
            if (table.equals("partial_date")) {
                if (!headers.equals(
                                Set.copyOf(
                                        CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION
                                                .columns()))
                        || !Set.of("1", "2").contains(values.get("table_name"))
                        || !precisionIds.add(id)) invalid();
            } else {
                if (!headers.equals(COLUMNS.get(table))) invalid();
                boolean screening = CdsExportPreventionSources.SCREENING_TABLES.contains(table);
                var owners = screening ? screenings : selected;
                if (PARENTS.contains(table)) {
                    if (!owners.contains(id)
                            || !(screening ? screeningParents : parents).add(id)
                            || !values.equals(metadata.get(id))) invalid();
                } else {
                    boolean extension = EXTENSIONS.contains(table);
                    int parent = integer(values.get(extension ? "prevention_id" : "table_id"));
                    if (!owners.contains(parent)) invalid();
                    if (!extension
                            && (!"4".equals(values.get("table_name")) || !precisionIds.add(id)))
                        invalid();
                }
                counts.merge(table, 1L, Math::addExact);
            }
            table = null;
            key = null;
            values.clear();
            headers.clear();
        }

        @Override
        public void close() {
            closed = true;
            values.clear();
            headers.clear();
            parents.clear();
            screeningParents.clear();
            precisionIds.clear();
            bytes = null;
            pending = null;
        }
    }

    private static int integer(String text) throws IOException {
        try {
            int value = Integer.parseInt(text);
            if (!Integer.toString(value).equals(text)) invalid();
            return value;
        } catch (NumberFormatException failed) {
            throw new IOException("CDS_EXPORT_IMMUNIZATION_CAPTURE_INVALID");
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_IMMUNIZATION_CAPTURE_INVALID");
    }
}
