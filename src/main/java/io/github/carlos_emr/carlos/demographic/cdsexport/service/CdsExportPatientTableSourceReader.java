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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Mechanical bounded reader for the explicitly declared patient-owned native source plans. */
abstract class CdsExportPatientTableSourceReader implements CdsExportNativeSourceReader {
    private final CdsExportPatientTableReadDao source;
    private final CdsExportPatientTableReadSupport.Plan plan;
    private final Set<CdsCategory> readCategories;

    CdsExportPatientTableSourceReader(
            CdsExportPatientTableReadDao source, CdsExportPatientTableReadSupport.Plan plan) {
        this(source, plan, Set.of(CdsCategory.DEMOGRAPHICS));
    }

    CdsExportPatientTableSourceReader(
            CdsExportPatientTableReadDao source,
            CdsExportPatientTableReadSupport.Plan plan,
            Set<CdsCategory> categories) {
        this.source = source;
        this.plan = plan;
        readCategories = Set.copyOf(categories);
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
        return readCategories;
    }

    @Override
    public java.util.List<
                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column>
            receiptColumns() {
        var columns = source.readCdsExportReceiptColumns();
        // The frozen recipe can prove the selected fields; the receipt reader explicitly marks
        // this reviewed projection as partial, so its digest cannot match a whole-row receipt.
        return plan == CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS
                ? columns.stream().filter(column -> plan.columns().contains(column.name())).toList()
                : columns;
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return source.readCdsExportSourceSchema();
    }

    @Override
    public long count(int patient) {
        return source.countCdsExportRows(patient);
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        Long after = null;
        while (true) {
            var page = page(patient, after, 32);
            if (page.size() > 32) throw new IOException("CDS_EXPORT_NATIVE_PAGE_INVALID");
            for (var row : page) {
                if (after != null && row.id() <= after || !row.cells().keySet().equals(columns()))
                    throw new IOException("CDS_EXPORT_NATIVE_PAGE_INVALID");
                output.row(
                        key(row.id()),
                        writer -> {
                            for (var field : row.cells().entrySet()) {
                                if (field.getValue().sqlNull())
                                    writer.text(field.getKey(), (String) null);
                                else if (field.getValue().exactNumeric() != null) {
                                    byte[] display = field.getValue().inlineBytes();
                                    if (display == null || display.length > 128)
                                        throw new IOException("CDS_EXPORT_NUMERIC_DISPLAY_INVALID");
                                    writer.numeric(
                                            field.getKey(),
                                            StandardCharsets.UTF_8
                                                    .newDecoder()
                                                    .onMalformedInput(CodingErrorAction.REPORT)
                                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                                                    .decode(java.nio.ByteBuffer.wrap(display))
                                                    .toString(),
                                            field.getValue().exactNumeric());
                                } else
                                    try (var input =
                                                    new CdsExportRawValueInputStream(
                                                            field.getValue(),
                                                            (offset, length) ->
                                                                    chunk(
                                                                            patient,
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

    protected java.util.List<io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto> page(
            int patient, Long after, int limit) {
        return source.findCdsExportRowsAfter(patient, after, limit);
    }

    protected byte[] chunk(int patient, long row, String column, long offset, int length) {
        return source.readCdsExportValueChunk(patient, row, column, offset, length);
    }

    /** Order-preserving, lossless key for the entire signed BIGINT range, including legacy negative/zero IDs. */
    static String key(long id) {
        String value =
                BigInteger.valueOf(id).subtract(BigInteger.valueOf(Long.MIN_VALUE)).toString();
        return "0".repeat(20 - value.length()) + value;
    }
}
