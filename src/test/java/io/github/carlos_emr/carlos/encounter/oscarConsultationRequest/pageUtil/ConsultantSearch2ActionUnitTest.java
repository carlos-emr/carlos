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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.consultation.dto.ConsultantOptionDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsultantSearch2Action}, the Consultant type-ahead JSON endpoint of the
 * Consultations list (issue #3976).
 *
 * @since 2026-09-30
 */
@DisplayName("ConsultantSearch2Action")
@Tag("unit")
@Tag("consultation")
class ConsultantSearch2ActionUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private ConsultationRequestDao consultationRequestDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        consultationRequestDao = mock(ConsultationRequestDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("GET", "/encounter/consultation/searchConsultants");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull()))
                .thenReturn(true);
        when(consultationRequestDao.searchDistinctConsultants(anyString(), anyInt())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private ConsultantSearch2Action newAction() {
        return new ConsultantSearch2Action(securityInfoManager, consultationRequestDao);
    }

    @Test
    @DisplayName("should reject a user without _con read before searching")
    void shouldThrowSecurityException_whenConPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_con"), eq("r"), isNull()))
                .thenReturn(false);
        request.setParameter("keyword", "smith");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_con)");
        verifyNoInteractions(consultationRequestDao);
    }

    @Test
    @DisplayName("should answer 401 without querying when there is no session")
    void shouldReturnUnauthorized_whenNotLoggedIn() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(consultationRequestDao);
    }

    @Test
    @DisplayName("should write label/value JSON and return NONE")
    void shouldWriteLabelValueJson_whenConsultantsMatch() throws Exception {
        request.setParameter("keyword", "  smith b ");
        when(consultationRequestDao.searchDistinctConsultants("smith b", ConsultantSearch2Action.MAX_RESULTS))
                .thenReturn(List.of(new ConsultantOptionDto(12, "Smith", "Brian"),
                        new ConsultantOptionDto(13, "Smithers", null)));

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode json = MAPPER.readTree(response.getContentAsString());
        assertThat(json.isArray()).isTrue();
        assertThat(json).hasSize(2);
        assertThat(json.get(0).get("label").asText()).isEqualTo("Smith, Brian");
        assertThat(json.get(0).get("value").asInt()).isEqualTo(12);
        assertThat(json.get(1).get("label").asText()).isEqualTo("Smithers");
    }

    @Test
    @DisplayName("should JSON-escape specialist names instead of emitting markup")
    void shouldEscapeNames_whenSpecialistNameContainsMarkupAndQuotes() throws Exception {
        request.setParameter("keyword", "evil");
        when(consultationRequestDao.searchDistinctConsultants(anyString(), anyInt()))
                .thenReturn(List.of(new ConsultantOptionDto(1, "</script><img src=x onerror=alert(1)>", "\"q\"")));

        newAction().execute();

        String body = response.getContentAsString();
        JsonNode json = MAPPER.readTree(body);
        assertThat(json.get(0).get("label").asText())
                .isEqualTo("</script><img src=x onerror=alert(1)>, \"q\"");
        // Quotes are escaped so a name can never break out of its JSON string.
        assertThat(body).contains("\\\"q\\\"");
    }

    @Test
    @DisplayName("should cap the response at 20 consultants even if the DAO returns more")
    void shouldCapResponse_atMaxResults() throws Exception {
        request.setParameter("keyword", "sm");
        List<ConsultantOptionDto> many = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            many.add(new ConsultantOptionDto(i, "Smith" + i, "A"));
        }
        when(consultationRequestDao.searchDistinctConsultants("sm", 20)).thenReturn(many);

        newAction().execute();

        assertThat(MAPPER.readTree(response.getContentAsString())).hasSize(20);
        verify(consultationRequestDao).searchDistinctConsultants("sm", 20);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", " ", "s", " s "})
    @DisplayName("should return an empty array without querying for keywords under two characters")
    void shouldReturnEmptyArray_whenKeywordTooShort(String keyword) throws Exception {
        request.setParameter("keyword", keyword);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).isEqualTo("[]");
        verifyNoInteractions(consultationRequestDao);
    }

    @Test
    @DisplayName("should return an empty array without querying when the keyword is missing or too long")
    void shouldReturnEmptyArray_whenKeywordMissingOrTooLong() throws Exception {
        newAction().execute();
        assertThat(response.getContentAsString()).isEqualTo("[]");

        response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        request.setParameter("keyword", "a".repeat(ConsultantSearch2Action.MAX_KEYWORD_LENGTH + 1));
        newAction().execute();
        assertThat(response.getContentAsString()).isEqualTo("[]");

        verifyNoInteractions(consultationRequestDao);
    }
}
