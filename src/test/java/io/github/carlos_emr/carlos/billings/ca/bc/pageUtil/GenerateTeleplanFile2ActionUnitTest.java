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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.bc.MSP.TeleplanFileWriter;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.TeleplanSubmission;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillActivityDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Generate Teleplan File (issue #4439 D3): the Administration menu opens the page with a GET, and that GET wrote an
 * MSP batch of its own (the claims' billed status, the billactivity row, the Teleplan sequence and the files in
 * HOME_DIR). A GET without {@code providers} now only renders the page, a GET or HEAD naming {@code providers} is
 * refused with 405, and only the Create Report form's POST generates.
 *
 * @since 2026-10-22
 */
@DisplayName("BC Generate Teleplan File: only the posted form generates")
@Tag("unit")
@Tag("billing")
@Tag("security")
class GenerateTeleplanFile2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PRACTITIONER = "12345";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private BillingmasterDAO billingmasterDao;
    private DemographicManager demographicManager;
    private MockedStatic<ServletActionContext> servlet;
    private MockedConstruction<ProviderData> providerData;
    private MockedConstruction<TeleplanFileWriter> writer;
    private MockedConstruction<BillActivityDAO> billActivity;
    private TeleplanSubmission submission;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setRequestURI("/carlos/billing/CA/BC/GenerateTeleplanFile");
        response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_billing", "w", null)).thenReturn(true);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        demographicManager = createAndRegisterMock(DemographicManager.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);

        submission = mock(TeleplanSubmission.class);
        providerData = mockConstruction(ProviderData.class, (mock, context) ->
                when(mock.getProviderListWithInsuranceNo(anyString())).thenReturn(List.of("101")));
        writer = mockConstruction(TeleplanFileWriter.class, (mock, context) ->
                when(mock.getSubmission(any(), anyBoolean(), any(), any())).thenReturn(submission));
        billActivity = mockConstruction(BillActivityDAO.class, (mock, context) -> {
            when(mock.getNextMonthlySequence("")).thenReturn("1");
            when(mock.getMonthCode()).thenReturn("J");
        });
    }

    @AfterEach
    void tearDown() {
        billActivity.close();
        writer.close();
        providerData.close();
        servlet.close();
    }

    private String execute() throws Exception {
        return new GenerateTeleplanFile2Action().execute();
    }

    private void assertNothingRead() {
        assertThat(providerData.constructed()).isEmpty();
        assertThat(writer.constructed()).isEmpty();
        assertThat(billActivity.constructed()).isEmpty();
        verifyNoInteractions(billingmasterDao, demographicManager, submission);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRefuseWith405_whenGetOrHeadNamesProviders(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.addParameter("providers", PRACTITIONER);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertNothingRead();
    }

    /** The menu item opens the page bare; the archive links name only a year. */
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldOnlyRenderThePage_whenOpenedWithoutProviders(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.addParameter("year", "2025");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertNothingRead();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE"})
    void shouldRefuseWith405_forAnyOtherMethodButPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.addParameter("providers", PRACTITIONER);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertNothingRead();
    }

    @Test
    void shouldGenerateTheBatch_whenTheFormPostsAProvider() throws Exception {
        request.setMethod("POST");
        request.addParameter("providers", PRACTITIONER);

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(providerData.constructed().get(0)).getProviderListWithInsuranceNo(PRACTITIONER);
        verify(writer.constructed().get(0)).getSubmission(any(), eq(false), any(ProviderData[].class), nullable(String.class));
        verify(submission).commit(startsWith("HJ"), nullable(String.class), eq("J"), eq("1"), nullable(String.class));
        assertThat(request.getAttribute("error")).isNull();
    }

    @Test
    void shouldGenerateForEveryProvider_whenAllIsPosted() throws Exception {
        request.setMethod("POST");
        request.addParameter("providers", "all");

        execute();

        verify(providerData.constructed().get(0)).getProviderListWithInsuranceNo("%");
        verify(submission).commit(anyString(), nullable(String.class), anyString(), anyString(), nullable(String.class));
    }

    /** With no physician the lookup matched none, and the commit still wrote an empty batch. */
    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void shouldWriteNothing_whenThePostNamesNoProvider(String providers) throws Exception {
        request.setMethod("POST");
        request.addParameter("providers", providers);

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute("error")).isNotNull();
        assertNothingRead();
    }

    @Test
    void shouldRefuse_withoutBillingWrite() {
        when(security.hasPrivilege(any(), eq("_billing"), eq("w"), nullable(String.class))).thenReturn(false);
        request.setMethod("POST");
        request.addParameter("providers", PRACTITIONER);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_billing)");
        assertNothingRead();
    }

    /** Each archive year reopens this page for that year; they opened the simulation, which has no Activity List. */
    @Test
    void shouldOpenEachArchiveYear_onTheGenerationPage() throws IOException {
        String page = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/TeleplanSubmission.jsp"),
                StandardCharsets.UTF_8);
        assertThat(page)
                .contains("/billing/CA/BC/GenerateTeleplanFile?year=<carlos:encode value='<%= year %>' context=\"uriComponent\"/>")
                .doesNotContain("SimulateTeleplanFile")
                .contains("<form action=\"${pageContext.request.contextPath}/billing/CA/BC/GenerateTeleplanFile\" method=\"post\"");
    }
}
