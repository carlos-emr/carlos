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
package io.github.carlos_emr.carlos.tickler.model;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDaoImpl;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.casemgmt.print.OscarChartPrinter;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the Tickler relationship lazy-fetch migration.
 *
 * @since 2026-05-05
 */
@DisplayName("Tickler lazy fetch migration")
@Tag("unit")
@Tag("fast")
class TicklerLazyFetchMigrationUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should use lazy fetch for all Tickler relationships")
    void shouldUseLazyFetch_forAllTicklerRelationships() {
        List<FetchType> relationshipFetchTypes = Stream.of(Tickler.class.getDeclaredFields())
                .map(this::relationshipFetchType)
                .filter(Objects::nonNull)
                .toList();

        assertThat(relationshipFetchTypes).isNotEmpty().allMatch(FetchType.LAZY::equals);
    }

    @Test
    @DisplayName("should declare transactional boundary for relationship mutators")
    void shouldDeclareTransactionalBoundary_forRelationshipMutators() throws NoSuchMethodException {
        Method updateTickler = TicklerManagerImpl.class.getMethod(
                "updateTickler",
                io.github.carlos_emr.carlos.utility.LoggedInInfo.class,
                Tickler.class);
        Method reassign = TicklerManagerImpl.class.getMethod(
                "reassign",
                io.github.carlos_emr.carlos.utility.LoggedInInfo.class,
                Integer.class,
                String.class,
                String.class);

        assertThat(updateTickler.getAnnotation(Transactional.class)).isNotNull();
        assertThat(reassign.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    @DisplayName("should resolve provider names without entity traversal for chart printing")
    void shouldResolveProviderNamesWithoutEntityTraversal_forChartPrinting() throws Exception {
        ProviderDao providerDao = mock(ProviderDao.class);
        TicklerManager ticklerManager = mock(TicklerManager.class);
        registerMock(ProviderDao.class, providerDao);
        registerMock(TicklerManager.class, ticklerManager);
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(OscarAppointmentDao.class, mock(OscarAppointmentDao.class));
        registerMock(PreventionDao.class, mock(PreventionDao.class));
        registerMock(DemographicExtDao.class, mock(DemographicExtDao.class));

        Tickler tickler = new Tickler();
        tickler.setCreator("101");
        tickler.setTaskAssignedTo("202");
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        Provider creator = mock(Provider.class);
        Provider assignee = mock(Provider.class);
        when(creator.getFormattedName()).thenReturn("Creator Provider");
        when(assignee.getFormattedName()).thenReturn("Assignee Provider");
        when(providerDao.getProvider("101")).thenReturn(creator);
        when(providerDao.getProvider("202")).thenReturn(assignee);
        when(ticklerManager.getTicklers(any(), any(CustomFilter.class))).thenReturn(List.of(tickler));

        try (OscarChartPrinter printer = new OscarChartPrinter(mock(jakarta.servlet.http.HttpServletRequest.class),
                new ByteArrayOutputStream())) {
            printer.setDemographic(demographic);
            printer.printTicklers(null);
        }

        verify(providerDao).getProvider("101");
        verify(providerDao).getProvider("202");
    }

    @Test
    @DisplayName("should join fetch detail graph for Tickler find")
    void shouldJoinFetchDetailGraph_forTicklerFind() throws NoSuchFieldException, IllegalAccessException {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        TicklerDaoImpl ticklerDao = new TicklerDaoImpl();
        Field entityManagerField = TicklerDaoImpl.class.getSuperclass().getDeclaredField("entityManager");
        entityManagerField.setAccessible(true);
        entityManagerField.set(ticklerDao, entityManager);
        when(entityManager.createQuery(contains("left join fetch t.comments"))).thenReturn(query);
        when(query.setParameter(eq(1), eq(123))).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Tickler()));

        ticklerDao.find(123);

        verify(entityManager).createQuery(contains("left join fetch t.demographic"));
        verify(entityManager).createQuery(contains("left join fetch t.provider"));
        verify(entityManager).createQuery(contains("left join fetch t.assignee"));
        verify(entityManager).createQuery(contains("left join fetch t.ticklerCategory"));
        verify(entityManager).createQuery(contains("left join fetch t.program"));
    }

    private FetchType relationshipFetchType(Field field) {
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        if (manyToOne != null) {
            return manyToOne.fetch();
        }
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany != null) {
            return oneToMany.fetch();
        }
        return null;
    }
}
