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
package io.github.carlos_emr.carlos.encounter.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EctFormData.PatientForm#CREATED_DATE_COMPARATOR}, the order of the chart Forms
 * list within each form type: newest created date first, a row without a created date placed by
 * its edited date, and a row with neither placed last instead of failing the sort (issue #5684).
 * Dates are the JDBC types the form loader reads: {@link Date} for {@code formCreated} and
 * {@link Timestamp} for {@code formEdited}.
 */
@DisplayName("EctFormData.PatientForm.CREATED_DATE_COMPARATOR")
@Tag("unit")
@Tag("encounter")
class EctFormDataCreatedDateComparatorUnitTest {

    private static EctFormData.PatientForm form(int id, String created, String edited) {
        return new EctFormData.PatientForm("Annual", id, 1,
                created == null ? null : Date.valueOf(created),
                edited == null ? null : Timestamp.valueOf(edited));
    }

    private static List<Integer> sortedIds(EctFormData.PatientForm... forms) {
        List<EctFormData.PatientForm> list = new ArrayList<>(List.of(forms));
        list.sort(EctFormData.PatientForm.CREATED_DATE_COMPARATOR);
        return list.stream().map(f -> f.formId).toList();
    }

    @Test
    void shouldSortNewestCreatedFirst_whenEveryRowHasACreatedDate() {
        assertThat(sortedIds(
                form(1, "2026-01-02", "2026-03-01 00:00:00"),
                form(2, "2026-01-05", "2026-01-05 00:00:00"),
                form(3, "2026-01-03", "2026-02-01 00:00:00")))
                .containsExactly(2, 3, 1);
    }

    @Test
    void shouldPlaceRowByEditedDate_whenItHasNoCreatedDate() {
        assertThat(sortedIds(
                form(1, "2026-01-05", "2026-01-05 00:00:00"),
                form(2, null, "2026-01-10 12:00:00"),
                form(3, "2026-01-20", "2026-01-20 00:00:00")))
                .containsExactly(3, 2, 1);
    }

    @Test
    void shouldSortByEditedDate_whenNoRowHasACreatedDate() {
        assertThat(sortedIds(
                form(1, null, "2026-01-01 08:00:00"),
                form(2, null, "2026-01-01 09:00:00")))
                .containsExactly(2, 1);
    }

    @Test
    void shouldPlaceRowLast_whenItHasNeitherDate() {
        assertThat(sortedIds(
                form(1, null, null),
                form(2, "2026-01-05", null),
                form(3, null, "2026-01-10 00:00:00")))
                .containsExactly(3, 2, 1);
    }

    @Test
    void shouldKeepLoadOrder_whenCreatedDatesTie() {
        assertThat(sortedIds(
                form(5, "2026-01-05", "2026-01-05 10:00:00"),
                form(4, "2026-01-05", "2026-01-05 09:00:00")))
                .containsExactly(5, 4);
    }
}
