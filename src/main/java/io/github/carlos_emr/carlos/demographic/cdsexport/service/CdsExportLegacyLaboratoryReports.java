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
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The legacy-format laboratory reports (the MDS, CML and BC PathNet routes of older releases),
 * which are exported as Lab Report data summaries in Reports, and where they go when a job
 * selects LaboratoryResults but not Reports.
 *
 * <p>Owner decision (2026-11-14): such a job still exports them, as Lab Report entries in the
 * Reports section, and never holds the patient for them; nothing else from Reports is exported.
 * The source catalogue states it: their data-summary tables (DOCUMENT) are captured with
 * LaboratoryResults as well as with Reports ({@link #tables}), and only they are. A patient file
 * of such a job therefore may hold a Reports section ({@link #sections}), which the inventory,
 * the field receipts and the XML check bind to these records alone. The read right of these
 * records is LaboratoryResults' ({@link #authority}). The job reports how many went out that way
 * ({@code CdsExportPackageMetadataDto.legacyLaboratoryReports}).
 *
 * @since 2026-11-14
 */
public final class CdsExportLegacyLaboratoryReports {
    /** The record kinds of the legacy-format laboratory reports. */
    static final Set<CdsExportDataSummaryRegistry.Kind> KINDS =
            Set.copyOf(
                    EnumSet.of(
                            CdsExportDataSummaryRegistry.Kind.MDS_LAB_REPORT,
                            CdsExportDataSummaryRegistry.Kind.CML_LAB_REPORT,
                            CdsExportDataSummaryRegistry.Kind.PATHNET_LAB_REPORT));

    private CdsExportLegacyLaboratoryReports() {}

    /**
     * The data-summary tables of the legacy reports: the DOCUMENT tables the source catalogue
     * captures with LaboratoryResults.
     */
    static Set<String> tables() {
        var catalogue = CdsExportSourceCatalogue.current();
        var result = new TreeSet<>(catalogue.required(Set.of(CdsCategory.LABORATORY_RESULTS)));
        result.retainAll(catalogue.tables(CdsExportSourceCatalogue.Disposition.DOCUMENT));
        return Set.copyOf(result);
    }

    /** Whether the job writes the legacy reports in Reports although Reports is not selected. */
    public static boolean withoutReports(Set<CdsCategory> selected) {
        return selected.contains(CdsCategory.LABORATORY_RESULTS)
                && !selected.contains(CdsCategory.REPORTS);
    }

    static boolean withoutReports(CdsExportCommand command) {
        return withoutReports(command.categories());
    }

    /**
     * The categories a patient file of a job with these selected categories may hold: the
     * selected ones, and Reports when only the legacy laboratory reports are written there.
     */
    public static Set<CdsCategory> sections(Set<CdsCategory> selected) {
        if (!withoutReports(selected)) return selected;
        var result = EnumSet.copyOf(selected);
        result.add(CdsCategory.REPORTS);
        return java.util.Collections.unmodifiableSet(result);
    }

    /**
     * Whether a data-summary capture scope is that of the legacy reports alone in a job without
     * Reports: the job selects LaboratoryResults but not Reports, and every data-summary table
     * of the scope is a legacy report table.
     */
    static boolean onlyLegacy(CdsExportCommand command, Set<String> dataSummaryTables) {
        return withoutReports(command) && tables().containsAll(dataSummaryTables);
    }

    /**
     * The category whose read right covers the data summaries of this job: Reports, or
     * LaboratoryResults when only the legacy laboratory reports are written (Reports not
     * selected).
     */
    static CdsCategory authority(CdsExportCommand command) {
        return withoutReports(command) ? CdsCategory.LABORATORY_RESULTS : CdsCategory.REPORTS;
    }

    /** The number of legacy laboratory reports in a chart's data-summary counts (kind label to count). */
    static long count(Map<String, Long> dataSummaries) {
        long total = 0;
        for (var kind : KINDS)
            total = Math.addExact(total, dataSummaries.getOrDefault(kind.label(), 0L));
        return total;
    }
}
