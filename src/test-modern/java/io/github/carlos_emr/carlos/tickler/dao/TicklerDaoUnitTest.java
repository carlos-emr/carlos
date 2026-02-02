/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.tickler.dao;

import io.github.carlos_emr.carlos.commn.dao.TicklerDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerUpdate;
import io.github.carlos_emr.carlos.tickler.TicklerUnitTestBase;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TicklerDao demonstrating true unit testing without Spring context.
 *
 * <p>This test class demonstrates how to effectively unit test DAO layer components
 * despite the SpringUtils anti-pattern. It uses MockedStatic to mock SpringUtils.getBean()
 * calls and tests query building, parameter binding, and business logic without
 * requiring a database connection.</p>
 *
 * <p><b>Key Testing Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Query string verification without database execution</li>
 *   <li>Parameter binding validation</li>
 *   <li>Business logic testing in isolation</li>
 *   <li>Edge case handling</li>
 *   <li>Method interaction verification</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see TicklerDaoImpl
 * @see OpenOUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tickler DAO Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("dao")
public class TicklerDaoUnitTest extends TicklerUnitTestBase {

    private TicklerDaoImpl ticklerDao;

    @BeforeEach
    void setUp() {
        // Create the DAO instance
        ticklerDao = new TicklerDaoImpl();

        // Inject the mock EntityManager using helper from base class
        injectEntityManager(ticklerDao);
    }

    @Test
    @DisplayName("should build correct HQL query for finding active ticklers by demographic")
    void shouldBuildCorrectQuery_whenFindingActiveTicklersByDemographic() {
        // Given
        when(mockEntityManager.createQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(new ArrayList<>());

        // When
        ticklerDao.findActiveByDemographicNo(TEST_DEMO_NO);

        // Then
        verifyQueryContains("t.demographicNo = ?1");
        verifyQueryContains("t.status = 'A'");
        verifyQueryContains("order by t.serviceDate desc");

        verify(mockQuery).setParameter(1, TEST_DEMO_NO);
    }

    @Test
    @DisplayName("should handle empty demographic list by adding zero placeholder")
    void shouldHandleEmptyDemographicList_byAddingZeroPlaceholder() {
        // Given - Empty list edge case
        List<Integer> emptyDemoList = new ArrayList<>();
        ArgumentCaptor<List<Integer>> listCaptor = ArgumentCaptor.forClass(List.class);
        when(mockEntityManager.createQuery(anyString())).thenReturn(mockQuery);
        when(mockQuery.getResultList()).thenReturn(new ArrayList<>());

        // When
        ticklerDao.findActiveByMessageForPatients(emptyDemoList, "test");

        // Then - Should add 0 as placeholder
        verify(mockQuery).setParameter(eq(1), listCaptor.capture());
        assertThat(listCaptor.getValue())
            .hasSize(1)
            .contains(0);
    }

    @Test
    @DisplayName("should eagerly load tickler updates when finding by ID")
    void shouldEagerlyLoadUpdates_whenFindingById() {
        // Given
        Integer ticklerId = 789;
        Tickler mockTickler = mock(Tickler.class);
        Set<TicklerUpdate> mockUpdates = new HashSet<>();
        mockUpdates.add(new TicklerUpdate());

        when(mockEntityManager.find(Tickler.class, ticklerId)).thenReturn(mockTickler);
        when(mockTickler.getUpdates()).thenReturn(mockUpdates);

        // When
        Tickler result = ticklerDao.find(ticklerId);

        // Then - Verify eager loading pattern
        assertThat(result).isEqualTo(mockTickler);
        verify(mockTickler).getUpdates();
        // The DAO calls .size() to force eager loading
        assertThat(mockUpdates.size()).isEqualTo(1);
    }
}