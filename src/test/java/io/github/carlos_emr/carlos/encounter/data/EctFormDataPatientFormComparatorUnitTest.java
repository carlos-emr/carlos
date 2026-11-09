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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.encounter.data.EctFormData.PatientForm;

/**
 * Pins the ordering contract of {@link PatientForm#CREATED_DATE_COMPARATOR} and
 * {@link PatientForm#EDITED_DATE_COMPARATOR} (issue #5994).
 *
 * <p>{@code formCreated} is nullable in every encounter form table and {@code formEdited} is nullable
 * in some, so a row can have neither date. Both comparators must order such rows (last) instead of
 * throwing a {@link NullPointerException}, and must be a consistent total order: the previous
 * versions compared one row's {@code created} with the other's {@code edited}, which is not
 * transitive and can make {@code List.sort} fail with "Comparison method violates its general
 * contract".</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("form")
@Tag("encounter")
@DisplayName("PatientForm date comparators")
class EctFormDataPatientFormComparatorUnitTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final Date D1 = new Date(1_700_000_000_000L);
    private static final Date D2 = new Date(D1.getTime() + DAY);
    private static final Date D3 = new Date(D2.getTime() + DAY);
    private static final Date D4 = new Date(D3.getTime() + DAY);

    private static PatientForm form(int id, Date created, Date edited) {
        return new PatientForm("Form " + id, id, 1, created, edited);
    }

    private static List<Integer> idsAfterSort(Comparator<PatientForm> comparator, PatientForm... forms) {
        List<PatientForm> list = new ArrayList<>(List.of(forms));
        Collections.sort(list, comparator);
        return list.stream().map(f -> f.formId).collect(Collectors.toList());
    }

    @Nested
    @DisplayName("EDITED_DATE_COMPARATOR")
    class EditedDate {

        @Test
        @DisplayName("should sort newest edit first when every form has both dates")
        void shouldSortNewestEditFirst_whenAllDatesPresent() {
            assertThat(idsAfterSort(PatientForm.EDITED_DATE_COMPARATOR,
                    form(1, D1, D2), form(2, D1, D4), form(3, D1, D3)))
                    .containsExactly(2, 3, 1);
        }

        @Test
        @DisplayName("should fall back to the created date when a form was never edited")
        void shouldFallBackToCreated_whenEditedIsNull() {
            // effective dates: 1 -> D3 (created), 2 -> D4 (edited), 3 -> D2 (created)
            assertThat(idsAfterSort(PatientForm.EDITED_DATE_COMPARATOR,
                    form(1, D3, null), form(2, D1, D4), form(3, D2, null)))
                    .containsExactly(2, 1, 3);
        }

        @Test
        @DisplayName("should not throw and sort the form last when it has neither date")
        void shouldSortUndatedFormLast_whenBothDatesNull() {
            List<PatientForm> forms = new ArrayList<>(List.of(
                    form(1, null, null), form(2, D1, D2), form(3, D3, null)));

            assertThatCode(() -> Collections.sort(forms, PatientForm.EDITED_DATE_COMPARATOR))
                    .doesNotThrowAnyException();
            assertThat(forms).extracting(f -> f.formId).containsExactly(3, 2, 1);
        }

        @Test
        @DisplayName("should not throw for every pairing of present and missing dates")
        void shouldNotThrow_forEveryNullCombination() {
            Date[] values = {null, D1, D2};
            for (Date c1 : values) {
                for (Date e1 : values) {
                    for (Date c2 : values) {
                        for (Date e2 : values) {
                            PatientForm a = form(1, c1, e1);
                            PatientForm b = form(2, c2, e2);
                            assertThatCode(() -> PatientForm.EDITED_DATE_COMPARATOR.compare(a, b))
                                    .doesNotThrowAnyException();
                            assertThat(Integer.signum(PatientForm.EDITED_DATE_COMPARATOR.compare(a, b)))
                                    .isEqualTo(-Integer.signum(PatientForm.EDITED_DATE_COMPARATOR.compare(b, a)));
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("should break ties by highest form ID first so the order is deterministic")
        void shouldBreakTiesByHighestId_whenDatesEqual() {
            assertThat(idsAfterSort(PatientForm.EDITED_DATE_COMPARATOR,
                    form(5, D1, D2), form(9, D1, D2), form(7, null, null), form(8, null, null)))
                    .containsExactly(9, 5, 8, 7);
        }

        @Test
        @DisplayName("should report equal for two forms that are the same row")
        void shouldReturnZero_forSameRow() {
            PatientForm row = form(4, D1, D2);
            assertThat(PatientForm.EDITED_DATE_COMPARATOR.compare(row, form(4, D1, D2))).isZero();
        }

        @Test
        @DisplayName("should stay a consistent total order across randomly dated forms")
        void shouldNotViolateComparatorContract_forRandomMixedDates() {
            Random random = new Random(5994L);
            Date[] pool = {null, D1, D2, D3, D4};
            List<PatientForm> forms = new ArrayList<>();
            for (int i = 1; i <= 400; i++) {
                forms.add(form(i, pool[random.nextInt(pool.length)], pool[random.nextInt(pool.length)]));
            }

            // TimSort throws IllegalArgumentException on an inconsistent comparator; 400 mixed rows
            // are well past its merge threshold, so this also exercises the merge path.
            assertThatCode(() -> Collections.sort(forms, PatientForm.EDITED_DATE_COMPARATOR))
                    .doesNotThrowAnyException();
            for (int i = 1; i < forms.size(); i++) {
                assertThat(PatientForm.EDITED_DATE_COMPARATOR.compare(forms.get(i - 1), forms.get(i)))
                        .isLessThanOrEqualTo(0);
            }
        }
    }

    @Nested
    @DisplayName("CREATED_DATE_COMPARATOR")
    class CreatedDate {

        @Test
        @DisplayName("should sort newest creation first when every form has both dates")
        void shouldSortNewestCreatedFirst_whenAllDatesPresent() {
            assertThat(idsAfterSort(PatientForm.CREATED_DATE_COMPARATOR,
                    form(1, D2, D4), form(2, D4, D4), form(3, D3, D4)))
                    .containsExactly(2, 3, 1);
        }

        @Test
        @DisplayName("should fall back to the edited date when a form has no created date")
        void shouldFallBackToEdited_whenCreatedIsNull() {
            // effective dates: 1 -> D3 (edited), 2 -> D4 (created), 3 -> D2 (edited)
            assertThat(idsAfterSort(PatientForm.CREATED_DATE_COMPARATOR,
                    form(1, null, D3), form(2, D4, D4), form(3, null, D2)))
                    .containsExactly(2, 1, 3);
        }

        @Test
        @DisplayName("should not throw and sort the form last when it has neither date")
        void shouldSortUndatedFormLast_whenBothDatesNull() {
            List<PatientForm> forms = new ArrayList<>(List.of(
                    form(1, null, null), form(2, D1, null), form(3, null, D3)));

            assertThatCode(() -> Collections.sort(forms, PatientForm.CREATED_DATE_COMPARATOR))
                    .doesNotThrowAnyException();
            assertThat(forms).extracting(f -> f.formId).containsExactly(3, 2, 1);
        }

        @Test
        @DisplayName("should stay a consistent total order across randomly dated forms")
        void shouldNotViolateComparatorContract_forRandomMixedDates() {
            Random random = new Random(5994L);
            Date[] pool = {null, D1, D2, D3, D4};
            List<PatientForm> forms = new ArrayList<>();
            for (int i = 1; i <= 400; i++) {
                forms.add(form(i, pool[random.nextInt(pool.length)], pool[random.nextInt(pool.length)]));
            }

            assertThatCode(() -> Collections.sort(forms, PatientForm.CREATED_DATE_COMPARATOR))
                    .doesNotThrowAnyException();
            for (int i = 1; i < forms.size(); i++) {
                assertThat(PatientForm.CREATED_DATE_COMPARATOR.compare(forms.get(i - 1), forms.get(i)))
                        .isLessThanOrEqualTo(0);
            }
        }
    }
}
