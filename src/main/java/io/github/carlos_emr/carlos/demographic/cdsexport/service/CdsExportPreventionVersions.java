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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.util.*;

/**
 * Version state of a native prevention row. CARLOS never updates a prevention in place:
 * {@code PreventionData.updatetPreventionData} soft-deletes the edited row ({@code deleted='1'})
 * and inserts the new version, and a user delete also only sets {@code deleted='1'}. The legacy
 * CDS exporter read {@code PreventionDao.findNotDeletedByDemographicId}, so those versions were
 * never exported. They stay in the capture, and every field of the version, its extensions and
 * its precision rows receives an explicit not-exported disposition; they are neither an
 * occurrence nor a reason to hold the patient. A legacy NULL flag records no deletion and is
 * current; any other value is unknown and still requires review.
 */
final class CdsExportPreventionVersions {
    static final String SUPERSEDED = "SUPERSEDED_OR_REMOVED_PREVENTION_VERSION";

    private CdsExportPreventionVersions() {}

    /** True only for the native soft-delete flag; NULL, "0" and unknown values are not. */
    static boolean superseded(CdsExportCapturedTextRowDto parent) {
        return "1".equals(parent.fields().get("deleted"));
    }

    /** "0" or a legacy NULL: the current version of a prevention. */
    static boolean current(String deleted) {
        return deleted == null || "0".equals(deleted);
    }

    /** Same decision from an authenticated row witness, without reading the clinical values. */
    static boolean superseded(CdsExportNativeRowReceiptDto witness) throws IOException {
        if (witness.state() == null)
            throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
        return CdsExportImmunizationProjectionEvidence.matches(witness.state(), "deleted", "1");
    }

    /**
     * The current version an edit chain leads to from each superseded starting row. An edit of a
     * prevention records the version it replaced in a {@code previousId} extension of the new
     * version ({@code AddPrevention2Action}); a removal records nothing. The relation is read from
     * the row witnesses alone, so the inventory and the vaccine assembly reach the same answer
     * before any clinical value is read.
     *
     * @param starts prevention row ids to follow (the rows of imported vaccines)
     * @param witnesses captured witnesses; rows of {@code table} and of {@code extensions} are used
     * @param table the logical prevention table of the starting rows
     * @param extensions the logical extension table of those rows
     * @return for each start whose row is superseded, the id of the current version that replaced
     *     it, or null when the chain ends in a removal; a start that is still current is absent
     * @throws CdsExportReviewException when one version was replaced twice, which no CARLOS screen
     *     records (an edit requires the edited version to be current)
     */
    static Map<String, String> currentVersions(
            Set<String> starts,
            Collection<CdsExportNativeRowReceiptDto> witnesses,
            String table,
            String extensions)
            throws IOException {
        var rows = new HashMap<String, CdsExportNativeRowReceiptDto>();
        var ids = new HashMap<String, String>();
        for (var witness : witnesses)
            if (witness.table().equals(table)) {
                if (rows.putIfAbsent(witness.id(), witness) != null)
                    throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
                ids.put(text(witness.id()), witness.id());
            }
        var replacedBy = new HashMap<String, List<String>>();
        for (var witness : witnesses) {
            if (!witness.table().equals(extensions)) continue;
            if (witness.state() == null)
                throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
            if (!CdsExportCapturedTextWitness.matches(witness.state(), "keyval", "previousId"))
                continue;
            String owner = ids.get(cell(witness, "prevention_id")),
                    previous = ids.get(cell(witness, "val"));
            if (owner == null || previous == null || owner.equals(previous)) continue;
            replacedBy.computeIfAbsent(previous, ignored -> new ArrayList<>()).add(owner);
        }
        var result = new HashMap<String, String>();
        for (String start : starts) {
            var witness = rows.get(start);
            if (witness == null || !superseded(witness)) continue;
            String at = start, found = null;
            var seen = new HashSet<String>();
            while (seen.add(at)) {
                var next = replacedBy.getOrDefault(at, List.of());
                if (next.isEmpty()) break;
                if (next.size() > 1)
                    throw new CdsExportReviewException(
                            CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
                at = next.getFirst();
                if (!superseded(rows.get(at))) {
                    found = at;
                    break;
                }
            }
            result.put(start, found);
        }
        return result;
    }

    private static String cell(CdsExportNativeRowReceiptDto witness, String column)
            throws IOException {
        var state = witness.state();
        for (int n = 0; n < state.columns().size(); n++)
            if (state.columns().get(n).name().equals(column)) {
                var cell = state.cells().get(n);
                return cell.sqlNull() ? null : cell.bytes() + ":" + cell.sha256();
            }
        throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
    }

    private static String text(String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return bytes.length
                + ":"
                + java.util.HexFormat.of()
                        .formatHex(
                                io.github.carlos_emr.carlos.utility.CdsImportKeys.digest()
                                        .digest(bytes));
    }

    /** One explicit not-exported disposition for every captured field of a superseded version. */
    static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> dispositions(
            List<CdsExportCapturedTextRowDto> rows) {
        var result = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        for (var row : rows) {
            var fields = new CdsExportNativeFieldAssessments(row, result);
            for (String column : row.fields().keySet()) fields.operational(column, SUPERSEDED);
        }
        return result;
    }
}
