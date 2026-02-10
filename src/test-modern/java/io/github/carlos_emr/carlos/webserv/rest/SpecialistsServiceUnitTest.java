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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProfessionalSpecialistConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProfessionalSpecialistTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpecialistsService}.
 *
 * <p>Tests the specialist search REST endpoint including name parsing logic,
 * pagination parameter handling, security enforcement, and error handling.
 * Uses Mockito to isolate the service from its dependencies.</p>
 *
 * @see SpecialistsService
 * @since 2026-02-10
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpecialistsService Unit Tests")
@Tag("unit")
@Tag("fast")
class SpecialistsServiceUnitTest {

    /**
     * Tests for the splitSearchString static utility method.
     *
     * <p>Verifies correct parsing of name search strings in various formats
     * including null input, last name only, and "lastName, firstName" format.</p>
     */
    @Nested
    @DisplayName("splitSearchString - Name Parsing")
    class SplitSearchStringTests {

        @Test
        @DisplayName("should return null array when input is null")
        void shouldReturnNullArray_whenInputIsNull() {
            String[] result = SpecialistsService.splitSearchString(null);

            assertThat(result).hasSize(2);
            assertThat(result[0]).isNull();
            assertThat(result[1]).isNull();
        }

        @Test
        @DisplayName("should parse last name only when no comma present")
        void shouldParseLastNameOnly_whenNoCommaPresent() {
            String[] result = SpecialistsService.splitSearchString("Smith");

            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo("Smith");
            assertThat(result[1]).isNull();
        }

        @Test
        @DisplayName("should parse last name and first name when comma separated")
        void shouldParseLastAndFirstName_whenCommaSeparated() {
            String[] result = SpecialistsService.splitSearchString("Smith, Jane");

            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo("Smith");
            assertThat(result[1]).isEqualTo("Jane");
        }

        @Test
        @DisplayName("should trim whitespace around names")
        void shouldTrimWhitespace_whenNamesHaveExtraSpaces() {
            String[] result = SpecialistsService.splitSearchString("  Smith ,  Jane  ");

            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo("Smith");
            assertThat(result[1]).isEqualTo("Jane");
        }

        @Test
        @DisplayName("should return null first name when comma has only whitespace after it")
        void shouldReturnNullFirstName_whenCommaFollowedByWhitespace() {
            String[] result = SpecialistsService.splitSearchString("Smith,   ");

            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo("Smith");
            assertThat(result[1]).isNull();
        }

        @Test
        @DisplayName("should return null for empty string input")
        void shouldReturnNull_whenInputIsEmpty() {
            String[] result = SpecialistsService.splitSearchString("   ");

            assertThat(result).hasSize(2);
            assertThat(result[0]).isNull();
            assertThat(result[1]).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "'Doe', Doe, ",
                "'Doe, John', Doe, John",
                "'O''Brien', O'Brien, ",
                "'Smith-Jones', Smith-Jones, ",
                "'De La Cruz, Maria', De La Cruz, Maria"
        })
        @DisplayName("should correctly parse various name formats")
        void shouldParseVariousNameFormats(String input, String expectedLast, String expectedFirst) {
            String[] result = SpecialistsService.splitSearchString(input);

            assertThat(result[0]).isEqualTo(expectedLast);
            if (expectedFirst == null || expectedFirst.isEmpty()) {
                assertThat(result[1]).isNull();
            } else {
                assertThat(result[1]).isEqualTo(expectedFirst);
            }
        }
    }

    /**
     * Tests for the searchSpecialists REST endpoint method.
     *
     * <p>Verifies security enforcement, DAO query delegation, result conversion,
     * pagination handling, and error scenarios.</p>
     */
    @Nested
    @DisplayName("searchSpecialists - Endpoint")
    class SearchSpecialistsTests {

        @Mock
        private ProfessionalSpecialistDao specialistDao;

        @Mock
        private ProfessionalSpecialistConverter converter;

        @Mock
        private SecurityInfoManager securityInfoManager;

        @Mock
        private LoggedInInfo loggedInInfo;

        @Mock
        private Message cxfMessage;

        @Mock
        private HttpServletRequest httpServletRequest;

        @InjectMocks
        private SpecialistsService service;

        private MockedStatic<PhaseInterceptorChain> phaseInterceptorChainMock;

        @BeforeEach
        void setUp() {
            phaseInterceptorChainMock = mockStatic(PhaseInterceptorChain.class);
            phaseInterceptorChainMock.when(PhaseInterceptorChain::getCurrentMessage).thenReturn(cxfMessage);
            when(cxfMessage.get(AbstractHTTPDestination.HTTP_REQUEST)).thenReturn(httpServletRequest);

            try (MockedStatic<LoggedInInfo> loggedInInfoMock = mockStatic(LoggedInInfo.class)) {
                loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(httpServletRequest))
                        .thenReturn(loggedInInfo);
                loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(httpServletRequest))
                        .thenReturn(loggedInInfo);
            }

            lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);
        }

        @org.junit.jupiter.api.AfterEach
        void tearDown() {
            if (phaseInterceptorChainMock != null) {
                phaseInterceptorChainMock.close();
            }
        }

        @Test
        @DisplayName("should throw SecurityException when user lacks consultation privilege")
        void shouldThrowSecurityException_whenUserLacksPrivilege() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(false);

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                assertThatThrownBy(() -> service.searchSpecialists(null, null, 1, 10))
                        .isInstanceOf(SecurityException.class)
                        .hasMessageContaining("Missing required security object");
            }
        }

        @Test
        @DisplayName("should return success with empty list when no specialists match")
        void shouldReturnEmptyList_whenNoSpecialistsMatch() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(isNull(), isNull(), isNull(), eq(0), eq(10)))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                RestResponse<List<ProfessionalSpecialistTo1>> result =
                        service.searchSpecialists(null, null, 1, 10);

                assertThat(result.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
                assertThat(result.getBody()).isEmpty();
                assertThat(result.getError()).isNull();
            }
        }

        @Test
        @DisplayName("should return converted specialists on successful search")
        void shouldReturnConvertedSpecialists_whenSearchSucceeds() {
            ProfessionalSpecialist specialist = new ProfessionalSpecialist();
            specialist.setFirstName("Jane");
            specialist.setLastName("Smith");

            ProfessionalSpecialistTo1 transferObject = new ProfessionalSpecialistTo1();
            transferObject.setFirstName("Jane");
            transferObject.setLastName("Smith");
            transferObject.setName("Smith, Jane");

            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(eq("Smith"), isNull(), isNull(), eq(0), eq(10)))
                    .thenReturn(List.of(specialist));
            when(converter.getAsTransferObject(any(), eq(specialist)))
                    .thenReturn(transferObject);

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                RestResponse<List<ProfessionalSpecialistTo1>> result =
                        service.searchSpecialists("Smith", null, 1, 10);

                assertThat(result.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
                assertThat(result.getBody()).hasSize(1);
                assertThat(result.getBody().get(0).getLastName()).isEqualTo("Smith");
                assertThat(result.getBody().get(0).getFirstName()).isEqualTo("Jane");
            }
        }

        @Test
        @DisplayName("should calculate correct offset for page 2")
        void shouldCalculateCorrectOffset_whenPageIsTwo() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(isNull(), isNull(), isNull(), eq(10), eq(10)))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                service.searchSpecialists(null, null, 2, 10);

                verify(specialistDao).findByFullNameAndReferralNo(isNull(), isNull(), isNull(), eq(10), eq(10));
            }
        }

        @Test
        @DisplayName("should normalize page to 1 when negative value provided")
        void shouldNormalizePage_whenNegativeValueProvided() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(isNull(), isNull(), isNull(), eq(0), eq(10)))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                service.searchSpecialists(null, null, -1, 10);

                verify(specialistDao).findByFullNameAndReferralNo(isNull(), isNull(), isNull(), eq(0), eq(10));
            }
        }

        @Test
        @DisplayName("should pass referral number to DAO when provided")
        void shouldPassReferralNumber_whenProvided() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(isNull(), isNull(), eq("12345"), eq(0), eq(10)))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                service.searchSpecialists(null, "12345", 1, 10);

                verify(specialistDao).findByFullNameAndReferralNo(isNull(), isNull(), eq("12345"), eq(0), eq(10));
            }
        }

        @Test
        @DisplayName("should split name and pass both parts to DAO")
        void shouldSplitName_whenCommaFormatProvided() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(eq("Smith"), eq("Jane"), isNull(), eq(0), eq(10)))
                    .thenReturn(Collections.emptyList());

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                service.searchSpecialists("Smith, Jane", null, 1, 10);

                verify(specialistDao).findByFullNameAndReferralNo(eq("Smith"), eq("Jane"), isNull(), eq(0), eq(10));
            }
        }

        @Test
        @DisplayName("should return error response when unexpected exception occurs")
        void shouldReturnErrorResponse_whenExceptionOccurs() {
            when(securityInfoManager.hasPrivilege(any(), eq("_con"), eq("r"), isNull()))
                    .thenReturn(true);
            when(specialistDao.findByFullNameAndReferralNo(any(), any(), any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Database error"));

            try (MockedStatic<LoggedInInfo> liMock = mockStatic(LoggedInInfo.class)) {
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
                        .thenReturn(loggedInInfo);
                liMock.when(() -> LoggedInInfo.getLoggedInInfoFromRequest(any()))
                        .thenReturn(loggedInInfo);
                lenient().when(loggedInInfo.getLoggedInProvider()).thenReturn(null);

                RestResponse<List<ProfessionalSpecialistTo1>> result =
                        service.searchSpecialists("Smith", null, 1, 10);

                assertThat(result.getStatus()).isEqualTo(ResponseStatus.ERROR);
                assertThat(result.getError()).isNotNull();
                assertThat(result.getError().getMessage()).isEqualTo("Unexpected error");
                assertThat(result.getBody()).isNull();
            }
        }
    }
}
