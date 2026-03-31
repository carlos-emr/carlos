/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HqlQueryHelper} Hibernate query execution utility.
 *
 * @since 2026-03-31
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HqlQueryHelper Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class HqlQueryHelperUnitTest {

    @Mock private Session mockSession;
    @Mock private Query<?> mockQuery;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(mockSession.createQuery(anyString())).thenReturn((Query) mockQuery);
        when(mockQuery.getResultList()).thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("should execute query with no params")
        void shouldExecuteQuery_withNoParams() {
            List<?> result = HqlQueryHelper.find(mockSession, "from Entity");
            assertThat(result).isNotNull();
            verify(mockSession).createQuery("from Entity");
        }

        @Test
        @DisplayName("should bind positional parameters 1-based")
        void shouldBindPositionalParams_oneBased() {
            HqlQueryHelper.find(mockSession, "from Entity where id = ?1 and name = ?2", 42, "test");
            verify(mockQuery).setParameter(1, 42);
            verify(mockQuery).setParameter(2, "test");
        }

        @Test
        @DisplayName("should throw NullPointerException for null session")
        void shouldThrow_forNullSession() {
            assertThatThrownBy(() -> HqlQueryHelper.find(null, "from Entity"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should translate HibernateException to DataAccessException")
        void shouldTranslateHibernateException() {
            when(mockQuery.getResultList()).thenThrow(new HibernateException("test error"));

            assertThatThrownBy(() -> HqlQueryHelper.find(mockSession, "bad query"))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Nested
    @DisplayName("findWithLimit")
    class FindWithLimit {

        @Test
        @DisplayName("should set maxResults when positive")
        void shouldSetMaxResults_whenPositive() {
            HqlQueryHelper.findWithLimit(mockSession, "from Entity", 10);
            verify(mockQuery).setMaxResults(10);
        }

        @Test
        @DisplayName("should not set maxResults when -1")
        void shouldNotSetMaxResults_whenNegativeOne() {
            HqlQueryHelper.findWithLimit(mockSession, "from Entity", -1);
            verify(mockQuery, never()).setMaxResults(anyInt());
        }

        @Test
        @DisplayName("should bind params and set limit together")
        void shouldBindParamsAndSetLimit() {
            HqlQueryHelper.findWithLimit(mockSession, "from Entity where x = ?1", 5, "value");
            verify(mockQuery).setParameter(1, "value");
            verify(mockQuery).setMaxResults(5);
        }

        @Test
        @DisplayName("should throw NullPointerException for null session")
        void shouldThrow_forNullSession() {
            assertThatThrownBy(() -> HqlQueryHelper.findWithLimit(null, "from Entity", 10))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("bulkUpdate")
    class BulkUpdate {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should execute update query and return affected rows")
        void shouldExecuteUpdate_andReturnAffectedRows() {
            when(mockQuery.executeUpdate()).thenReturn(3);

            int result = HqlQueryHelper.bulkUpdate(mockSession, "update Entity set x = ?1 where y = ?2", "val", "cond");

            assertThat(result).isEqualTo(3);
            verify(mockQuery).setParameter(1, "val");
            verify(mockQuery).setParameter(2, "cond");
        }

        @Test
        @DisplayName("should throw NullPointerException for null session")
        void shouldThrow_forNullSession() {
            assertThatThrownBy(() -> HqlQueryHelper.bulkUpdate(null, "update Entity set x = 1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should translate HibernateException to DataAccessException")
        void shouldTranslateException() {
            when(mockQuery.executeUpdate()).thenThrow(new HibernateException("update error"));

            assertThatThrownBy(() -> HqlQueryHelper.bulkUpdate(mockSession, "bad update"))
                    .isInstanceOf(DataAccessException.class);
        }
    }
}
