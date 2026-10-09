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
package io.github.carlos_emr.carlos.chartspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChartSpaceAllergies2Action}; no Spring context. The
 * validator is real (built on a mock {@code SecurityInfoManager}) so the
 * check order is exercised end to end; the loader is a mock.
 *
 * @since 2026-10-09
 */
@Tag("unit")
class ChartSpaceAllergies2ActionUnitTest {

    private static final String SESSION_KEY = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
    private static final ObjectMapper JSON = new ObjectMapper();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo info;
    private SecurityInfoManager securityInfoManager;
    private AllergyBlockLoader loader;
    private ChartSpaceAllergies2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        request.setMethod("GET");
        info = mock(LoggedInInfo.class);
        session.setAttribute(SESSION_KEY, info);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        securityInfoManager = mock(SecurityInfoManager.class);
        loader = mock(AllergyBlockLoader.class);
        action = new ChartSpaceAllergies2Action(new ChartSpaceRequestValidator(securityInfoManager), loader);
    }

    @AfterEach
    void tearDown() {
        ActionContext.clear();
    }

    private void allowEChartRead() {
        when(securityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), eq("2"))).thenReturn(true);
    }

    @Test
    void shouldWriteJson_whenAuthorized() throws Exception {
        request.setParameter("demographicNo", "2");
        allowEChartRead();
        String markup = "<img src=x onerror=alert(1)>";
        when(loader.load(info, 2)).thenReturn(new AllergyBlockDto(BlockStatus.OK,
                List.of(new AllergyBlockDto.Item(markup, "3", "Hives", "2020-03-15"))));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("OK");
        assertThat(body.get("items").get(0).get("description").asText()).isEqualTo(markup);
        assertThat(body.get("items").get(0).get("severityCode").asText()).isEqualTo("3");
        assertThat(body.get("items").get(0).get("reaction").asText()).isEqualTo("Hives");
        assertThat(body.get("items").get(0).get("startDate").asText()).isEqualTo("2020-03-15");
    }

    @Test
    void shouldWriteNoAccessJson_whenLoaderSaysNoAccess() throws Exception {
        request.setParameter("demographicNo", "2");
        allowEChartRead();
        when(loader.load(info, 2)).thenReturn(new AllergyBlockDto(BlockStatus.NO_ACCESS, List.of()));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        JsonNode body = JSON.readTree(response.getContentAsString());
        assertThat(body.get("status").asText()).isEqualTo("NO_ACCESS");
        assertThat(body.get("items").isArray()).isTrue();
        assertThat(body.get("items")).isEmpty();
    }

    @Test
    void shouldThrowSecurityException_whenEChartReadMissing() {
        request.setParameter("demographicNo", "2");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
        verifyNoInteractions(loader);
    }

    @Test
    void shouldReturn400_whenDemographicNoInvalid() throws Exception {
        request.setParameter("demographicNo", "abc");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(securityInfoManager, loader);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE"})
    void shouldReturn405_forNonGetMethods(String method) throws Exception {
        request.setMethod(method);
        request.setParameter("demographicNo", "2");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(loader);
    }

    @Test
    void shouldMapRoute_withoutResults() throws Exception {
        var action = ViewChartSpace2ActionIntegrationTest.findAction(
                ViewChartSpace2ActionIntegrationTest.parseStrutsConfig(
                        "src/main/webapp/WEB-INF/classes/struts-encounter.xml"),
                "encounter/chartspace/allergies");

        assertThat(action).as("encounter/chartspace/allergies action mapping").isNotNull();
        assertThat(action.getAttribute("class")).isEqualTo(ChartSpaceAllergies2Action.SPRING_BEAN_NAME);
        assertThat(action.getElementsByTagName("result").getLength()).isZero();
    }
}
