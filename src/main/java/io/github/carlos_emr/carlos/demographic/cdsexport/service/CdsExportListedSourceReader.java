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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Counts, inside the patient's capture snapshot, the selected patient's rows in each LISTED table
 * (records of removed CARLOS features, maintainer decision 20). Only the table name and the exact
 * row count are captured; no value of a listed row is ever read. The counts reach the encrypted
 * package ReadMe so the recipient knows what was not transferred; they never hold the patient.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportListedSourceReader implements CdsExportNativeSourceReader {
    public static final String TABLE = "listed_sources";

    private final CdsExportDataSummaryReadDao source;

    public CdsExportListedSourceReader(CdsExportDataSummaryReadDao source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public String table() {
        return TABLE;
    }

    @Override
    public Set<String> columns() {
        return Set.of("row_count", "table_name");
    }

    /** Demographics is part of every export, so every chart's listed rows are counted. */
    @Override
    public Set<CdsCategory> categories() {
        return Set.of(CdsCategory.DEMOGRAPHICS);
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        try (var input =
                getClass().getResourceAsStream("/cds-export/source-schema/listed_sources.json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Override
    public long count(int patient) {
        return count(patient, () -> {});
    }

    @Override
    public long count(int patient, Runnable checkpoint) {
        return source.listed(patient, checkpoint).size();
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
        for (var listed : source.listed(patient, checkpoint)) {
            checkpoint.run();
            output.row(
                    listed.table(),
                    row -> {
                        row.text("row_count", Long.toString(listed.rows()));
                        row.text("table_name", listed.table());
                    });
        }
    }
}
