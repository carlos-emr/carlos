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
package io.github.carlos_emr.carlos.waitinglist;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WaitingListAvailabilityService}, the single rule the Master
 * Record and its save path share for "which waiting lists may this provider use".
 *
 * <p>Regression coverage for issue #4229, where turning the feature ON made the
 * Master Record's waiting-list controls read-only.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("WaitingListAvailabilityService")
@Tag("unit")
@Tag("waitinglist")
class WaitingListAvailabilityServiceUnitTest {

    private static final String GROUP = "clinicA";

    private String previousFeatureValue;
    private WaitingListNameDao waitingListNameDao;
    private WaitingListAvailabilityService service;
    private ProviderPreference preference;

    @BeforeEach
    void setUp() {
        previousFeatureValue = CarlosProperties.getInstance()
                .getProperty(WaitingListAvailabilityService.FEATURE_PROPERTY);
        CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "true");

        waitingListNameDao = mock(WaitingListNameDao.class);
        service = new WaitingListAvailabilityService(waitingListNameDao);

        preference = new ProviderPreference();
        preference.setMyGroupNo(GROUP);
    }

    @AfterEach
    void tearDown() {
        if (previousFeatureValue == null) {
            CarlosProperties.getInstance().remove(WaitingListAvailabilityService.FEATURE_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, previousFeatureValue);
        }
    }

    private static WaitingListName list(int id, String name) {
        WaitingListName list = new WaitingListName();
        list.setId(id);
        list.setName(name);
        list.setGroupNo(GROUP);
        list.setIsHistory("N");
        return list;
    }

    @Nested
    @DisplayName("isFeatureEnabled")
    class FeatureSwitch {

        @Test
        @DisplayName("should report the feature enabled when the packaged value true is set")
        void shouldReportEnabled_whenPropertyIsTrue() {
            assertThat(service.isFeatureEnabled()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "", "yes", "TRUE", "on"})
        @DisplayName("should match the JSP oscarPropertiesCheck tag, which only renders the section for exactly true")
        void shouldReportDisabled_forValuesTheJspTagDoesNotRender(String value) {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, value);

            assertThat(service.isFeatureEnabled()).isFalse();
        }

        @Test
        @DisplayName("should report the feature disabled when the property is absent")
        void shouldReportDisabled_whenPropertyIsAbsent() {
            CarlosProperties.getInstance().remove(WaitingListAvailabilityService.FEATURE_PROPERTY);

            assertThat(service.isFeatureEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("findSelectableLists")
    class FindSelectableLists {

        @Test
        @DisplayName("should offer the current lists of the provider's group when the feature is on (issue #4229)")
        void shouldOfferGroupLists_whenFeatureIsEnabled() {
            List<WaitingListName> lists = List.of(list(7, "Physio"), list(9, "Surgery"));
            when(waitingListNameDao.findCurrentByGroup(GROUP)).thenReturn(lists);

            assertThat(service.findSelectableLists(preference)).containsExactlyElementsOf(lists);
        }

        @Test
        @DisplayName("should offer nothing and not query when the feature is off")
        void shouldOfferNothing_whenFeatureIsDisabled() {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "false");

            assertThat(service.findSelectableLists(preference)).isEmpty();
            verifyNoInteractions(waitingListNameDao);
        }

        @Test
        @DisplayName("should offer nothing for a session without a provider preference instead of failing")
        void shouldOfferNothing_whenPreferenceIsMissing() {
            assertThat(service.findSelectableLists(null)).isEmpty();
            verifyNoInteractions(waitingListNameDao);
        }

        @Test
        @DisplayName("should offer nothing when the provider has no schedule group")
        void shouldOfferNothing_whenProviderHasNoGroup() {
            preference.setMyGroupNo(null);

            assertThat(service.findSelectableLists(preference)).isEmpty();
            verifyNoInteractions(waitingListNameDao);
        }
    }

    @Nested
    @DisplayName("isSelectable")
    class IsSelectable {

        @Test
        @DisplayName("should accept a list the provider's group currently offers")
        void shouldAccept_whenListIsOffered() {
            when(waitingListNameDao.findCurrentByGroup(GROUP)).thenReturn(List.of(list(7, "Physio")));

            assertThat(service.isSelectable(preference, 7)).isTrue();
        }

        @Test
        @DisplayName("should refuse a list that is archived, moved or from another group")
        void shouldRefuse_whenListIsNotOffered() {
            when(waitingListNameDao.findCurrentByGroup(GROUP)).thenReturn(List.of(list(7, "Physio")));

            assertThat(service.isSelectable(preference, 8)).isFalse();
        }

        @Test
        @DisplayName("should re-read the provider's current group instead of an earlier answer")
        void shouldFollowTheCurrentGroup_whenGroupChangesBetweenChecks() {
            when(waitingListNameDao.findCurrentByGroup(GROUP)).thenReturn(List.of(list(7, "Physio")));
            when(waitingListNameDao.findCurrentByGroup("clinicB")).thenReturn(List.of());
            assertThat(service.isSelectable(preference, 7)).isTrue();

            preference.setMyGroupNo("clinicB");

            assertThat(service.isSelectable(preference, 7)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("should refuse the form's no-list value and invalid ids without querying")
        void shouldRefuse_forNonPositiveIds(int listId) {
            assertThat(service.isSelectable(preference, listId)).isFalse();
            verifyNoInteractions(waitingListNameDao);
        }

        @Test
        @DisplayName("should refuse every list when the feature is off")
        void shouldRefuse_whenFeatureIsDisabled() {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "false");

            assertThat(service.isSelectable(preference, 7)).isFalse();
            verifyNoInteractions(waitingListNameDao);
        }
    }

    @Nested
    @DisplayName("hasCurrentLists")
    class HasCurrentLists {

        @Test
        @DisplayName("should show the Master Record link when the feature is on and a current list exists")
        void shouldShowLink_whenFeatureOnAndListExists() {
            when(waitingListNameDao.countActiveWatingListNames()).thenReturn(1L);

            assertThat(service.hasCurrentLists()).isTrue();
        }

        @Test
        @DisplayName("should hide the Master Record link when no current list exists")
        void shouldHideLink_whenNoListExists() {
            when(waitingListNameDao.countActiveWatingListNames()).thenReturn(0L);

            assertThat(service.hasCurrentLists()).isFalse();
        }

        @Test
        @DisplayName("should hide the Master Record link when the feature is off")
        void shouldHideLink_whenFeatureIsDisabled() {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "false");

            assertThat(service.hasCurrentLists()).isFalse();
            verifyNoInteractions(waitingListNameDao);
        }
    }
}
