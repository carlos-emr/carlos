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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.util.Set;

/** Decodes captured field metadata without current-row lookups, normalizing originals or conflating field/native units. */
public final class CdsExportRetainedFieldMapper {
    private final java.util.function.UnaryOperator<String> shared;

    public CdsExportRetainedFieldMapper() {
        this(java.util.function.UnaryOperator.identity());
    }

    /**
     * A mapper whose fields share their repeated texts. Every field of a chart repeats its run,
     * patient key, unit key, namespace, value type and, across records, its source path; a caller
     * that keeps every field of a long imported chart passes one canonical instance of each
     * through {@code shared} so a field costs its own values, not a copy of those. {@code shared}
     * must return an equal string (or null for null); the field is the same value either way.
     */
    public CdsExportRetainedFieldMapper(java.util.function.UnaryOperator<String> shared) {
        this.shared = java.util.Objects.requireNonNull(shared);
    }

    public CdsExportRetainedFieldDto map(
            int patient, Set<CdsCategory> selected, CdsExportCapturedTextRowDto row)
            throws IOException {
        try {
            if (patient < 1
                    || !"cds_import_field".equals(row.table())
                    || !row.fields()
                            .keySet()
                            .equals(
                                    Set.copyOf(
                                            CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS
                                                    .columns()))) invalid();
            var fields = row.fields();
            if (!Integer.toString(patient).equals(fields.get("demographic_no"))
                    || !row.recordKey().equals(fields.get("id"))) invalid();
            var category = CdsCategory.fromWireName(fields.get("category"));
            if (!selected.contains(category)) invalid();
            long ordinal = integer(fields.get("occurrence")),
                    offset = integer(fields.get("payload_offset")),
                    bytes = integer(fields.get("payload_length"));
            if (!row.recordKey()
                    .equals(
                            CdsImportKeys.hash(
                                    fields.get("unit_key"),
                                    fields.get("source_namespace"),
                                    fields.get("source_path"),
                                    Long.toString(ordinal)))) invalid();
            CdsExportRetainedFieldDto.Payload payload = null;
            if (fields.get("payload_location") != null)
                payload =
                        new CdsExportRetainedFieldDto.Payload(
                                shared.apply(fields.get("payload_location")),
                                fields.get("payload_digest"),
                                offset,
                                bytes);
            else if (fields.get("payload_digest") != null || offset != 0 || bytes != 0) invalid();
            return new CdsExportRetainedFieldDto(
                    row.recordKey(),
                    shared.apply(fields.get("run_id")),
                    shared.apply(fields.get("patient_key")),
                    shared.apply(fields.get("unit_key")),
                    category,
                    shared.apply(fields.get("source_path")),
                    ordinal,
                    shared.apply(fields.get("source_namespace")),
                    shared.apply(fields.get("value_type")),
                    shared.apply(fields.get("date_precision")),
                    fields.get("original_value"),
                    fields.get("normalized_value"),
                    payload);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new IOException("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
        }
    }

    private static long integer(String value) throws IOException {
        try {
            long number = Long.parseLong(value);
            if (number < 0 || !Long.toString(number).equals(value)) invalid();
            return number;
        } catch (NumberFormatException failure) {
            invalid();
            return 0;
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_RETAINED_FIELD_REQUIRES_REVIEW");
    }
}
