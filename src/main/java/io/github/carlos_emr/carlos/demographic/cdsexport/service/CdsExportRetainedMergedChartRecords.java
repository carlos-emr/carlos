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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The merged-chart records ({@link CdsMergedChartContract}) a sending CARLOS wrote and this
 * CARLOS imported, among a chart's imported NewCategory units.
 *
 * <p>The importer brought every record of the sender's file onto one chart and kept these two
 * records as source only. They describe the sender's charts and their positions in the sender's
 * file, which no longer hold here, where the file became one chart. An export therefore never
 * writes them again as current records (a further CARLOS would read them as this file's own and
 * refuse it): their units are named in the ReadMe and stay in the import's retained source.
 */
final class CdsExportRetainedMergedChartRecords {
    /** ReadMe label of these records, one count per patient file. */
    static final String NOT_EXPORTED_LABEL =
            "Merged chart records received from another CARLOS (kept as import source)";

    /** Field rule of their retained fields. */
    static final String RULE = "RECEIVED_MERGED_CHART_RECORD_KEPT_AS_IMPORT_SOURCE";

    /** The source path of a NewCategory record's CategoryName, as the importer stores it. */
    private static final Pattern CATEGORY_NAME =
            Pattern.compile("(?:/[^/]+)*/NewCategory(?:\\[[0-9]+\\])?/CategoryName(?:\\[1\\])?");

    private CdsExportRetainedMergedChartRecords() {}

    /**
     * The applied NewCategory units ({@code run:unit}) of the chart's imported merged-chart
     * records, from the retained index: the occurrence unit each such CategoryName field belongs
     * to (a field is stored in its own source-field unit, linked to its occurrence's unit).
     */
    static SortedSet<String> units(CdsExportRetainedIndexDto index) {
        var units = new TreeSet<String>();
        for (var row : index.tables().getOrDefault("cds_import_field", Map.of()).values()) {
            Map<String, String> fields = row.fields();
            if (!name(fields.get("category"), fields.get("source_path"), fields.get("original_value")))
                continue;
            String parent = index.nativeParents().get(row.recordKey());
            if (parent != null && index.appliedUnits().contains(parent)) units.add(parent);
        }
        return units;
    }

    /** Whether one imported NewCategory unit, given its retained fields, is such a record. */
    static boolean received(Collection<CdsExportRetainedFieldDto> fields) {
        for (var field : fields)
            if (field.category() == CdsCategory.NEW_CATEGORY
                    && name(
                            field.category().wireName(),
                            field.sourcePath(),
                            field.originalInline())) return true;
        return false;
    }

    /** The CategoryName field of a NewCategory record that holds either record's name. */
    private static boolean name(String category, String path, String value) {
        return CdsCategory.NEW_CATEGORY.wireName().equals(category)
                && path != null
                && value != null
                && Set.of(CdsMergedChartContract.CHART_RECORD, CdsMergedChartContract.LINKS_RECORD)
                        .contains(value)
                && CATEGORY_NAME.matcher(path).matches();
    }
}
