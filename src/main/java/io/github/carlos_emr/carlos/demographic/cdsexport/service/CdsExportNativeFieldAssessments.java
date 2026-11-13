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
    /** The rule every populated column starts with until a reviewed rule decides it. */
    private static final String UNDECIDED = "UNMAPPED_NATIVE_VALUE";

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
                                        UNDECIDED));
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

    /**
     * The populated value has no element that can hold it: it travels exactly in the native
     * demographic NewCategory record ({@link CdsExportDemographicSupportingMapper}).
     */
    void carry(String column) {
        var decision = CdsExportDemographicSupportingMapper.carried(raw(column));
        set(column, decision.outcome(), decision.destination(), decision.rule());
    }

    /**
     * Carries every populated column of the row that no rule has decided yet. A value an explicit
     * rule sent to review stays a review.
     */
    void carryRemaining(java.util.Set<String> exclude) {
        for (var entry : row.fields().entrySet()) {
            if (exclude.contains(entry.getKey())) continue;
            var decision =
                    decisions.get(
                            new CdsExportNativeFieldKeyDto(
                                    row.table(), row.recordKey(), entry.getKey()));
            if (decision != null
                    && decision.outcome() == Outcome.REQUIRES_REVIEW
                    && UNDECIDED.equals(decision.rule())) carry(entry.getKey());
        }
    }

    /**
     * An optional xs:token element: untrimmed or doubled whitespace is written collapsed, as every
     * schema reader sees it, and the exact value is carried; a value longer than the element is
     * carried instead. Only text no XML document can hold requires review.
     */
    String token(String column, String destination, int maximum) {
        String value = raw(column);
        if (value == null || value.isEmpty()) return null;
        if (!legal(value, Integer.MAX_VALUE)) {
            review(column, destination, "WIRE_TEXT_REQUIRES_REVIEW");
            return null;
        }
        String collapsed = value.strip().replaceAll("[ \t\r\n]+", " ");
        if (collapsed.isEmpty() || collapsed.codePointCount(0, collapsed.length()) > maximum) {
            carry(column);
            return null;
        }
        if (collapsed.equals(value)) set(column, Outcome.MAPPED, destination, "EXACT_NATIVE_TEXT");
        else
            set(
                    column,
                    Outcome.MAPPED,
                    destination + "," + CdsExportDemographicSupportingMapper.DESTINATION,
                    "XS_TOKEN_WHITESPACE_COLLAPSED_EXACT_VALUE_CARRIED");
        return collapsed;
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
