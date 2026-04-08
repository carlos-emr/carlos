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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
class ExcludeDemographicHandlerUnitTest {

    private static MockedStatic<SpringUtils> springUtilsMock;
    private static DemographicExtDao mockDao;
    private static ExcludeDemographicHandler handler;

    @BeforeAll
    static void setUpBeforeAll() {
        mockDao = mock(DemographicExtDao.class);

        springUtilsMock = Mockito.mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(DemographicExtDao.class))
                .thenReturn(mockDao);
        springUtilsMock.when(() -> SpringUtils.getBean(DashboardManager.class))
                .thenReturn(mock(DashboardManager.class));

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo("100");
        loggedInInfo.setLoggedInProvider(provider);

        handler = new ExcludeDemographicHandler();
        handler.setLoggedinInfo(loggedInInfo);
    }

    @AfterAll
    static void tearDownAfterAll() {
        if (springUtilsMock != null) {
            springUtilsMock.close();
        }
    }

    @Nested
    @DisplayName("excludeDemoIds(String, String) input validation")
    class ExcludeDemoIdsJsonValidation {

        @Test
        @DisplayName("should parse plain comma-separated integers")
        void shouldParseCommaSeparatedIntegers() {
            handler.excludeDemoIds("1,2,3", "testIndicator");
            verify(mockDao, times(3)).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should parse bracket-wrapped integer array")
        void shouldParseBracketWrappedIntegers() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("[10,20,30]", "testIndicator");
            verify(mockDao, times(3)).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should parse single integer without brackets")
        void shouldParseSingleInteger() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("42", "testIndicator");
            verify(mockDao, times(1)).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject JSON object injection payload")
        void shouldRejectJsonObjectInjection() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("{\"key\":\"value\"}", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject script injection payload")
        void shouldRejectScriptInjection() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("<script>alert(1)</script>", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject string values in array")
        void shouldRejectStringValues() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("[\"malicious\",\"payload\"]", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject nested array payload")
        void shouldRejectNestedArrayPayload() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("[[1,2],[3,4]]", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject string injection between brackets")
        void shouldRejectStringInjectionBetweenBrackets() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("1,2],\"injected\":[3", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should reject consecutive commas")
        void shouldRejectConsecutiveCommas() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("1,,3", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should handle null jsonString gracefully")
        void shouldHandleNullInput() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds((String) null, "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should handle empty jsonString gracefully")
        void shouldHandleEmptyInput() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds("", "testIndicator");
            verify(mockDao, never()).addKey(anyString(), anyInt(), anyString(), anyString());
        }

        @Test
        @DisplayName("should handle integers with whitespace")
        void shouldHandleIntegersWithWhitespace() {
            Mockito.clearInvocations(mockDao);
            handler.excludeDemoIds(" 1 , 2 , 3 ", "testIndicator");
            verify(mockDao, times(3)).addKey(anyString(), anyInt(), anyString(), anyString());
        }
    }
}
