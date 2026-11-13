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

import io.github.carlos_emr.carlos.commn.dao.CdsLaboratoryResultDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Complete structured result, including blocked values; actual laboratory authority is required
 * before capture.
 *
 * <p>A patient's legacy-format reports (MDS, CML, BC PathNet) are exported as CARLOS data
 * summaries with Reports. A request that selects LaboratoryResults without Reports would leave
 * them out of the package, so its projection holds such a patient
 * ({@value #LEGACY_REPORTS_NEED_REPORTS}) instead of presenting an incomplete laboratory history.
 */
@Service
public class CdsExportLaboratorySourceReader implements CdsExportNativeSourceReader {
    static final String LEGACY_REPORTS_NEED_REPORTS = "CDS_EXPORT_LEGACY_LAB_REPORTS_NEED_REPORTS";

    private final CdsLaboratoryResultDao source;
    private final boolean legacyReportsExported;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportLaboratorySourceReader(CdsLaboratoryResultDao source) {
        this(source, true);
    }

    private CdsExportLaboratorySourceReader(
            CdsLaboratoryResultDao source, boolean legacyReportsExported) {
        this.source = Objects.requireNonNull(source);
        this.legacyReportsExported = legacyReportsExported;
    }

    /** The request's projection: whether the request also exports the legacy-format reports. */
    @Override
    public CdsExportNativeSourceReader forRequest(
            io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        boolean reports = command.categories().contains(CdsCategory.REPORTS);
        return reports == legacyReportsExported
                ? this
                : new CdsExportLaboratorySourceReader(source, reports);
    }

    @Override
    public String table() {
        return "cds_laboratory_result";
    }

    @Override
    public Set<String> columns() {
        return Set.copyOf(CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS.columns());
    }

    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.LABORATORY_RESULTS);
    }

    @Override
    public boolean requiresSensitiveLaboratoryRead() {
        return true;
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of(table(), "hl7TextMessage", "hl7TextInfo", "patientLabRouting");
    }

    @Override
    public List<CdsNativeRowDigest.Column> receiptColumns() {
        return source.readCdsExportReceiptColumns();
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
        capture(patient, output, () -> {});
    }

    @Override
    public void capture(
            int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint)
            throws IOException {
        String after = null;
        Objects.requireNonNull(checkpoint).run();
        if (!legacyReportsExported && source.hasCdsExportLegacyReports(patient))
            throw new io.github.carlos_emr.carlos.demographic.cdsexport.support
                    .CdsExportMappingReviewException(LEGACY_REPORTS_NEED_REPORTS);
        while (true) {
            checkpoint.run();
            var page = source.findCdsExportRowsAfter(patient, after, 32);
            if (page.size() > 32) invalid();
            for (var row : page) {
                checkpoint.run();
                if (row.id() == null
                        || !row.id().matches("[0-9a-f]{64}")
                        || after != null && row.id().compareTo(after) <= 0
                        || !row.cells().keySet().equals(columns())) invalid();
                output.row(
                        row.id(),
                        writer -> {
                            for (var field : row.cells().entrySet()) {
                                checkpoint.run();
                                if (field.getValue().sqlNull())
                                    writer.text(field.getKey(), (String) null);
                                else
                                    try (var input =
                                                    new CdsExportRawValueInputStream(
                                                            field.getValue(),
                                                            (offset, length) -> {
                                                                checkpoint.run();
                                                                return source
                                                                        .readCdsExportValueChunk(
                                                                                patient,
                                                                                row.id(),
                                                                                field.getKey(),
                                                                                offset,
                                                                                length);
                                                            });
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
            if (page.size() < 32) {
                checkpoint.run();
                return;
            }
        }
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_LABORATORY_PAGE_INVALID");
    }
}
