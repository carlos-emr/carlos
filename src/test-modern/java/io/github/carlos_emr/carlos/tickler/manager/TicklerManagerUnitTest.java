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
package io.github.carlos_emr.carlos.tickler.manager;

import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerUpdateDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerCommentDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerUpdate;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import io.github.carlos_emr.carlos.tickler.TicklerUnitTestBase;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TicklerManager business logic.
 *
 * <p>This test class demonstrates unit testing of service/manager layer components.
 * It focuses on business logic validation, workflow orchestration, and security
 * checks without requiring database access or Spring context.</p>
 *
 * <p><b>Key Patterns Demonstrated:</b></p>
 * <ul>
 *   <li>Validation logic testing</li>
 *   <li>Security privilege verification</li>
 *   <li>Business rule enforcement</li>
 *   <li>Manager-DAO interaction patterns</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see TicklerManagerImpl
 * @see TicklerUnitTestBase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tickler Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class TicklerManagerUnitTest extends TicklerUnitTestBase {

    @Mock
    private TicklerDao mockTicklerDao;

    @Mock
    private TicklerUpdateDao mockTicklerUpdateDao;

    @Mock
    private TicklerCommentDao mockTicklerCommentDao;

    private TicklerManagerImpl ticklerManager;

    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(TicklerDao.class, mockTicklerDao);
        registerMock(TicklerUpdateDao.class, mockTicklerUpdateDao);
        registerMock(TicklerCommentDao.class, mockTicklerCommentDao);

        // Security manager returns true for all privilege checks in unit tests
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // Create manager instance
        ticklerManager = new TicklerManagerImpl();

        // Inject dependencies using reflection
        injectDependency(ticklerManager, "ticklerDao", mockTicklerDao);
        injectDependency(ticklerManager, "ticklerUpdateDao", mockTicklerUpdateDao);
        injectDependency(ticklerManager, "ticklerCommentDao", mockTicklerCommentDao);
        injectDependency(ticklerManager, "securityInfoManager", mockSecurityInfoManager);
    }

    @Nested
    @DisplayName("Validation Logic")
    class ValidationLogic {

        @Test
        @DisplayName("should validate tickler with all required fields")
        void shouldValidateTickler_whenAllRequiredFieldsPresent() {
            // Given - Valid tickler
            Tickler validTickler = createTestTickler();

            // When
            boolean isValid = ticklerManager.validateTicklerIsValid(validTickler);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("should reject tickler missing demographic number")
        void shouldRejectTickler_whenDemographicNumberMissing() {
            // Given - Invalid tickler
            Tickler invalidTickler = createTestTickler();
            invalidTickler.setDemographicNo(null);

            // When
            boolean isValid = ticklerManager.validateTicklerIsValid(invalidTickler);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject tickler with zero demographic number")
        void shouldRejectTickler_whenDemographicNumberIsZero() {
            // Given - Invalid tickler
            Tickler invalidTickler = createTestTickler();
            invalidTickler.setDemographicNo(0);

            // When
            boolean isValid = ticklerManager.validateTicklerIsValid(invalidTickler);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject tickler missing creator")
        void shouldRejectTickler_whenCreatorMissing() {
            // Given
            Tickler invalidTickler = createTestTickler();
            invalidTickler.setCreator("");

            // When
            boolean isValid = ticklerManager.validateTicklerIsValid(invalidTickler);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("should reject null tickler")
        void shouldRejectNullTickler() {
            // When
            boolean isValid = ticklerManager.validateTicklerIsValid(null);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Business Operations")
    class BusinessOperations {

        @Test
        @DisplayName("should persist valid tickler when adding")
        void shouldPersistValidTickler_whenAdding() {
            // Given
            Tickler validTickler = createTestTickler();

            // When
            boolean success = ticklerManager.addTickler(mockLoggedInInfo, validTickler);

            // Then
            assertThat(success).isTrue();
            verify(mockTicklerDao).persist(validTickler);
        }

        @Test
        @DisplayName("should not persist invalid tickler")
        void shouldNotPersistInvalidTickler() {
            // Given
            Tickler invalidTickler = createInvalidTickler();

            // When
            boolean success = ticklerManager.addTickler(mockLoggedInInfo, invalidTickler);

            // Then
            assertThat(success).isFalse();
            verify(mockTicklerDao, never()).persist(any());
        }

        @Test
        @DisplayName("should persist new updates when updating tickler")
        void shouldPersistNewUpdates_whenUpdatingTickler() {
            // Given
            Tickler tickler = createTestTicklerWithId(123);
            TicklerUpdate newUpdate = new TicklerUpdate();
            newUpdate.setId(null); // New update with no ID
            newUpdate.setTicklerNo(123);

            tickler.setUpdates(new HashSet<>());
            tickler.getUpdates().add(newUpdate);

            // When
            boolean success = ticklerManager.updateTickler(mockLoggedInInfo, tickler);

            // Then
            assertThat(success).isTrue();
            verify(mockTicklerUpdateDao).persist(newUpdate);
            verify(mockTicklerDao).merge(tickler);
        }

        @Test
        @DisplayName("should not update invalid tickler")
        void shouldNotUpdateInvalidTickler() {
            // Given
            Tickler invalidTickler = createTestTicklerWithId(456);
            invalidTickler.setTaskAssignedTo(null); // Make it invalid

            // When
            boolean success = ticklerManager.updateTickler(mockLoggedInInfo, invalidTickler);

            // Then
            assertThat(success).isFalse();
            verify(mockTicklerDao, never()).merge(any());
            verify(mockTicklerUpdateDao, never()).persist(any());
        }
    }

    /**
     * Helper method to inject dependencies into manager using reflection.
     */
    private void injectDependency(Object target, String fieldName, Object dependency) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }
}