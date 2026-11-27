/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link OutcomesDashboard2Action}, the route behind the shared outcomes dashboard
 * refresh ({@code POST /web/dashboard/OutcomesDashboard}, issue #5335).
 *
 * <p>The publishing helper is replaced so no outbound HTTP is attempted; the helper itself is
 * covered by {@link OutcomesDashboardUtilsUnitTest}.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
@DisplayName("OutcomesDashboard2Action")
class OutcomesDashboard2ActionUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<OutcomesDashboardUtils> publisher;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private DashboardManager dashboards;
    private ProviderManager2 providers;
    private Provider provider;
    private IndicatorTemplate library;

    @BeforeEach
    void authorizedPostWithOneKnownIndicator() {
        request = new MockHttpServletRequest("POST", "/web/dashboard/OutcomesDashboard");
        response = new MockHttpServletResponse();
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_dashboardDisplay", "r", null)).thenReturn(true);
        dashboards = createAndRegisterMock(DashboardManager.class);
        providers = createAndRegisterMock(ProviderManager2.class);
        // OutcomesDashboardUtils resolves these beans when it is first loaded.
        createAndRegisterMock(ClinicDAO.class);
        library = new IndicatorTemplate();
        library.setId(17);
        when(dashboards.getIndicatorLibrary(login)).thenReturn(List.of(library));
        provider = mock(Provider.class);
        when(providers.getProvider(login, "999998")).thenReturn(provider);

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        publisher = mockStatic(OutcomesDashboardUtils.class);
    }

    @AfterEach
    void releaseStaticMocks() {
        if (publisher != null) publisher.close();
        if (servlet != null) servlet.close();
    }

    /** The action under test, with the XML template lookup answered by name instead of by parsing. */
    private OutcomesDashboard2Action action(String... knownSharedMetricSetNames) {
        List<String> known = List.of(knownSharedMetricSetNames);
        return new OutcomesDashboard2Action() {
            @Override
            protected IndicatorTemplate findIndicatorTemplateBySharedMetricSetName(
                    LoggedInInfo x, List<IndicatorTemplate> templates, String sharedMetricSetName) {
                return known.contains(sharedMetricSetName) ? library : null;
            }
        };
    }

    private static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void postQueries(String... names) {
        String list = String.join("\",\"", names);
        request.setParameter(
                "data", encode("{\"username\":\"999998\",\"queryList\":[\"" + list + "\"]}"));
    }

    private JsonNode body() throws IOException {
        return JSON.readTree(response.getContentAsString());
    }

    private void verifyNothingPublished() throws Exception {
        publisher.verify(
                () -> OutcomesDashboardUtils.sendProviderIndicatorData(any(), any(), any()), never());
    }

    @Test
    @DisplayName("publishes each matched metric set and reports how many were delivered")
    void shouldPublishEachMatchedMetricSet_andReportTheCount() throws Exception {
        publisher
                .when(() -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, library))
                .thenReturn(true);
        postQueries("Metric A", "Metric B");

        assertThat(action("Metric A", "Metric B").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body().get("published").asInt()).isEqualTo(2);
        publisher.verify(
                () -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, library),
                org.mockito.Mockito.times(2));
    }

    @Test
    @DisplayName("ignores the legacy method query parameter and still routes through execute")
    void shouldIgnoreMethodParameter_whenCallerStillSendsIt() throws Exception {
        publisher
                .when(() -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, library))
                .thenReturn(true);
        request.setParameter("method", "refreshIndicators");
        postQueries("Metric A");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(body().get("published").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("skips a requested metric no template matches without failing the refresh")
    void shouldSkipUnknownMetric_withoutReportingAnError() throws Exception {
        postQueries("Not a shared metric");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body().get("published").asInt()).isZero();
        verifyNothingPublished();
    }

    @Test
    @DisplayName("answers 502 when the shared dashboard does not accept the metric set")
    void shouldReportBadGateway_whenDestinationRejectsTheMetricSet() throws Exception {
        publisher
                .when(() -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, library))
                .thenReturn(false);
        postQueries("Metric A");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(body().get("error").asText()).isEqualTo("publish_failed");
    }

    @Test
    @DisplayName("answers 502 without leaking the transport failure when publication throws")
    void shouldReportBadGateway_whenPublicationThrows() throws Exception {
        publisher
                .when(() -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, library))
                .thenThrow(new IOException("connect refused to https://internal.example/secret"));
        postQueries("Metric A");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).doesNotContain("internal.example");
    }

    @Test
    @DisplayName("answers 403 when the manager withholds the indicator library")
    void shouldAnswerForbidden_whenIndicatorLibraryIsWithheld() throws Exception {
        when(dashboards.getIndicatorLibrary(login)).thenReturn(null);
        postQueries("Metric A");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(body().get("error").asText()).isEqualTo("forbidden");
        verifyNothingPublished();
    }

    @ParameterizedTest(name = "{0} is refused with 405 before any work")
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("refuses every method but POST before consulting grants, reading, resolving or publishing anything")
    void shouldRefuseNonPostMethods_beforeAnyWork(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        postQueries("Metric A");

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        // The verb is refused before the privilege is even consulted, so the 405 never depends on the caller's grants.
        verifyNoInteractions(security, dashboards, providers);
        verifyNothingPublished();
    }

    @Test
    @DisplayName("throws SecurityException naming the missing right, and publishes nothing")
    void shouldThrowSecurityException_whenDashboardRightIsMissing() throws Exception {
        when(security.hasPrivilege(eq(login), anyString(), anyString(), any())).thenReturn(false);
        postQueries("Metric A");

        assertThatThrownBy(() -> action("Metric A").execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_dashboardDisplay)");

        verifyNoInteractions(dashboards, providers);
        verifyNothingPublished();
    }

    @Test
    void shouldRejectRepeatedDataParameters_beforeLookingUpMetrics() throws Exception {
        postQueries("Metric A");
        request.addParameter("data", request.getParameter("data"));

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(dashboards, providers);
        verifyNothingPublished();
    }

    static Stream<String> malformedData() {
        String valid = "{\"username\":\"999998\",\"queryList\":[\"Metric A\"]}";
        return Stream.of(
                "!" + encode(valid),
                encode(valid + " {}"),
                encode("{\"username\":\"other\",\"username\":\"999998\",\"queryList\":[]}"),
                null,
                "",
                "   ",
                "!!! not base64 !!!",
                encode("not json"),
                encode("[]"),
                encode("\"text\""),
                encode("{\"queryList\":[\"Metric A\"]}"),
                encode("{\"username\":\"999998\"}"),
                encode("{\"username\":999998,\"queryList\":[\"Metric A\"]}"),
                encode("{\"username\":\"999998\",\"queryList\":\"Metric A\"}"),
                encode("{\"username\":\"999998\",\"queryList\":[\"Metric A\",17]}"),
                encode("{\"username\":\"999998\",\"queryList\":[\"Metric A\",null]}"),
                encode("{\"username\":\"999998\",\"queryList\":[\"Metric A\",{\"x\":1}]}"));
    }

    @ParameterizedTest
    @MethodSource("malformedData")
    @DisplayName("answers 400 for a data parameter that is not a username plus queryList")
    void shouldAnswerBadRequest_whenDataIsMalformed(String data) throws Exception {
        if (data != null) request.setParameter("data", data);

        assertThat(action("Metric A").execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body().get("error").asText()).isEqualTo("invalid_request");
        verifyNoInteractions(dashboards, providers);
        verifyNothingPublished();
    }
}
