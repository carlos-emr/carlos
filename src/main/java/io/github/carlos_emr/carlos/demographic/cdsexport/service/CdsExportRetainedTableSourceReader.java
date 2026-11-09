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
import io.github.carlos_emr.carlos.commn.dao.CdsExportRetainedTableReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request-bound retained evidence reader. All original/logical values, unit edges and
 * prepared values share the native patient snapshot. Referenced files must separately be
 * copied and authenticated before interpreting this as complete retained-source evidence.
 */
class CdsExportRetainedTableSourceReader implements CdsExportNativeSourceReader {
    private final CdsExportRetainedTableReadDao source;
    private final CdsExportPatientTableReadSupport.Plan plan;
    private final Set<CdsCategory> selected;
    private final Set<String> categories;

    CdsExportRetainedTableSourceReader(
            CdsExportRetainedTableReadDao source, CdsExportPatientTableReadSupport.Plan plan) {
        this(source, plan, Set.of(CdsCategory.DEMOGRAPHICS));
    }

    private CdsExportRetainedTableSourceReader(
            CdsExportRetainedTableReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            Set<CdsCategory> selected) {
        this.source = java.util.Objects.requireNonNull(source);
        this.plan = java.util.Objects.requireNonNull(plan);
        this.selected = Set.copyOf(selected);
        categories =
                selected.stream()
                        .map(CdsCategory::wireName)
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public CdsExportNativeSourceReader forRequest(CdsExportCommand command) {
        return new CdsExportRetainedTableSourceReader(
                source,
                plan,
                plan == CdsExportPatientTableReadSupport.Plan.IMPORT_PATIENTS
                                || plan == CdsExportPatientTableReadSupport.Plan.IMPORT_RUNS
                        ? Set.of(CdsCategory.DEMOGRAPHICS)
                        : command.categories());
    }

    @Override
    public String table() {
        return plan.table();
    }

    @Override
    public Set<String> columns() {
        return Set.copyOf(plan.columns());
    }

    @Override
    public Set<CdsCategory> categories() {
        return selected;
    }

    @Override
    public boolean requiresSensitiveLaboratoryRead() {
        return selected.contains(CdsCategory.LABORATORY_RESULTS);
    }

    @Override
    public java.util.List<
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column>
            receiptColumns() {
        return source.readCdsExportReceiptColumns();
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return source.readCdsExportRetainedSchema();
    }

    @Override
    public long count(int patient) {
        return source.countCdsExportRetainedRows(patient, categories);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        String after = null;
        while (true) {
            var page = source.findCdsExportRetainedRowsAfter(patient, categories, after, 32);
            if (page.size() > 32) throw new IOException("CDS_EXPORT_RETAINED_PAGE_INVALID");
            for (var row : page) {
                if (!plan.validRetainedKey(row.id())
                        || after != null && row.id().compareTo(after) <= 0
                        || !row.cells().keySet().equals(columns()))
                    throw new IOException("CDS_EXPORT_RETAINED_PAGE_INVALID");
                output.row(
                        row.id(),
                        writer -> {
                            for (var field : row.cells().entrySet()) {
                                if (field.getValue().sqlNull())
                                    writer.text(field.getKey(), (String) null);
                                else
                                    try (var input =
                                                    new CdsExportRawValueInputStream(
                                                            field.getValue(),
                                                            (offset, length) ->
                                                                    source
                                                                            .readCdsExportRetainedChunk(
                                                                                    patient,
                                                                                    categories,
                                                                                    row.id(),
                                                                                    field.getKey(),
                                                                                    offset,
                                                                                    length));
                                            var text =
                                                    new InputStreamReader(
                                                            input,
                                                            StandardCharsets.UTF_8
                                                                    .newDecoder()
                                                                    .onMalformedInput(
                                                                            CodingErrorAction
                                                                                    .REPORT)
                                                                    .onUnmappableCharacter(
                                                                            CodingErrorAction
                                                                                    .REPORT))) {
                                        writer.text(field.getKey(), text);
                                    }
                            }
                        });
                after = row.id();
            }
            if (page.size() < 32) return;
        }
    }
}
