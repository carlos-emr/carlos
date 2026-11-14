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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeRowReceiptDto;
import java.io.IOException;
import java.util.Set;

/**
 * Logical capture names for the problem sources CARLOS keeps outside the CPP notes: Disease
 * Registry entries ({@code dxresearch}) with their start-date precision rows, coded episodes
 * ({@code Episode}, pregnancies included) and the public code-table descriptions they name. All
 * four are captured only for ProblemList and are emitted at the ProblemList wire position after
 * the CPP Concerns items.
 *
 * <p>A registry entry the chart deleted (status {@code D}, which the registry list never shows)
 * and an episode saved as {@code Deleted} are captured and accounted field by field, but are not
 * current chart facts: they are neither an expected occurrence nor a reason to hold the patient.
 */
final class CdsExportProblemRegistrySources {
    static final String REGISTRY = "dxresearch",
            PRECISION = "dxresearch_precision",
            EPISODES = "Episode",
            REFERENCES = CdsExportDiagnosisCodeReferenceSourceReader.TABLE;
    static final Set<String> TABLES = Set.of(REGISTRY, PRECISION, EPISODES, REFERENCES);

    /** The registry's deleted status; the registry list query is {@code status<>'D'}. */
    static final String REGISTRY_DELETED = "D";

    /** The episode status the episode form saves for a deleted episode. */
    static final String EPISODE_DELETED = "Deleted";

    private CdsExportProblemRegistrySources() {}

    /** Physical table whose schema contract a logical capture name must match. */
    static String schemaTable(String table) {
        return PRECISION.equals(table) ? "partial_date" : table;
    }

    /** True for a deleted registry entry or episode, decided from the captured row. */
    static boolean removed(CdsExportCapturedTextRowDto row) throws IOException {
        if (REGISTRY.equals(row.table()))
            return REGISTRY_DELETED.equals(row.fields().get("status"));
        if (EPISODES.equals(row.table()))
            return EPISODE_DELETED.equals(row.fields().get("status"));
        throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_SCOPE_INVALID");
    }

    /** The same decision from an authenticated row witness, without reading clinical values. */
    static boolean removed(CdsExportNativeRowReceiptDto witness) throws IOException {
        if (witness.state() == null)
            throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_WITNESS_REQUIRED");
        if (REGISTRY.equals(witness.table()))
            return CdsExportImmunizationProjectionEvidence.matches(
                    witness.state(), "status", REGISTRY_DELETED);
        if (EPISODES.equals(witness.table()))
            return CdsExportImmunizationProjectionEvidence.matches(
                    witness.state(), "status", EPISODE_DELETED);
        throw new IOException("CDS_EXPORT_PROBLEM_REGISTRY_SCOPE_INVALID");
    }
}
