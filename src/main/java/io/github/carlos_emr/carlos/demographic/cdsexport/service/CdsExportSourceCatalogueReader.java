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

import io.github.carlos_emr.carlos.commn.dao.CdsExportSourceCatalogueDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import java.util.*;
import org.springframework.stereotype.Service;

/** Every registered clinical request includes independent whole-schema and unclassified-source closure. */
@Service
public class CdsExportSourceCatalogueReader implements CdsExportNativeSourceReader {
    private final CdsExportSourceCatalogueDao source;
    private final Set<CdsCategory> selected;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportSourceCatalogueReader(CdsExportSourceCatalogueDao source) {
        this(source, Set.copyOf(EnumSet.allOf(CdsCategory.class)));
    }

    private CdsExportSourceCatalogueReader(
            CdsExportSourceCatalogueDao source, Set<CdsCategory> selected) {
        this.source = Objects.requireNonNull(source);
        this.selected = Set.copyOf(selected);
    }

    @Override
    public CdsExportNativeSourceReader forRequest(CdsExportCommand command) {
        return new CdsExportSourceCatalogueReader(source, command.categories());
    }

    @Override
    public String table() {
        return "clinical_source_catalogue";
    }

    @Override
    public Set<String> columns() {
        return Set.of("catalogueSha256");
    }

    @Override
    public Set<CdsCategory> categories() {
        return selected;
    }

    @Override
    public Set<String> requiredTables() {
        return CdsExportSourceCatalogue.current().required(selected);
    }

    @Override
    public CdsExportTableSchemaDto schema() {
        return source.readSourceSchema();
    }

    @Override
    public long count(int patient) {
        return count(patient, () -> {});
    }

    @Override
    public long count(int patient, Runnable checkpoint) {
        source.requireUnclassifiedSourcesEmpty(patient, checkpoint);
        return 0;
    }

    @Override
    public void capture(int patient, CdsExportNativeCaptureComposer.Writer output) {
        source.requireUnclassifiedSourcesEmpty(patient, () -> {});
    }

    @Override
    public void capture(
            int patient, CdsExportNativeCaptureComposer.Writer output, Runnable checkpoint) {
        source.requireUnclassifiedSourcesEmpty(patient, checkpoint);
    }
}
