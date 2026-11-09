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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.util.Map;

/** Every captured field starts unresolved; a reviewed rule must explicitly account for each populated value. */
final class CdsExportNativeFieldAssessments {
    private final CdsExportCapturedTextRowDto row;
    private final Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions;

    CdsExportNativeFieldAssessments(
            CdsExportCapturedTextRowDto row,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> decisions) {
        this.row = row;
        this.decisions = decisions;
        row.fields()
                .forEach(
                        (column, value) ->
                                set(
                                        column,
                                        value == null || value.isEmpty()
                                                ? Outcome.ABSENT
                                                : Outcome.REQUIRES_REVIEW,
                                        "",
                                        "UNMAPPED_NATIVE_VALUE"));
    }

    String raw(String column) {
        if (!row.fields().containsKey(column))
            throw new IllegalArgumentException("CDS_EXPORT_NATIVE_COLUMN_UNKNOWN");
        return row.fields().get(column);
    }

    void set(String column, Outcome outcome, String destination, String rule) {
        String value = raw(column);
        var presence =
                value == null
                        ? Presence.SQL_NULL
                        : value.isEmpty() ? Presence.EMPTY : Presence.VALUE;
        decisions.put(
                new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column),
                new CdsExportFieldAssessmentDto(presence, outcome, destination, rule));
    }

    void operational(String column, String rule) {
        set(column, Outcome.OPERATIONAL, "", rule);
    }

    void review(String column, String destination, String rule) {
        set(column, Outcome.REQUIRES_REVIEW, destination, rule);
    }

    String text(String column, String destination, int maximum) {
        String value = raw(column);
        if (value == null || value.isEmpty()) return null;
        if (!legal(value, maximum)) {
            review(column, destination, "WIRE_TEXT_REQUIRES_REVIEW");
            return null;
        }
        set(column, Outcome.MAPPED, destination, "EXACT_NATIVE_TEXT");
        return value;
    }

    static boolean legal(String value, int maximum) {
        return value != null
                && value.codePointCount(0, value.length()) <= maximum
                && value.codePoints()
                        .noneMatch(
                                code ->
                                        code < 0x20 && code != '\t' && code != '\r' && code != '\n'
                                                || code >= 0xd800 && code <= 0xdfff
                                                || code == 0xfffe
                                                || code == 0xffff);
    }
}
