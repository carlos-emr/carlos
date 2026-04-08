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

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that {@link DxDaoImpl} rejects coding system names not
 * present in its {@code VALID_CODING_SYSTEMS} allowlist. These tests confirm
 * that arbitrary table-name probing (information disclosure) is prevented and
 * that the CodeQL {@code java/sql-injection} suppression is justified.
 *
 * @since 2026-04-08
 * @see DxDaoImpl
 */
@DisplayName("DxDao Allowlist Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
@ExtendWith(MockitoExtension.class)
class DxDaoAllowlistUnitTest {

    @Mock
    private EntityManager mockEntityManager;

    @Mock
    private Query mockQuery;

    private DxDaoImpl dao;

    @BeforeEach
    void setUp() throws Exception {
        dao = new DxDaoImpl();
        // Inject mock EntityManager via reflection (entityManager field is in AbstractDaoImpl)
        java.lang.reflect.Field field = AbstractDaoImpl.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(dao, mockEntityManager);
    }

    @Nested
    @DisplayName("Rejects invalid coding systems")
    class RejectsInvalidCodingSystems {

        @ParameterizedTest(name = "findCodingSystemDescription(code) rejects \"{0}\"")
        @ValueSource(strings = {"provider", "demographic", "drugs", "SELECT 1;--", "", "ICD9", "Provider"})
        @DisplayName("should return empty list for invalid coding system in findCodingSystemDescription(code)")
        void shouldReturnEmptyList_whenInvalidCodingSystemForCode(String invalidSystem) {
            List<Object[]> result = dao.findCodingSystemDescription(invalidSystem, "001");

            assertThat(result).isEmpty();
            verify(mockEntityManager, never()).createNativeQuery(anyString());
        }

        @ParameterizedTest(name = "findCodingSystemDescription(keywords) rejects \"{0}\"")
        @ValueSource(strings = {"provider", "demographic", "drugs", "SELECT 1;--", "", "ICD9"})
        @DisplayName("should return empty list for invalid coding system in findCodingSystemDescription(keywords)")
        void shouldReturnEmptyList_whenInvalidCodingSystemForKeywords(String invalidSystem) {
            List<Object[]> result = dao.findCodingSystemDescription(invalidSystem, new String[]{"test"});

            assertThat(result).isEmpty();
            verify(mockEntityManager, never()).createNativeQuery(anyString());
        }

        @ParameterizedTest(name = "getCodeDescription rejects \"{0}\"")
        @ValueSource(strings = {"provider", "demographic", "drugs", "SELECT 1;--", "", "ICD9"})
        @DisplayName("should return empty string for invalid coding system in getCodeDescription")
        void shouldReturnEmptyString_whenInvalidCodingSystemForDescription(String invalidSystem) {
            String result = dao.getCodeDescription(invalidSystem, "001");

            assertThat(result).isEmpty();
            verify(mockEntityManager, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("should return empty list for null coding system in findCodingSystemDescription(code)")
        void shouldReturnEmptyList_whenNullCodingSystemForCode() {
            List<Object[]> result = dao.findCodingSystemDescription(null, "001");

            assertThat(result).isEmpty();
            verify(mockEntityManager, never()).createNativeQuery(anyString());
        }

        @Test
        @DisplayName("should return empty string for null coding system in getCodeDescription")
        void shouldReturnEmptyString_whenNullCodingSystemForDescription() {
            String result = dao.getCodeDescription(null, "001");

            assertThat(result).isEmpty();
            verify(mockEntityManager, never()).createNativeQuery(anyString());
        }
    }

    @Nested
    @DisplayName("Accepts valid coding systems")
    class AcceptsValidCodingSystems {

        @ParameterizedTest(name = "findCodingSystemDescription(code) accepts \"{0}\"")
        @ValueSource(strings = {"icd9", "icd10", "ichppccode", "SnomedCore", "msp"})
        @DisplayName("should execute query for valid coding system in findCodingSystemDescription(code)")
        void shouldExecuteQuery_whenValidCodingSystem(String validSystem) {
            when(mockEntityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
            when(mockQuery.getResultList()).thenReturn(List.of());

            dao.findCodingSystemDescription(validSystem, "001");

            verify(mockEntityManager).createNativeQuery(anyString());
        }

        @ParameterizedTest(name = "getCodeDescription accepts \"{0}\"")
        @ValueSource(strings = {"icd9", "icd10", "ichppccode", "SnomedCore", "msp"})
        @DisplayName("should execute query for valid coding system in getCodeDescription")
        void shouldExecuteQuery_whenValidCodingSystemForDescription(String validSystem) {
            when(mockEntityManager.createNativeQuery(anyString())).thenReturn(mockQuery);
            when(mockQuery.getSingleResult()).thenReturn("Test description");

            String result = dao.getCodeDescription(validSystem, "001");

            assertThat(result).isEqualTo("Test description");
            verify(mockEntityManager).createNativeQuery(anyString());
        }
    }
}
