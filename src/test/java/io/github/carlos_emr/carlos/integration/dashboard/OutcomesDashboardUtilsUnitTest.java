/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.display.beans.IndicatorBean;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorQueryHandler;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link OutcomesDashboardUtils#sendProviderIndicatorData}, the helper that makes
 * the outbound HTTP POST behind the shared outcomes dashboard refresh (issue #5335).
 *
 * <p>The destination is a loopback {@link HttpServer} created per test, so the real HTTP client
 * path runs and nothing leaves the machine.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
@DisplayName("OutcomesDashboardUtils.sendProviderIndicatorData")
class OutcomesDashboardUtilsUnitTest extends CarlosUnitTestBase {

    private static final String SEND_URL = "shared_outcomes_dashboard_send_url";
    private static final String CLINIC_ID = "shared_outcomes_dashboard_clinic_id";
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer destination;
    private final List<Map<String, String>> received = new CopyOnWriteArrayList<>();
    private volatile int answerWith = 200;
    private String originalSendUrl;
    private String originalClinicId;
    private Object originalDashboardManager;
    private Object originalClinicDao;

    private LoggedInInfo login;
    private Provider provider;
    private DashboardManager dashboards;
    private IndicatorTemplate template;

    @BeforeEach
    void startDestinationAndWireTheHelper() throws Exception {
        destination = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        destination.createContext(
                "/send",
                exchange -> {
                    String body;
                    try (InputStream in = exchange.getRequestBody()) {
                        body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    received.add(form(body));
                    byte[] reply = "accepted".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(answerWith, reply.length);
                    exchange.getResponseBody().write(reply);
                    exchange.close();
                });
        destination.start();

        CarlosProperties props = CarlosProperties.getInstance();
        originalSendUrl = props.getProperty(SEND_URL);
        originalClinicId = props.getProperty(CLINIC_ID);
        props.setProperty(CLINIC_ID, "TEST_CLINIC_5335");

        dashboards = createAndRegisterMock(DashboardManager.class);
        // Parsing an indicator template loads the dashboard handlers, which bind this bean statically.
        createAndRegisterMock(DemographicExtDao.class);
        ClinicDAO clinicDao = createAndRegisterMock(ClinicDAO.class);
        Clinic clinic = new Clinic();
        clinic.setClinicName("Synthetic Clinic");
        when(clinicDao.getClinic()).thenReturn(clinic);
        // The helper binds these two beans in static fields when it is first loaded; point them at
        // this test's mocks whichever test loaded it first, and put the originals back afterwards.
        originalDashboardManager = ReflectionTestUtils.getField(OutcomesDashboardUtils.class, "dashboardManager");
        originalClinicDao = ReflectionTestUtils.getField(OutcomesDashboardUtils.class, "clinicDao");
        ReflectionTestUtils.setField(OutcomesDashboardUtils.class, "dashboardManager", dashboards);
        ReflectionTestUtils.setField(OutcomesDashboardUtils.class, "clinicDao", clinicDao);

        login = mock(LoggedInInfo.class);
        provider = new Provider();
        provider.setProviderNo("999998");
        provider.setFirstName("Synthetic");
        provider.setLastName("Provider");
        template = new IndicatorTemplate();
        template.setId(17);
        template.setName("Local indicator name");
        template.setTemplate(sharedTemplate());
    }

    @AfterEach
    void stopDestinationAndRestoreTheHelper() {
        if (destination != null) destination.stop(0);
        ReflectionTestUtils.setField(OutcomesDashboardUtils.class, "dashboardManager", originalDashboardManager);
        ReflectionTestUtils.setField(OutcomesDashboardUtils.class, "clinicDao", originalClinicDao);
        restore(SEND_URL, originalSendUrl);
        restore(CLINIC_ID, originalClinicId);
    }

    private static void restore(String key, String value) {
        if (value == null) CarlosProperties.getInstance().remove(key);
        else CarlosProperties.getInstance().setProperty(key, value);
    }

    private void configureDestination() {
        CarlosProperties.getInstance()
                .setProperty(SEND_URL, "http://127.0.0.1:" + destination.getAddress().getPort() + "/send");
    }

    /** Builds the panel text the way the production query handler does (hand-built, single-quoted keys). */
    private void producePanel(String... labelsAndValues) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        for (int i = 0; i < labelsAndValues.length; i += 2) {
            row.put(labelsAndValues[i], Integer.valueOf(labelsAndValues[i + 1]));
        }
        IndicatorBean panel = new IndicatorBean();
        panel.setOriginalJsonPlots(IndicatorQueryHandler.createOriginalGraphPlots(List.of(row)));
        when(dashboards.getIndicatorPanelForProvider(any(), eq("999998"), anyInt())).thenReturn(panel);
    }

    /** A real indicator template with the shared-dashboard name, data id and label mapping. */
    private static String sharedTemplate() {
        try (InputStream in = OutcomesDashboardUtilsUnitTest.class
                .getResourceAsStream("/indicatorXMLTemplates/influenza_test.xml")) {
            String xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return xml.replace(
                    "</indicatorTemplateXML>",
                    "<shared>true</shared>"
                            + "<sharedMetricSetName>Synthetic Metric Set</sharedMetricSetName>"
                            + "<sharedMetricDataId>Status</sharedMetricDataId>"
                            + "<sharedMappings><sharedMapping fromLabel=\"Local label\" toLabel=\"Shared label\"/>"
                            + "</sharedMappings></indicatorTemplateXML>");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> form(String body) {
        return Stream.of(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        p -> URLDecoder.decode(p[0], StandardCharsets.UTF_8),
                        p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("posts the mapped metric set to the configured destination and reports success")
    void shouldPostMappedMetricSet_whenDestinationAccepts() throws Exception {
        configureDestination();
        producePanel("Local label", "7", "Other", "3");

        boolean sent = OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template);

        assertThat(sent).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).get("version")).isEqualTo("1.1");
        JsonNode data = JSON.readTree(received.get(0).get("data"));
        assertThat(data.at("/clinic/identifier").asText()).isEqualTo("TEST_CLINIC_5335");
        assertThat(data.at("/metricOwner/username").asText()).isEqualTo("999998");
        assertThat(data.at("/metricSet/name").asText()).isEqualTo("Synthetic Metric Set");
        JsonNode points = data.at("/metricSet/metricData");
        assertThat(points).hasSize(2);
        assertThat(points.get(0).at("/name/0/identifier").asText()).isEqualTo("Status");
        assertThat(points.get(0).at("/name/0/label").asText()).isEqualTo("Shared label");
        assertThat(points.get(0).get("value").asInt()).isEqualTo(7);
        assertThat(points.get(1).at("/name/0/label").asText()).isEqualTo("Other");
    }

    @ParameterizedTest(name = "HTTP {0} from the destination is not reported as published")
    @ValueSource(ints = {400, 403, 500, 503})
    @DisplayName("reports failure when the destination answers outside 2xx")
    void shouldReturnFalse_whenDestinationAnswersWithAnError(int status) throws Exception {
        configureDestination();
        answerWith = status;
        producePanel("Local label", "7");

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template)).isFalse();
        // The HTTP client's default strategy retries a 503 once, so more than one request may arrive.
        assertThat(received).isNotEmpty();
    }

    @Test
    @DisplayName("does not follow a redirect, so a login page answering 200 is not an accepted metric set")
    void shouldReturnFalse_whenDestinationRedirects() throws Exception {
        configureDestination();
        destination.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/send");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        CarlosProperties.getInstance().setProperty(SEND_URL,
                "http://127.0.0.1:" + destination.getAddress().getPort() + "/moved");
        producePanel("Local label", "7");

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template)).isFalse();

        assertThat(received).as("the redirect target must never be requested").isEmpty();
    }

    @Test
    @DisplayName("propagates a transport failure so the caller can report it")
    void shouldThrow_whenDestinationIsUnreachable() throws Exception {
        configureDestination();
        destination.stop(0);
        producePanel("Local label", "7");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template))
                .isInstanceOf(IOException.class);
    }

    @ParameterizedTest(name = "send url {0}")
    @ValueSource(strings = {"", "   "})
    @DisplayName("sends nothing and computes nothing when no destination is configured")
    void shouldNotSend_whenDestinationIsNotConfigured(String configured) throws Exception {
        CarlosProperties.getInstance().setProperty(SEND_URL, configured);

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template)).isFalse();

        assertThat(received).isEmpty();
        verifyNoInteractions(dashboards);
    }

    @Test
    @DisplayName("sends nothing when the destination property is absent")
    void shouldNotSend_whenDestinationPropertyIsAbsent() throws Exception {
        CarlosProperties.getInstance().remove(SEND_URL);

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template)).isFalse();

        verifyNoInteractions(dashboards);
    }

    @Test
    @DisplayName("sends nothing for an unknown provider")
    void shouldNotSend_whenProviderIsUnknown() throws Exception {
        configureDestination();

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, null, template)).isFalse();

        assertThat(received).isEmpty();
        verifyNoInteractions(dashboards);
    }

    @Test
    @DisplayName("sends nothing when the indicator panel is not produced (no drilldown right)")
    void shouldNotSend_whenIndicatorPanelIsNotProduced() throws Exception {
        configureDestination();
        when(dashboards.getIndicatorPanelForProvider(any(), eq("999998"), anyInt())).thenReturn(null);

        assertThat(OutcomesDashboardUtils.sendProviderIndicatorData(login, provider, template)).isFalse();

        assertThat(received).isEmpty();
    }
}
