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
        var chain = new Chain();
        for (var witness : witnesses)
            if (witness.table().equals(table)) chain.row(witness);
            else if (witness.table().equals(extensions)) chain.link(witness);
        return chain.current(starts);
    }

    /**
     * The edit chains of a chart's prevention rows, kept without their witnesses: whether each
     * row is superseded, and for each recorded edit the digests of the two row ids it joins.
     * Rows and edits may arrive in any order and from several passes. A section that reads its
     * rows window by window fills one from the links first and then only from the rows those
     * links or its starting rows name ({@link #names}), so it holds two digests for each edit
     * and a flag for each edited row instead of every row's witness.
     */
    static final class Chain {
        /** 0 current, 1 superseded, 2 a witness that cannot say: refused when a chain reaches it. */
        private final Map<String, Byte> states = new HashMap<>();

        /** Why a row's witness cannot say, kept for the rare row it concerns. */
        private final Map<String, IOException> refused = new HashMap<>();
        private final Map<String, String> ids = new HashMap<>();
        private final List<String[]> links = new ArrayList<>();
        private final Set<String> named = new HashSet<>();

        /** The first row or link that cannot be used: refused when the chains are asked for. */
        private IOException failure;

        /** Records one prevention row; a row recorded twice is refused by {@link #current}. */
        void row(CdsExportNativeRowReceiptDto witness) {
            byte state;
            try {
                state = (byte) (CdsExportPreventionVersions.superseded(witness) ? 1 : 0);
            } catch (IOException unreadable) {
                // Decided only when a chain reaches the row, as it was when the witness was kept.
                state = 2;
                refused.putIfAbsent(witness.id(), unreadable);
            }
            if (states.putIfAbsent(witness.id(), state) != null && failure == null)
                failure = new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
            ids.put(text(witness.id()), witness.id());
        }

        /**
         * Records one extension row when it names the version its prevention replaced. A row
         * whose witness cannot say is refused by {@link #current}, not here: a caller that
         * follows no chain never asked.
         */
        void link(CdsExportNativeRowReceiptDto witness) {
            try {
                if (witness.state() == null)
                    throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
                if (!CdsExportCapturedTextWitness.matches(
                        witness.state(), "keyval", "previousId")) return;
                String owner = cell(witness, "prevention_id"), previous = cell(witness, "val");
                links.add(new String[] {owner, previous});
                if (owner != null) named.add(owner);
                if (previous != null) named.add(previous);
            } catch (IOException unusable) {
                if (failure == null) failure = unusable;
            }
        }

        /** Whether a recorded link names the prevention row with this id. */
        boolean names(String id) {
            return named.contains(text(id));
        }

        /** The answer of {@link CdsExportPreventionVersions#currentVersions} for these rows. */
        Map<String, String> current(Set<String> starts) throws IOException {
            if (failure != null) throw failure;
            var replacedBy = new HashMap<String, List<String>>();
            for (var link : links) {
                String owner = ids.get(link[0]), previous = ids.get(link[1]);
                if (owner == null || previous == null || owner.equals(previous)) continue;
                replacedBy.computeIfAbsent(previous, ignored -> new ArrayList<>()).add(owner);
            }
            var result = new HashMap<String, String>();
            for (String start : starts) {
                if (!states.containsKey(start) || !superseded(start)) continue;
                String at = start, found = null;
                var seen = new HashSet<String>();
                while (seen.add(at)) {
                    var next = replacedBy.getOrDefault(at, List.of());
                    if (next.isEmpty()) break;
                    if (next.size() > 1)
                        throw new CdsExportReviewException(
                                CdsExportReviewException.Reason.AMBIGUOUS_SOURCE);
                    at = next.getFirst();
                    if (!superseded(at)) {
                        found = at;
                        break;
                    }
                }
                result.put(start, found);
            }
            return result;
        }

        private boolean superseded(String id) throws IOException {
            Byte state = states.get(id);
            if (state == null)
                throw new IOException("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
            if (state == 2) throw refused.get(id);
            return state == 1;
        }

        @Override
        public String toString() {
            return "CdsExportPreventionVersions.Chain[protected]";
        }
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
