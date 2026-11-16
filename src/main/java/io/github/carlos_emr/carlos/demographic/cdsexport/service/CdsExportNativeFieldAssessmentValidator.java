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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Presence;
import java.io.IOException;
import java.util.*;

/**
 * Independently checks the exact field identities and recorded presence behind a mapping.
 * Equal counts alone cannot prove that a key was not replaced, invented or omitted. This
 * accounts only for supplied authenticated rows; whole-chart inventory, clinical semantics,
 * actual serialized values and final delivery still require their own independent checks.
 */
public final class CdsExportNativeFieldAssessmentValidator {
    public record Result(
            long fields,
            long sqlNulls,
            long empty,
            long values,
            long absent,
            long mapped,
            long operational,
            long blocked) {
        public Result {
            if (fields < 0
                    || sqlNulls < 0
                    || empty < 0
                    || values < 0
                    || absent < 0
                    || mapped < 0
                    || operational < 0
                    || blocked < 0
                    || Math.addExact(Math.addExact(sqlNulls, empty), values) != fields
                    || Math.addExact(
                                    Math.addExact(absent, mapped),
                                    Math.addExact(operational, blocked))
                            != fields)
                throw new IllegalArgumentException("CDS_EXPORT_FIELD_ASSESSMENT_COUNTS_INVALID");
        }

        public boolean representable() {
            return blocked == 0;
        }
    }

    /**
     * Checks every field of the supplied rows against its recorded decision. No count of fields
     * refuses an occurrence: its rows are already held together by the caller.
     */
    public Result validate(
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(rows);
        Objects.requireNonNull(assessments);
        Objects.requireNonNull(checkpoint);
        long expected = 0;
        var rowKeys = new HashSet<String>();
        var presence = new long[Presence.values().length];
        var outcomes = new long[Outcome.values().length];
        for (var row : rows) {
            checkpoint.run();
            if (row == null || !rowKeys.add(row.table() + "/" + row.recordKey())) scope();
            for (var field : row.fields().entrySet()) {
                checkpoint.run();
                expected++;
                var key =
                        new CdsExportNativeFieldKeyDto(
                                row.table(), row.recordKey(), field.getKey());
                var assessment = assessments.get(key);
                var actual =
                        field.getValue() == null
                                ? Presence.SQL_NULL
                                : field.getValue().isEmpty() ? Presence.EMPTY : Presence.VALUE;
                if (assessment == null
                        || assessment.presence() != actual
                        || assessment.rule().isBlank()
                        || assessment.rule().length() > 255
                        || assessment.destination().length() > 4096
                        || assessment.outcome() == Outcome.ABSENT && actual == Presence.VALUE
                        || assessment.outcome() == Outcome.MAPPED
                                && assessment.destination().isBlank()) scope();
                presence[actual.ordinal()]++;
                outcomes[assessment.outcome().ordinal()]++;
            }
        }
        if (expected != assessments.size()) scope();
        checkpoint.run();
        return new Result(
                expected,
                presence[Presence.SQL_NULL.ordinal()],
                presence[Presence.EMPTY.ordinal()],
                presence[Presence.VALUE.ordinal()],
                outcomes[Outcome.ABSENT.ordinal()],
                outcomes[Outcome.MAPPED.ordinal()],
                outcomes[Outcome.OPERATIONAL.ordinal()],
                outcomes[Outcome.REQUIRES_REVIEW.ordinal()]);
    }

    /**
     * The former form, whose field count nothing checks any more.
     *
     * @deprecated {@code ignoredMaximumFields} has no effect. Use {@link #validate(List, Map,
     *     Runnable)}; this remains only until the last demographic callers
     *     ({@code CdsExportDemographicPhonesMapper}, {@code CdsExportDemographicClosureValidator})
     *     move to it.
     */
    @Deprecated(forRemoval = true)
    public Result validate(
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            long ignoredMaximumFields,
            Runnable checkpoint)
            throws IOException {
        return validate(rows, assessments, checkpoint);
    }

    public void requireRepresentable(
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments,
            Runnable checkpoint)
            throws IOException {
        if (!validate(rows, assessments, checkpoint).representable())
            throw new IOException("CDS_EXPORT_FIELD_ASSESSMENT_REQUIRES_REVIEW");
    }

    private static void scope() throws IOException {
        throw new IOException("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
    }
}
