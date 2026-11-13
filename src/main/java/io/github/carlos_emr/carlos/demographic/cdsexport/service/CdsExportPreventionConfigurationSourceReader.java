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
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.*;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Explicit trusted-configuration source; no SQL table or native row receipt is claimed. Exact bytes also drive selection in this same snapshot. */
@Service
public class CdsExportPreventionConfigurationSourceReader implements CdsExportNativeSourceReader {
    private final CdsExportPreventionSnapshotService snapshots;
    private final Set<CdsCategory> selected;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportPreventionConfigurationSourceReader(
            CdsExportPreventionSnapshotService snapshots) {
        this(snapshots, CdsExportPreventionSources.CATEGORIES);
    }

    private CdsExportPreventionConfigurationSourceReader(
            CdsExportPreventionSnapshotService snapshots, Set<CdsCategory> selected) {
        this.snapshots = java.util.Objects.requireNonNull(snapshots);
        this.selected = Set.copyOf(selected);
    }

    @Override
    public Set<CdsCategory> supportedCategories() {
        return CdsExportPreventionSources.CATEGORIES;
    }

    /** Vaccines and screenings share one classification; only the selected categories are authorized. */
    @Override
    public CdsExportNativeSourceReader forRequest(
            io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command) {
        return new CdsExportPreventionConfigurationSourceReader(
                snapshots, CdsExportPreventionSources.selected(command));
    }

    @Override
    public String table() {
        return "prevention_configuration";
    }

    @Override
    public Set<String> columns() {
        return Set.of("sha256", "xml");
    }

    @Override
    public Set<CdsCategory> categories() {
        return selected;
    }

    @Override
    public Set<String> requiredTables() {
        return Set.of("CVCImmunization", "CVCMapping", "preventions_scope");
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/cds-export/source-schema/prevention_configuration.json")) {
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
        try {
            snapshots.prepare(patient, checkpoint);
            return 1;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        var state = snapshots.current(patient);
        output.row(
                "configuration",
                writer -> {
                    writer.text("sha256", state.configurationSha256());
                    try (var xml = state.xml()) {
                        writer.binary("xml", xml);
                    }
                });
    }
}
