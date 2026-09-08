/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryArtifactProvider;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryRequest;
import io.github.carlos_emr.carlos.clinical.summary.SyntheticClinicalSummaryProvider;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryArtifact;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationException;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryGenerationService;
import io.github.carlos_emr.carlos.clinical.summary.SyntheticSummaryScope;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiClinicalSummaryPrototypeActionUnitTest extends CarlosUnitTestBase {
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final CarlosProperties properties = mock(CarlosProperties.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<CarlosProperties> settings;
    private AiClinicalSummaryPrototype2Action action;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        when(request.getMethod()).thenReturn("GET");
        when(properties.getProperty(AiClinicalSummaryPrototype2Action.ENABLED_PROPERTY, "false")).thenReturn("true");
        action = new AiClinicalSummaryPrototype2Action();
    }

    @AfterEach
    void tearDown() {
        settings.close();
        sessions.close();
        servlet.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void allowsReadWithExactPrivilege(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(security).hasPrivilege(user, "_eChart", "r", null);
        verify(request).setAttribute(eq("summaryArtifact"), anyMap());
        verify(request).setAttribute("summaryRenderable", true);
        verify(response).setHeader("Cache-Control", "no-store");
        verify(request, never()).getParameter(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH", "OPTIONS"})
    void refusesOtherMethods(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(405);
        verifyNoInteractions(security);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void deniesMissingPrivilege() {
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void deniesMissingLogin() {
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(security);
    }

    @Test
    void disabledByDefault() throws Exception {
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(properties.getProperty(AiClinicalSummaryPrototype2Action.ENABLED_PROPERTY, "false")).thenReturn("false");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(404);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.0", " 1", "2147483648", "abc", "1 OR 1=1"})
    void rejectsMalformedDemographic(String input) throws Exception {
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(request.getParameterValues("demographicNo")).thenReturn(new String[]{input});
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(400);
        verify(request, never()).setAttribute(anyString(), any());
    }

    @Test
    void rejectsAmbiguousDemographic() throws Exception {
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(request.getParameterValues("demographicNo")).thenReturn(new String[]{"1", "2"});
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(400);
    }

    @Test
    void routesExplicitDemographicToChartProvider() throws Exception {
        ClinicalSummaryArtifactProvider chart = mock(ClinicalSummaryArtifactProvider.class);
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(request.getParameterValues("demographicNo")).thenReturn(new String[]{"42"});
        when(chart.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(
                new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic()));
        assertThat(new AiClinicalSummaryPrototype2Action(chart).execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(chart).load(user, ClinicalSummaryRequest.chart(42));
        verify(request).setAttribute("summaryDemographicNo", 42);
    }

    @Test
    void missingPatientDoesNotFallBackToFixture() throws Exception {
        ClinicalSummaryArtifactProvider chart = mock(ClinicalSummaryArtifactProvider.class);
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(request.getParameterValues("demographicNo")).thenReturn(new String[]{"42"});
        when(chart.load(user, ClinicalSummaryRequest.chart(42))).thenThrow(new java.util.NoSuchElementException());
        assertThat(new AiClinicalSummaryPrototype2Action(chart).execute()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(404);
        verify(request, never()).setAttribute(anyString(), any());
    }

    private void enableGeneration() {
        when(request.getMethod()).thenReturn("POST");
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        when(properties.getProperty(ClinicalSummaryGenerationService.ENABLED_PROPERTY, "false")).thenReturn("true");
        when(request.getParameterValues("demographicNo")).thenReturn(new String[]{"42"});
    }

    @Test
    void discardsDraftWhenSourcesChangeDuringGeneration() throws Exception {
        enableGeneration();
        ClinicalSummaryArtifactProvider charts = mock(ClinicalSummaryArtifactProvider.class);
        ClinicalSummaryGenerationService generator = mock(ClinicalSummaryGenerationService.class);
        ClinicalSummaryArtifact original = new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic());
        com.fasterxml.jackson.databind.node.ObjectNode changed = new com.fasterxml.jackson.databind.ObjectMapper()
                .valueToTree(original.getView());
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed.get("sources").get(0)).put("text", "Changed note");
        ClinicalSummaryArtifact fresh = new ClinicalSummaryArtifact(changed);
        when(charts.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(original, fresh);
        when(generator.generate(original)).thenReturn(original);
        try (MockedStatic<SyntheticSummaryScope> scope = mockStatic(SyntheticSummaryScope.class)) {
            scope.when(() -> SyntheticSummaryScope.isEligible(original)).thenReturn(true);
            scope.when(() -> SyntheticSummaryScope.isEligible(fresh)).thenReturn(true);
            assertThat(new AiClinicalSummaryPrototype2Action(charts, generator).generate()).isEqualTo(ActionSupport.SUCCESS);
        }
        verify(request, never()).setAttribute(eq("summaryGenerated"), any());
        verify(request).setAttribute("summaryArtifact", fresh.getView());
        verify(request).setAttribute(eq("summaryGenerationError"), contains("chart changed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void generationRequiresPost(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).setHeader("Allow", "POST");
        verify(response).sendError(405);
        verifyNoInteractions(security);
    }

    @Test
    void generationIsSeparatelyDisabledByDefault() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(security.hasPrivilege(user, "_eChart", "r", null)).thenReturn(true);
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(404);
        verify(request, never()).getParameterValues(anyString());
    }

    @Test
    void generationRequiresExplicitPatient() throws Exception {
        enableGeneration();
        when(request.getParameterValues("demographicNo")).thenReturn(null);
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(400);
    }

    @Test
    void generationRejectsUnverifiedChartsWithoutModelCalls() throws Exception {
        enableGeneration();
        ClinicalSummaryArtifactProvider charts = mock(ClinicalSummaryArtifactProvider.class);
        ClinicalSummaryGenerationService generator = mock(ClinicalSummaryGenerationService.class);
        when(charts.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(
                new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic()));
        assertThat(new AiClinicalSummaryPrototype2Action(charts, generator).generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(403);
        verifyNoInteractions(generator);
    }

    @Test
    void generationRechecksChartBeforeRendering() throws Exception {
        enableGeneration();
        ClinicalSummaryArtifactProvider charts = mock(ClinicalSummaryArtifactProvider.class);
        ClinicalSummaryGenerationService generator = mock(ClinicalSummaryGenerationService.class);
        ClinicalSummaryArtifact fixture = new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic());
        when(charts.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(fixture);
        when(generator.generate(fixture)).thenReturn(fixture);
        try (MockedStatic<SyntheticSummaryScope> scope = mockStatic(SyntheticSummaryScope.class)) {
            scope.when(() -> SyntheticSummaryScope.isEligible(fixture)).thenReturn(true);
            assertThat(new AiClinicalSummaryPrototype2Action(charts, generator).generate()).isEqualTo(ActionSupport.SUCCESS);
        }
        verify(charts, times(2)).load(user, ClinicalSummaryRequest.chart(42));
        verify(request).setAttribute("summaryGenerated", true);
    }

    @Test
    void generationFailureRetainsOnlyFreshAuthorizedEvidence() throws Exception {
        enableGeneration();
        ClinicalSummaryArtifactProvider charts = mock(ClinicalSummaryArtifactProvider.class);
        ClinicalSummaryGenerationService generator = mock(ClinicalSummaryGenerationService.class);
        ClinicalSummaryArtifact fixture = new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic());
        when(charts.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(fixture);
        when(generator.generate(fixture)).thenThrow(new ClinicalSummaryGenerationException("Local model unavailable"));
        try (MockedStatic<SyntheticSummaryScope> scope = mockStatic(SyntheticSummaryScope.class)) {
            scope.when(() -> SyntheticSummaryScope.isEligible(fixture)).thenReturn(true);
            assertThat(new AiClinicalSummaryPrototype2Action(charts, generator).generate()).isEqualTo(ActionSupport.SUCCESS);
        }
        verify(charts, times(2)).load(user, ClinicalSummaryRequest.chart(42));
        verify(request).setAttribute("summaryGenerationError", "Local model unavailable");
        verify(request, never()).setAttribute("summaryGenerated", true);
        verify(request).setAttribute("summaryArtifact", fixture.getView());
    }

    @Test
    void revokedPatientAccessDuringGenerationPreventsRendering() throws Exception {
        enableGeneration();
        ClinicalSummaryArtifactProvider charts = mock(ClinicalSummaryArtifactProvider.class);
        ClinicalSummaryGenerationService generator = mock(ClinicalSummaryGenerationService.class);
        ClinicalSummaryArtifact fixture = new SyntheticClinicalSummaryProvider().load(user, ClinicalSummaryRequest.synthetic());
        when(charts.load(user, ClinicalSummaryRequest.chart(42))).thenReturn(fixture).thenThrow(new SecurityException("Access revoked"));
        when(generator.generate(fixture)).thenReturn(fixture);
        try (MockedStatic<SyntheticSummaryScope> scope = mockStatic(SyntheticSummaryScope.class)) {
            scope.when(() -> SyntheticSummaryScope.isEligible(fixture)).thenReturn(true);
            assertThatThrownBy(() -> new AiClinicalSummaryPrototype2Action(charts, generator).generate())
                    .isInstanceOf(SecurityException.class);
        }
        verify(request, never()).setAttribute(eq("summaryArtifact"), any());
        verify(request, never()).setAttribute("summaryGenerated", true);
    }
}
