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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renaming a satellite site: {@link SiteDaoImpl#save(Site)} renames the references to the site's old name.
 * {@code billing_on_cheader1} is an Ontario billing table that a BC install's schema does not have, so it is
 * renamed only on Ontario installs; the schedule and appointment tables are renamed everywhere.
 *
 * <p>Isolated because each test sets {@code billregion} on the shared {@link CarlosProperties} singleton.</p>
 */
@Isolated
@Tag("unit")
@Tag("dao")
@DisplayName("SiteDaoImpl site rename")
class SiteDaoImplRenameUnitTest extends CarlosUnitTestBase {

    private static final String BILL_REGION = "billregion";

    private EntityManager entityManager;
    private SiteDaoImpl dao;
    private Object billRegionBefore;

    @BeforeEach
    void prepare() {
        // Hashtable read: getProperty(String) would log a missing-key warning.
        billRegionBefore = CarlosProperties.getInstance().get(BILL_REGION);
        entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery(anyString())).thenAnswer(invocation -> mock(Query.class, RETURNS_SELF));
        Site stored = new Site();
        stored.setSiteId(7);
        stored.setName("FAKE North");
        when(entityManager.find(Site.class, 7)).thenReturn(stored);
        ProviderSiteDao providerSites = createAndRegisterMock(ProviderSiteDao.class);
        when(providerSites.findBySiteId(7)).thenReturn(List.of());
        createAndRegisterMock(ProviderDao.class);
        dao = new SiteDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
    }

    @AfterEach
    void restore() {
        if (billRegionBefore == null) {
            CarlosProperties.getInstance().remove(BILL_REGION);
        } else {
            CarlosProperties.getInstance().put(BILL_REGION, billRegionBefore);
        }
    }

    private static Site renamed() {
        Site site = new Site();
        site.setSiteId(7);
        site.setName("FAKE North renamed");
        return site;
    }

    @Test
    @DisplayName("should rename the schedules, appointments and Ontario claims on an Ontario install")
    void shouldRenameAllFourTables_forOntarioInstall() {
        CarlosProperties.getInstance().setProperty(BILL_REGION, "ON");
        Site site = renamed();

        dao.save(site);

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(4)).createNativeQuery(statements.capture());
        assertThat(statements.getAllValues()).containsExactly(
                "update rschedule set avail_hour = replace(avail_hour, ?1, ?2) ",
                "update scheduledate set reason = ?1 where reason = ?2",
                "update appointment set location = ?1 where location = ?2",
                "update billing_on_cheader1 set clinic = ?1 where clinic = ?2");
        verify(entityManager).merge(site);
    }

    @Test
    @DisplayName("should rename the schedules and appointments but not the Ontario claims table on a BC install")
    void shouldSkipOntarioClaims_forBritishColumbiaInstall() {
        CarlosProperties.getInstance().setProperty(BILL_REGION, "BC");
        Site site = renamed();

        dao.save(site);

        ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(3)).createNativeQuery(statements.capture());
        assertThat(statements.getAllValues()).containsExactly(
                "update rschedule set avail_hour = replace(avail_hour, ?1, ?2) ",
                "update scheduledate set reason = ?1 where reason = ?2",
                "update appointment set location = ?1 where location = ?2");
        verify(entityManager).merge(site);
    }

    @Test
    @DisplayName("should rename nothing when the site keeps its name")
    void shouldRenameNothing_whenNameUnchanged() {
        CarlosProperties.getInstance().setProperty(BILL_REGION, "BC");
        Site site = renamed();
        site.setName("FAKE North");

        dao.save(site);

        verify(entityManager, never()).createNativeQuery(anyString());
        verify(entityManager).merge(site);
    }
}
