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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroup;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MeasurementGroupCreationService}: a group is written together with its
 * first types or not at all (issue #3335).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("measurement")
@DisplayName("MeasurementGroupCreationService")
class MeasurementGroupCreationServiceUnitTest {

    private static final String GROUP = "FAKE Synthetic vitals";

    private MeasurementGroupStyleDao styleDao;
    private MeasurementGroupDao groupDao;
    private MeasurementGroupCreationService service;

    @BeforeEach
    void setUp() {
        styleDao = mock(MeasurementGroupStyleDao.class);
        groupDao = mock(MeasurementGroupDao.class);
        service = new MeasurementGroupCreationService(styleDao, groupDao);
        when(styleDao.findByGroupName(any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("group name rule")
    class GroupNameRule {

        @ParameterizedTest
        @ValueSource(strings = {"Vitals", "FAKEPW 0A1B new group", "Diabetes, type 2.", "Follow up?", "a_b"})
        void shouldAcceptTheName_whenItUsesTheDefineFormCharacters(String name) {
            assertThat(MeasurementGroupCreationService.isValidGroupName(name)).isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "Tab\tname", "Line\nbreak", "Vitals-BP", "<b>x</b>", "a\"b", "Café"})
        void shouldRefuseTheName_whenBlankOrOutsideTheAllowedCharacters(String name) {
            assertThat(MeasurementGroupCreationService.isValidGroupName(name)).isFalse();
        }

        @Test
        void shouldRefuseTheName_whenLongerThanTheColumn() {
            assertThat(MeasurementGroupCreationService.isValidGroupName("a".repeat(100))).isTrue();
            assertThat(MeasurementGroupCreationService.isValidGroupName("a".repeat(101))).isFalse();
        }
    }

    @Nested
    @DisplayName("style sheet choice")
    class StyleSheetChoice {

        @ParameterizedTest
        @CsvSource({"7,7", "' 12 ',12", "0,0", "-3,0", "abc,0", "2147483648,0"})
        void shouldParseTheCssId_orFallBackToNone(String submitted, int expected) {
            assertThat(MeasurementGroupCreationService.parseCssId(submitted)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldMeanNoStyleSheet_forTheEmptyOption(String submitted) {
            assertThat(MeasurementGroupCreationService.parseCssId(submitted)).isZero();
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void shouldWriteTheStyleRowThenEachMember_whenTypesAreChosen() {
            Outcome outcome = service.create(GROUP, 4, List.of("FAKE Blood Pressure", "FAKE Weight"));

            assertThat(outcome).isEqualTo(Outcome.CREATED);
            ArgumentCaptor<MeasurementGroupStyle> style = ArgumentCaptor.forClass(MeasurementGroupStyle.class);
            ArgumentCaptor<MeasurementGroup> members = ArgumentCaptor.forClass(MeasurementGroup.class);
            InOrder order = inOrder(styleDao, groupDao);
            order.verify(styleDao).findByGroupName(GROUP);
            order.verify(styleDao).persist(style.capture());
            order.verify(groupDao, times(2)).persist(members.capture());
            assertThat(style.getValue().getGroupName()).isEqualTo(GROUP);
            assertThat(style.getValue().getCssId()).isEqualTo(4);
            assertThat(members.getAllValues()).extracting(MeasurementGroup::getName).containsOnly(GROUP);
            assertThat(members.getAllValues()).extracting(MeasurementGroup::getTypeDisplayName)
                    .containsExactly("FAKE Blood Pressure", "FAKE Weight");
        }

        @Test
        void shouldStoreEachTypeOnce_andSkipBlankEntries() {
            service.create(GROUP, 0, Arrays.asList("FAKE Weight", "", null, "  ", "FAKE Weight", "FAKE Height"));

            ArgumentCaptor<MeasurementGroup> members = ArgumentCaptor.forClass(MeasurementGroup.class);
            verify(groupDao, times(2)).persist(members.capture());
            assertThat(members.getAllValues()).extracting(MeasurementGroup::getTypeDisplayName)
                    .containsExactly("FAKE Weight", "FAKE Height");
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldWriteNothing_whenNoTypeIsChosen(List<String> types) {
            assertThat(service.create(GROUP, 4, types)).isEqualTo(Outcome.NO_TYPES);
            verify(styleDao, never()).persist(any());
            verifyNoInteractions(groupDao);
        }

        @Test
        void shouldWriteNothing_whenOnlyBlankTypesAreChosen() {
            assertThat(service.create(GROUP, 4, Arrays.asList("", " ", null))).isEqualTo(Outcome.NO_TYPES);
            verify(styleDao, never()).persist(any());
            verifyNoInteractions(groupDao);
        }

        @Test
        void shouldWriteNothing_whenTheNameIsAlreadyDefined() {
            MeasurementGroupStyle existing = new MeasurementGroupStyle();
            existing.setGroupName(GROUP);
            when(styleDao.findByGroupName(GROUP)).thenReturn(List.of(existing));

            assertThat(service.create(GROUP, 4, List.of("FAKE Weight"))).isEqualTo(Outcome.DUPLICATE_NAME);
            verify(styleDao, never()).persist(any());
            verifyNoInteractions(groupDao);
        }

        @Test
        void shouldWriteNothing_whenTheNameIsInvalid() {
            assertThat(service.create("Tab\tname", 4, List.of("FAKE Weight"))).isEqualTo(Outcome.INVALID_NAME);
            verifyNoInteractions(styleDao, groupDao);
        }

        @Test
        void shouldRunInOneTransaction_forTheTypeContract() throws Exception {
            Method create = MeasurementGroupCreationService.class.getMethod("create", String.class, int.class, Collection.class);
            Transactional transactional = create.getAnnotation(Transactional.class);
            assertThat(transactional).as("create must be one transaction so a failed member insert rolls back the style row")
                    .isNotNull();
            assertThat(transactional.readOnly()).isFalse();
        }
    }

    @Nested
    @DisplayName("groupExists")
    class GroupExists {

        @Test
        void shouldReportAnExistingGroup_byItsStyleRow() {
            when(styleDao.findByGroupName(GROUP)).thenReturn(List.of(new MeasurementGroupStyle()));
            assertThat(service.groupExists(GROUP)).isTrue();
            assertThat(service.groupExists("FAKE Other")).isFalse();
            verifyNoInteractions(groupDao);
        }
    }
}
