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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link WaitingListAvailabilityService} against the real
 * {@link WaitingListNameDao} queries on H2, so the "current list of the provider's
 * group" rule is proven on persisted {@code waitingListName} rows (group and
 * {@code is_history}) rather than on mocks.
 *
 * <p>Regression coverage for issue #4229.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("WaitingListAvailabilityService integration")
@Tag("integration")
@Tag("waitinglist")
@Tag("query")
@Transactional
class WaitingListAvailabilityServiceIntegrationTest extends CarlosTestBase {

    private static final String GROUP = "wlGroupA";
    private static final String OTHER_GROUP = "wlGroupB";

    @Autowired
    private WaitingListNameDao waitingListNameDao;

    private WaitingListAvailabilityService service;
    private String previousFeatureValue;

    @BeforeEach
    void setUp() {
        previousFeatureValue = CarlosProperties.getInstance()
                .getProperty(WaitingListAvailabilityService.FEATURE_PROPERTY);
        CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "true");
        // test-context-full.xml scans @Repository only, so the service is wired by hand
        // onto the real DAO bean, exactly as Spring would.
        service = new WaitingListAvailabilityService(waitingListNameDao);
    }

    @AfterEach
    void tearDown() {
        if (previousFeatureValue == null) {
            CarlosProperties.getInstance().remove(WaitingListAvailabilityService.FEATURE_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, previousFeatureValue);
        }
    }

    private WaitingListName persistList(String name, String group, String isHistory) {
        WaitingListName list = new WaitingListName();
        list.setName(name);
        list.setGroupNo(group);
        list.setProviderNo("999998");
        list.setCreateDate(new Date());
        list.setIsHistory(isHistory);
        waitingListNameDao.persist(list);
        return list;
    }

    private static ProviderPreference preferenceFor(String group) {
        ProviderPreference preference = new ProviderPreference();
        preference.setMyGroupNo(group);
        return preference;
    }

    @Test
    @DisplayName("should offer only current lists of the provider's group, ordered by name")
    void shouldOfferCurrentGroupLists_withPersistedRows() {
        WaitingListName surgery = persistList("Surgery", GROUP, "N");
        WaitingListName physio = persistList("Physio", GROUP, "N");
        persistList("Archived", GROUP, "Y");
        persistList("Other clinic", OTHER_GROUP, "N");

        assertThat(service.findSelectableLists(preferenceFor(GROUP)))
                .extracting(WaitingListName::getId)
                .containsExactly(physio.getId(), surgery.getId());
    }

    @Test
    @DisplayName("should accept a current group list and refuse archived or other-group lists")
    void shouldDecideSelectability_fromPersistedRows() {
        WaitingListName current = persistList("Current", GROUP, "N");
        WaitingListName archived = persistList("Archived", GROUP, "Y");
        WaitingListName otherGroup = persistList("Other clinic", OTHER_GROUP, "N");
        ProviderPreference preference = preferenceFor(GROUP);

        assertThat(service.isSelectable(preference, current.getId())).isTrue();
        assertThat(service.isSelectable(preference, archived.getId())).isFalse();
        assertThat(service.isSelectable(preference, otherGroup.getId())).isFalse();
    }

    @Test
    @DisplayName("should stop offering a list once it is archived")
    void shouldStopOffering_whenListIsArchivedAfterRender() {
        WaitingListName list = persistList("Soon archived", GROUP, "N");
        ProviderPreference preference = preferenceFor(GROUP);
        assertThat(service.isSelectable(preference, list.getId())).isTrue();

        list.setIsHistory("Y");
        waitingListNameDao.merge(list);

        assertThat(service.isSelectable(preference, list.getId())).isFalse();
    }

    @Test
    @DisplayName("should show the Master Record link only while a current list exists and the feature is on")
    void shouldReportCurrentLists_fromPersistedRows() {
        persistList("Only archived", GROUP, "Y");
        boolean beforeAnyCurrentList = waitingListNameDao.countActiveWatingListNames() > 0;
        assertThat(service.hasCurrentLists()).isEqualTo(beforeAnyCurrentList);

        persistList("Current", OTHER_GROUP, "N");
        assertThat(service.hasCurrentLists()).isTrue();

        CarlosProperties.getInstance().setProperty(WaitingListAvailabilityService.FEATURE_PROPERTY, "false");
        assertThat(service.hasCurrentLists()).isFalse();
        assertThat(service.findSelectableLists(preferenceFor(OTHER_GROUP))).isEmpty();
    }
}
