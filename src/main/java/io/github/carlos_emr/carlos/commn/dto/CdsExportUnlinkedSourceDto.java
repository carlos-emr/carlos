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
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The number of rows of one reviewed inbox table that are linked to no patient when an export job
 * runs: a lab or HRM report not yet matched to a chart, or the routing and reviewers of an
 * incoming document not yet filed. They belong to no chart, so no patient is held for them and
 * none is exported; the job reports the count so the exclusion is visible, never silent.
 *
 * <p>Only a table name from the fixed list below and a count: no identifier, row or value. The
 * export run ledger records each count as one {@code UNLINKED_SOURCE_ROWS} event whose safe code
 * is {@link #eventCode()}, so the diagnostic report and audit trail carry it without a schema
 * change.
 *
 * @since 2026-11-13
 */
public record CdsExportUnlinkedSourceDto(String table, long rows) {
    /** Ledger event type of one table's count. */
    public static final String EVENT = "UNLINKED_SOURCE_ROWS";

    /** The reviewed inbox tables and the fixed safe-code token of each. */
    private static final Map<String, String> TOKENS =
            Map.of(
                    "providerLabRouting", "PROVIDERLABROUTING",
                    "document_review", "DOCUMENT_REVIEW",
                    "DocumentExtraReviewer", "DOCUMENTEXTRAREVIEWER",
                    "HRMDocument", "HRMDOCUMENT",
                    "HRMDocumentComment", "HRMDOCUMENTCOMMENT",
                    "HRMDocumentSubClass", "HRMDOCUMENTSUBCLASS",
                    "HRMDocumentToProvider", "HRMDOCUMENTTOPROVIDER");

    private static final Pattern CODE =
            Pattern.compile("CDS_EXPORT_UNLINKED_([A-Z_]{1,40})_ROWS_([1-9][0-9]{0,17})");

    public CdsExportUnlinkedSourceDto {
        Objects.requireNonNull(table, "table");
        if (!TOKENS.containsKey(table) || rows < 1)
            throw new IllegalArgumentException("CDS_EXPORT_UNLINKED_SOURCE_INVALID");
    }

    /** Every table whose unlinked rows the export counts. */
    public static java.util.Set<String> tables() {
        return new java.util.TreeSet<>(TOKENS.keySet());
    }

    /** The ledger safe code carrying this count, such as {@code CDS_EXPORT_UNLINKED_HRMDOCUMENT_ROWS_4}. */
    public String eventCode() {
        return "CDS_EXPORT_UNLINKED_" + TOKENS.get(table) + "_ROWS_" + rows;
    }

    /** The count a ledger safe code carries, or empty when it is not one. */
    public static Optional<CdsExportUnlinkedSourceDto> fromEventCode(String code) {
        if (code == null) return Optional.empty();
        var match = CODE.matcher(code);
        if (!match.matches()) return Optional.empty();
        for (var entry : TOKENS.entrySet())
            if (entry.getValue().equals(match.group(1)))
                return Optional.of(
                        new CdsExportUnlinkedSourceDto(
                                entry.getKey(), Long.parseLong(match.group(2))));
        return Optional.empty();
    }
}
