/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Unit tests for {@link ExcludeDemographicHandler} JSON input validation.
 *
 * <p>Verifies that the integer array parsing rejects malicious input
 * (e.g. JSON injection payloads) and accepts only valid integer arrays.
 * Addresses SonarCloud S6398 false positive by proving the allowlist
 * validation is effective.
 *
 * @since 2026-04-08
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("ExcludeDemographicHandler unit tests")
@org.junit.jupiter.api.parallel.Isolated
class ExcludeDemographicHandlerUnitTest extends CarlosUnitTestBase {

    private Object originalDao;

    private DemographicExtDao mockDao;
    private ExcludeDemographicHandler handler;

    @BeforeEach
    void prepareHandler() throws Exception {
        mockDao = mock(DemographicExtDao.class);
        when(mockDao.getDemographicExtByKeyAndValue(anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        registerMock(DemographicExtDao.class, mockDao);
        registerMock(DashboardManager.class, mock(DashboardManager.class));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo("100");
        loggedInInfo.setLoggedInProvider(provider);

        handler = new ExcludeDemographicHandler();
        handler.setLoggedinInfo(loggedInInfo);

        // The production code declares demographicExtDao as a static field, which may
        // have been initialized before our MockedStatic was active (class loading order).
        // Use reflection to ensure the mock is injected regardless of load order.
        Field daoField = ExcludeDemographicHandler.class.getDeclaredField("demographicExtDao");
        daoField.setAccessible(true);
        originalDao = daoField.get(null);
        daoField.set(null, mockDao);
    }

    @AfterEach
    void restoreDao() throws Exception {
        Field daoField = ExcludeDemographicHandler.class.getDeclaredField("demographicExtDao");
        daoField.setAccessible(true);
        daoField.set(null, originalDao);
    }

    @Nested
    @DisplayName("excludeDemoIds(String, String) input validation")
    class ExcludeDemoIdsJsonValidation {

        @BeforeEach
        void clearMocks() {
            Mockito.clearInvocations(mockDao);
        }

        @Test
        @DisplayName("should parse plain comma-separated integers")
        void shouldParseCommaSeparatedIntegers() {
            handler.excludeDemoIds("1,2,3", "testIndicator");
            verify(mockDao, times(3)).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should parse bracket-wrapped integer array")
        void shouldParseBracketWrappedIntegers() {
            handler.excludeDemoIds("[10,20,30]", "testIndicator");
            verify(mockDao, times(3)).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should parse single integer without brackets")
        void shouldParseSingleInteger() {
            handler.excludeDemoIds("42", "testIndicator");
            verify(mockDao, times(1)).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject JSON object injection payload")
        void shouldRejectJsonObjectInjection() {
            handler.excludeDemoIds("{\"key\":\"value\"}", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject script injection payload")
        void shouldRejectScriptInjection() {
            handler.excludeDemoIds("<script>alert(1)</script>", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject string values in array")
        void shouldRejectStringValues() {
            handler.excludeDemoIds("[\"malicious\",\"payload\"]", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject nested array payload")
        void shouldRejectNestedArrayPayload() {
            handler.excludeDemoIds("[[1,2],[3,4]]", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject string injection between brackets")
        void shouldRejectStringInjectionBetweenBrackets() {
            handler.excludeDemoIds("1,2],\"injected\":[3", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject consecutive commas")
        void shouldRejectConsecutiveCommas() {
            handler.excludeDemoIds("1,,3", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should handle null jsonString gracefully")
        void shouldHandleNullInput() {
            handler.excludeDemoIds((String) null, "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should handle empty jsonString gracefully")
        void shouldHandleEmptyInput() {
            handler.excludeDemoIds("", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should handle integers with whitespace")
        void shouldHandleIntegersWithWhitespace() {
            handler.excludeDemoIds(" 1 , 2 , 3 ", "testIndicator");
            verify(mockDao, times(3)).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }

        @Test
        @DisplayName("should reject integer overflow values gracefully")
        void shouldRejectIntegerOverflow() {
            handler.excludeDemoIds("99999999999999999999", "testIndicator");
            verify(mockDao, never()).addKeyIfAbsentSince(anyString(), anyInt(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(java.util.Date.class));
        }
    }

    @Nested
    @DisplayName("unExcludeDemoIds(String, String) input validation")
    class UnExcludeDemoIdsJsonValidation {

        @BeforeEach
        void clearMocks() {
            Mockito.clearInvocations(mockDao);
        }

        @Test
        @DisplayName("should accept valid comma-separated integers")
        void shouldAcceptValidInput() {
            handler.unExcludeDemoIds("1,2,3", "testIndicator");
            verify(mockDao).getDemographicExtByKeyAndValue(anyString(), anyString());
            verify(mockDao, never()).removeDemographicExt(anyInt());
        }

        @Test
        @DisplayName("should reject JSON object injection payload")
        void shouldRejectJsonObjectInjection() {
            handler.unExcludeDemoIds("{\"key\":\"value\"}", "testIndicator");
            verify(mockDao, never()).getDemographicExtByKeyAndValue(anyString(), anyString());
        }

        @Test
        @DisplayName("should reject script injection payload")
        void shouldRejectScriptInjection() {
            handler.unExcludeDemoIds("<script>alert(1)</script>", "testIndicator");
            verify(mockDao, never()).getDemographicExtByKeyAndValue(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle null jsonString gracefully")
        void shouldHandleNullInput() {
            handler.unExcludeDemoIds((String) null, "testIndicator");
            verify(mockDao, never()).getDemographicExtByKeyAndValue(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle empty jsonString gracefully")
        void shouldHandleEmptyInput() {
            handler.unExcludeDemoIds("", "testIndicator");
            verify(mockDao, never()).getDemographicExtByKeyAndValue(anyString(), anyString());
        }
    }
}
