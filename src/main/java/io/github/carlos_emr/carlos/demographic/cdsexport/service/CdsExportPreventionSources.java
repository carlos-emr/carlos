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
import java.util.*;

/**
 * Logical capture names for one patient's prevention rows. A prevention row is either a vaccine
 * (Immunizations) or a screening (PastHealth, as the legacy exporter wrote it); the frozen
 * classification and its catalogue are shared by both categories. Screening rows use their own
 * logical tables over the same physical tables, so their witnesses and expected identities can
 * never be mistaken for vaccine rows, and selecting only one category never captures the other's
 * clinical values.
 */
final class CdsExportPreventionSources {
    static final String SCREENINGS = "preventions_screening",
            SCREENING_EXTENSIONS = "preventionsExt_screening",
            SCREENING_PRECISION = "prevention_precision_screening";
    /** Classification inputs read for either category; they hold no clinical prevention values. */
    static final Set<String> SHARED =
            Set.of("prevention_configuration", "preventions_scope", "CVCImmunization", "CVCMapping");
    static final Set<String> IMMUNIZATION_TABLES =
            Set.of("preventions", "preventionsExt", "prevention_precision");
    static final Set<String> SCREENING_TABLES =
            Set.of(SCREENINGS, SCREENING_EXTENSIONS, SCREENING_PRECISION);
    static final Set<String> SCREENING_REQUIRED = union(SHARED, SCREENING_TABLES);
    static final Set<CdsCategory> CATEGORIES =
            Set.of(CdsCategory.IMMUNIZATIONS, CdsCategory.PAST_HEALTH);

    private CdsExportPreventionSources() {}

    /** The prevention categories a request selected; a shared reader authorizes only these. */
    static Set<CdsCategory> selected(CdsExportCommand command) {
        var result = EnumSet.noneOf(CdsCategory.class);
        for (var category : command.categories()) if (CATEGORIES.contains(category)) result.add(category);
        if (result.isEmpty()) throw new IllegalStateException("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        return Set.copyOf(result);
    }

    /**
     * Physical table whose schema contract a logical prevention capture name must match. Row
     * witnesses of the screening aliases deliberately keep their logical names instead.
     */
    static String schemaTable(String table) {
        return switch (table) {
            case SCREENINGS -> "preventions";
            case SCREENING_EXTENSIONS -> "preventionsExt";
            case SCREENING_PRECISION, "prevention_precision" -> "partial_date";
            default -> table;
        };
    }

    static Set<String> union(Set<String> first, Set<String> second) {
        var result = new HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
