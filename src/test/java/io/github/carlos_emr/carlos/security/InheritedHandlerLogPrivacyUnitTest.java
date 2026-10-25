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
package io.github.carlos_emr.carlos.security;

import io.github.carlos_emr.carlos.casemgmt.dao.ClientImageDAO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicSearch2Action;
import io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.ConsultationClinicalData2Action;
import io.github.carlos_emr.carlos.integration.dashboard.OutcomesDashboard2Action;
import io.github.carlos_emr.carlos.lab.pageUtil.ForwardingRules2Action;
import io.github.carlos_emr.carlos.login.LoginResourceAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Exercises real request handlers and captured Log4j events with hostile request/stored values.
 * Refused inputs retain their original responses; successful routing updates still reach their DAO.
 *
 * @since 2026-10-25
 */
@Tag("unit")
class InheritedHandlerLogPrivacyUnitTest extends CarlosUnitTestBase {
    private static final String PRIVATE = "FAKE_PRIVATE_PATIENT";
    private static final String FORGED = "FORGED_AUDIT";
    private static final String ATTACK = PRIVATE + "\r\n" + FORGED + "\u2028";
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private PrescriptionManager prescriptions;
    private AllergyManager allergies;
    private CaseManagementManager notes;
    private DashboardManager dashboards;
    private ProviderManager2 providers;
    private IncomingLabRulesDao rules;
    @TempDir Path images;

    @BeforeEach
    void initializeAuthorizedRequest() {
        request = new MockHttpServletRequest("POST", "/owned-handler-test");
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), anyString(), anyString(), any())).thenReturn(true);
        prescriptions = createAndRegisterMock(PrescriptionManager.class);
        allergies = createAndRegisterMock(AllergyManager.class);
        notes = createAndRegisterMock(CaseManagementManager.class);
        dashboards = createAndRegisterMock(DashboardManager.class);
        providers = createAndRegisterMock(ProviderManager2.class);
        rules = createAndRegisterMock(IncomingLabRulesDao.class);
        createAndRegisterMock(ClientImageDAO.class);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void releaseRequestContext() { if (servlet != null) servlet.close(); }

    private static void assertPrivateValuesAbsent(LogCapture capture) {
        assertThat(capture.events()).isNotEmpty();
        PatternLayout layout = PatternLayout.newBuilder().withPattern("%m%n%throwable").build();
        String rendered = capture.events().stream().map(layout::toSerializable).reduce("", String::concat);
        assertThat(rendered).doesNotContain(PRIVATE, FORGED);
        assertThat(capture.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
    }

    @Test
    void shouldKeepGeneralSearchRouting_withoutLoggingAnUnrecognisedRequestMode() {
        request.setParameter("displaymode", ATTACK);
        request.setParameter("keyword", "");
        try (var capture = LogCapture.forLogger(DemographicSearch2Action.class)) {
            assertThat(new DemographicSearch2Action().execute()).isEqualTo("success");
            assertThat(request.getAttribute("showRecentPatients")).isEqualTo(true);
            verify(security).hasPrivilege(login, "_demographic", "r", null);
            assertPrivateValuesAbsent(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetchLongTermMedications", "fetchAllergies", "fetchIssueNote"})
    void shouldRefuseMalformedClinicalSelection_beforeManagerReadsAndWithoutLoggingIt(String method) {
        request.setParameter("method", method);
        request.setParameter("demographicNo", method.equals("fetchIssueNote") ? "101" : ATTACK);
        request.setParameter("issueType", ATTACK);
        try (var capture = LogCapture.forLogger(ConsultationClinicalData2Action.class)) {
            assertThat(new ConsultationClinicalData2Action().execute()).isNull();
            verifyNoInteractions(prescriptions, allergies, notes);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertPrivateValuesAbsent(capture);
        }
    }

    @Test
    void shouldStillServeValidLongTermMedicationRequests() {
        request.setParameter("method", "fetchLongTermMedications");
        request.setParameter("demographicNo", "101");
        when(prescriptions.getLongTermDrugs(login, 101)).thenReturn(List.of());
        assertThat(new ConsultationClinicalData2Action().execute()).isNull();
        verify(prescriptions).getLongTermDrugs(login, 101);
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    void shouldFinishAnUnknownIndicatorRequest_withoutEchoingDecodedQueryData() throws Exception {
        when(dashboards.getIndicatorLibrary(login)).thenReturn(List.of());
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        json.put("username", "999998");
        json.putArray("queryList").add(ATTACK);
        request.setParameter("data", Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8)));
        try (var capture = LogCapture.forLogger(OutcomesDashboard2Action.class)) {
            assertThat(new OutcomesDashboard2Action().execute()).isNull();
            verify(providers).getProvider(login, "999998");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith("application/json");
            assertPrivateValuesAbsent(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"stored-rule", "provider-names"})
    void shouldUpdateForwardingRules_withoutSerializingStoredRuleOrProviderNames(String metadata) throws Exception {
        request.setParameter("providerNo", "999998");
        request.setParameter("operation", "update");
        request.setParameter("providerNums", "999997");
        request.setParameter("status", "N");
        IncomingLabRules old = new IncomingLabRules();
        old.setId(17);
        old.setProviderNo("999998");
        old.setFrwdProviderNo("0");
        old.setStatus(metadata.equals("stored-rule") ? "\n" : "N"); // A persisted one-character status can split a log line.
        when(rules.findCurrentByProviderNoAndFrwdProvider("999998", "0")).thenReturn(List.of(old));
        Provider target = new Provider();
        target.setProviderNo("999997");
        target.setFirstName(metadata.equals("provider-names") ? PRIVATE + "\r\n" : "Fixture");
        target.setLastName(metadata.equals("provider-names") ? FORGED : "Fixture");
        when(rules.findRules("999998")).thenReturn(Collections.singletonList(new Object[]{old, target}));
        try (var capture = LogCapture.forLogger(ForwardingRules2Action.class)) {
            assertThat(new ForwardingRules2Action().execute()).isEqualTo("success");
            assertThat(old.getArchive()).isEqualTo("1");
            verify(rules).merge(old);
            verify(rules).persist(any(IncomingLabRules.class));
            assertPrivateValuesAbsent(capture);
            assertThat(capture.messages().stream().filter(message -> message.startsWith("Archived")))
                    .isNotEmpty().allSatisfy(message -> assertThat(message).doesNotContain("\r", "\n"));
        }
    }

    @Test
    void shouldRetainForwardingFailureResult_withoutLoggingRejectedProviderOrExceptionMessages() throws Exception {
        request.setParameter("providerNo", "999998");
        request.setParameter("operation", "remove");
        request.setParameter("remProviderNum", ATTACK);
        when(rules.findCurrentByProviderNo("999998")).thenThrow(new IllegalStateException(ATTACK));
        try (var capture = LogCapture.forLogger(ForwardingRules2Action.class)) {
            assertThat(new ForwardingRules2Action().execute()).isEqualTo("failure");
            verify(rules, never()).merge(any());
            verify(rules, never()).persist(any());
            assertPrivateValuesAbsent(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"blocked-filename", "invalid-encoding"})
    void shouldKeepLoginResourceRefusal_withoutLoggingTheRejectedPathOrException(String invalid) throws Exception {
        request.setMethod("GET");
        boolean badEncoding = invalid.equals("invalid-encoding");
        request.setPathInfo(badEncoding ? "/%\r\n" : "/" + ATTACK + ".jsp");
        LoginResourceAction resource = new LoginResourceAction() {
            @Override protected String getImagesDir() { return images.toString(); }
        };
        resource.init(new MockServletConfig(new MockServletContext()));
        try (var capture = LogCapture.forLogger(LoginResourceAction.class)) {
            resource.service(request, response);
            assertThat(response.getStatus()).isEqualTo(badEncoding ? 500 : 400);
            assertThat(response.getErrorMessage()).doesNotContain(PRIVATE, FORGED);
            assertPrivateValuesAbsent(capture);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"local_client", "unknown-source"})
    void shouldKeepImageFailureResponse_withoutLoggingRequestValuesOrExceptionMessages(String source) throws Exception {
        request.setMethod("GET");
        request.setParameter("source", source.equals("unknown-source") ? ATTACK : source);
        request.setParameter("clientId", ATTACK);
        request.setQueryString("source=" + ATTACK + "&clientId=" + ATTACK);
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, provider);
        try (var capture = LogCapture.forLogger(ImageRenderingServlet.class)) {
            new ImageRenderingServlet().doGet(request, response);
            assertThat(response.getStatus()).isEqualTo(source.equals("unknown-source") ? 500 : 404);
            assertPrivateValuesAbsent(capture);
        }
    }
}
