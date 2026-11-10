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
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Same explicit clinic setting used for native appointment import. Only this finite public configuration enters capture. */
@Service
public class CdsExportClinicalZoneSourceReader implements CdsExportNativeSourceReader {
    @FunctionalInterface
    private interface ZoneSource {
        CdsExportClinicalZone.Snapshot read() throws IOException;
    }

    private final ZoneSource configured;

    @Autowired
    public CdsExportClinicalZoneSourceReader(CdsExportActivePlanService plans) {
        this.configured = Objects.requireNonNull(plans)::zone;
    }

    public CdsExportClinicalZoneSourceReader(String configured) {
        String value = configured == null ? "" : configured.trim();
        this.configured = () -> CdsExportClinicalZone.capture(value);
    }

    @Override
    public String table() {
        return "clinical_timezone";
    }

    @Override
    public Set<String> columns() {
        return Set.of("sha256", "tzdbVersion", "zoneId");
    }

    @Override
    public Set<CdsCategory> categories() {
        var categories = EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.APPOINTMENTS);
        categories.add(CdsCategory.IMMUNIZATIONS);
        categories.add(CdsCategory.REPORTS);
        return Set.copyOf(categories);
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        try (var input =
                getClass()
                        .getResourceAsStream("/cds-export/source-schema/clinical_timezone.json")) {
            if (input == null) throw new IOException("CDS_EXPORT_SOURCE_SCHEMA_CONTRACT_MISSING");
            return new ObjectMapper().readValue(input, CdsExportTableSchemaDto.class);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Override
    public long count(int patient) {
        if (patient < 1)
            throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_ZONE_SCOPE_INVALID");
        try {
            configured.read();
            return 1;
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output)
            throws IOException {
        if (patient < 1)
            throw new IllegalArgumentException("CDS_EXPORT_CLINICAL_ZONE_SCOPE_INVALID");
        var frozen = configured.read();
        output.row(
                "configuration",
                row -> {
                    row.text("sha256", frozen.sha256());
                    row.text("tzdbVersion", frozen.tzdbVersion());
                    row.text("zoneId", frozen.zoneId());
                });
    }
}
