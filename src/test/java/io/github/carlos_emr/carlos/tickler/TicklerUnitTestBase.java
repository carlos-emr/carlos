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
package io.github.carlos_emr.carlos.tickler;

import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.Mockito;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.Date;

/**
 * Base class for Tickler-related unit tests providing common mocks and utilities.
 *
 * <p>This class extends CarlosUnitTestBase and adds Tickler-specific test infrastructure
 * including commonly used mocks, helper methods, and test data builders. It serves as
 * the foundation for all Tickler unit tests, enabling consistent test patterns across
 * DAO, Manager, and validation layers.</p>
 *
 * <p><b>Scalability Strategy:</b></p>
 * <ul>
 *   <li>All Tickler unit tests extend this base class</li>
 *   <li>Tests are organized by component and concern (Query, Logic, Validation, etc.)</li>
 *   <li>Common mocks and helpers are centralized here</li>
 *   <li>Test data builders provide consistent test objects</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see CarlosUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("tickler")
public abstract class TicklerUnitTestBase extends CarlosUnitTestBase {

    // Common mocks that most Tickler tests will need
    @Mock
    protected EntityManager mockEntityManager;

    @Mock
    protected Query mockQuery;

    // Create these as concrete mocks instead of using @Mock annotation
    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;

    // Test data constants
    protected static final Integer TEST_DEMO_NO = 1001;
    protected static final String TEST_PROVIDER = "999998";
    protected static final String TEST_MESSAGE = "Test tickler message";
    protected static final String TEST_ASSIGNED_TO = "999999";

    @BeforeEach
    void setUpTicklerMocks() {
        // Create mocks manually to avoid mockito-inline issues with interfaces
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);

        // Register common mocks with SpringUtils
        registerMock(EntityManager.class, mockEntityManager);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Tickler with sensible defaults.
     * Can be customized after creation for specific test needs.
     *
     * @return A valid Tickler instance for testing
     */
    protected Tickler createTestTickler() {
        Tickler tickler = new Tickler();
        tickler.setDemographicNo(TEST_DEMO_NO);
        tickler.setMessage(TEST_MESSAGE);
        tickler.setCreator(TEST_PROVIDER);
        tickler.setTaskAssignedTo(TEST_ASSIGNED_TO);
        tickler.setStatus(Tickler.STATUS.A);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setServiceDate(new Date());
        tickler.setCreateDate(new Date());
        tickler.setUpdateDate(new Date());
        return tickler;
    }

    /**
     * Creates a test Tickler with a specific ID.
     *
     * @param id The ID to set on the tickler
     * @return A Tickler instance with the specified ID
     */
    protected Tickler createTestTicklerWithId(Integer id) {
        Tickler tickler = createTestTickler();
        tickler.setId(id);
        return tickler;
    }

    /**
     * Creates a test Tickler with specific status.
     *
     * @param status The status to set
     * @return A Tickler instance with the specified status
     */
    protected Tickler createTestTicklerWithStatus(Tickler.STATUS status) {
        Tickler tickler = createTestTickler();
        tickler.setStatus(status);
        return tickler;
    }

    /**
     * Creates an invalid Tickler missing required fields.
     * Useful for validation testing.
     *
     * @return An invalid Tickler instance
     */
    protected Tickler createInvalidTickler() {
        Tickler tickler = new Tickler();
        // Missing required fields: demographicNo, creator, taskAssignedTo
        tickler.setMessage("Invalid tickler");
        return tickler;
    }

    /**
     * Helper method to inject EntityManager into DAO implementations using reflection.
     * Necessary because DAOs extend AbstractDaoImpl which uses SpringUtils.
     *
     * @param dao The DAO instance to inject into
     */
    protected void injectEntityManager(Object dao) {
        try {
            java.lang.reflect.Field field = dao.getClass().getSuperclass().getDeclaredField("entityManager");
            field.setAccessible(true);
            field.set(dao, mockEntityManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock EntityManager", e);
        }
    }

    /**
     * Helper to verify a query was created with expected HQL fragment.
     *
     * @param expectedFragment The HQL fragment that should be in the query
     */
    protected void verifyQueryContains(String expectedFragment) {
        org.mockito.ArgumentCaptor<String> queryCaptor =
            org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mockEntityManager).createQuery(queryCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(queryCaptor.getValue())
            .contains(expectedFragment);
    }
}