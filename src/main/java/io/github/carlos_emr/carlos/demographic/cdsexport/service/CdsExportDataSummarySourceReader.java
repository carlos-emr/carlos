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

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Captures every physical column of one reviewed data-summary (DOCUMENT) table for the selected
 * patient, into the same authenticated per-patient capture as the mapped tables. One reader exists
 * per DOCUMENT table of the source catalogue; all are built from the pinned physical contract by
 * {@link #readers}, never written by hand per table. They serve Reports only, so a chart is read
 * for them only when Reports is selected.
 *
 * <p>Character, number and date columns are captured as their exact UTF-8 text; binary columns as
 * their exact bytes; SQL NULL stays distinct from empty. No number of rows or fields is refused:
 * rows are streamed into the capture a page at a time. A table whose row identity proves not
 * unique holds only that patient ({@value #KEY_NOT_UNIQUE}), because keyed paging could then skip
 * a row; the job continues.
 *
 * @since 2026-11-13
 */
final class CdsExportDataSummarySourceReader implements CdsExportNativeSourceReader {
    static final String KEY_NOT_UNIQUE = "CDS_EXPORT_DATA_SUMMARY_ROW_IDENTITY_NOT_UNIQUE";

    private final CdsExportDataSummaryReadDao source;
    private final CdsExportDataSummaryReadDao.Plan plan;
    /** The plan's columns as a set, built once: every read of a capture compares it. */
    private final Set<String> columns;

    CdsExportDataSummaryReadDao.Plan plan() {
        return plan;
    }

    CdsExportDataSummarySourceReader(
            CdsExportDataSummaryReadDao source, CdsExportDataSummaryReadDao.Plan plan) {
        this.source = Objects.requireNonNull(source);
        this.plan = Objects.requireNonNull(plan);
        this.columns = Set.copyOf(plan.columns());
    }

    /** One reader per DOCUMENT table of the pinned catalogue, in table order. */
    static List<CdsExportNativeSourceReader> readers(CdsExportDataSummaryReadDao source) {
        var result = new ArrayList<CdsExportNativeSourceReader>();
        for (String table :
                CdsExportSourceCatalogue.current()
                        .tables(CdsExportSourceCatalogue.Disposition.DOCUMENT))
            result.add(
                    new CdsExportDataSummarySourceReader(
                            source, CdsExportDataSummaryReadDao.plan(table)));
        return List.copyOf(result);
    }

    @Override
    public String table() {
        return plan.table();
    }

    @Override
    public Set<String> columns() {
        return columns;
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.REPORTS);
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return source.schema(plan);
    }

    @Override
    public long count(int patient) {
        return source.count(plan, patient);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        long expected = count(patient);
        if (expected < 0) throw new IOException("CDS_EXPORT_CAPTURE_COUNT_INVALID");
        int limit = Math.max(1, Math.min(256, 4096 / plan.columns().size()));
        List<Object> after = null;
        long ordinal = 0;
        while (true) {
            var page = source.page(plan, patient, after, limit);
            if (page.size() > limit) throw new IOException("CDS_EXPORT_NATIVE_PAGE_INVALID");
            for (var row : page) {
                if (!row.cells().keySet().equals(columns()))
                    throw new IOException("CDS_EXPORT_NATIVE_PAGE_INVALID");
                if (++ordinal > expected) throw new CdsExportMappingReviewException(KEY_NOT_UNIQUE);
                String key =
                        plan.numericIdentity()
                                ? CdsExportPatientTableSourceReader.key((Long) row.key().getFirst())
                                : String.format(Locale.ROOT, "%020d", ordinal);
                output.row(
                        key,
                        writer -> {
                            for (var field : row.cells().entrySet()) {
                                String column = field.getKey();
                                var cell = field.getValue();
                                if (cell.sqlNull()) {
                                    writer.text(column, (String) null);
                                    continue;
                                }
                                try (var input =
                                        new CdsExportRawValueInputStream(
                                                cell,
                                                (offset, length) ->
                                                        source.chunk(
                                                                plan,
                                                                patient,
                                                                row.key(),
                                                                column,
                                                                offset,
                                                                length))) {
                                    if (plan.binary().contains(column))
                                        writer.binary(column, input);
                                    else
                                        try (var text =
                                                new InputStreamReader(
                                                        input,
                                                        StandardCharsets.UTF_8
                                                                .newDecoder()
                                                                .onMalformedInput(
                                                                        CodingErrorAction.REPORT)
                                                                .onUnmappableCharacter(
                                                                        CodingErrorAction
                                                                                .REPORT))) {
                                            writer.text(column, text);
                                        }
                                }
                            }
                        });
            }
            if (page.size() < limit) break;
            after = page.getLast().key();
        }
        // A duplicate row identity would let keyed paging skip a row: the independent count
        // proves every row was captured, or the chart is held instead of losing it.
        if (ordinal != expected) throw new CdsExportMappingReviewException(KEY_NOT_UNIQUE);
    }
}
