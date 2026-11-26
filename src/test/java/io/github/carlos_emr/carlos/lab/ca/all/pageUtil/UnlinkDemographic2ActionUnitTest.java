/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import io.github.carlos_emr.carlos.lab.service.LabPatientUnlinkService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Unlink endpoint of the lab display: POST only, the global {@code _lab} update privilege, a
 * valid report number, delegation to {@link LabPatientUnlinkService} and the JSON answer the page
 * reads, including how many readings were archived (#6214).
 */
@DisplayName("UnlinkDemographic2Action")
@Tag("unit")
@Tag("web")
@Tag("security")
@Tag("lab")
class UnlinkDemographic2ActionUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private LabPatientUnlinkService unlinkService;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        unlinkService = mock(LabPatientUnlinkService.class);
        loggedInInfo = mock(LoggedInInfo.class);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("labNo", "456");
        request.setParameter("reason", "incorrect match");
        request.setRemoteAddr("127.0.0.1");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_lab"), eq("u"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private UnlinkDemographic2Action action() {
        return new UnlinkDemographic2Action(securityInfoManager, unlinkService);
    }

    private JsonNode answer() throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    @Test
    @DisplayName("should report the unlinked patient and the archived readings when the unlink succeeds")
    void shouldReportArchivedReadings_whenUnlinkSucceeds() throws Exception {
        when(unlinkService.unlinkReport(loggedInInfo, 456, "incorrect match", "127.0.0.1"))
                .thenReturn(new LabPatientUnlinkService.Result(true, 123, 3));

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        JsonNode answer = answer();
        assertThat(answer.get("success").asBoolean()).isTrue();
        assertThat(answer.get("unlinkedDemographicNo").asInt()).isEqualTo(123);
        assertThat(answer.get("labNo").asInt()).isEqualTo(456);
        assertThat(answer.get("reason").asText()).isEqualTo("incorrect match");
        assertThat(answer.get("archivedMeasurementCount").asInt()).isEqualTo(3);
        verify(unlinkService).unlinkReport(loggedInInfo, 456, "incorrect match", "127.0.0.1");
    }

    @Test
    @DisplayName("should answer success false when the report has no patient routing")
    void shouldReportNoUnlink_whenReportHasNoPatientRouting() throws Exception {
        when(unlinkService.unlinkReport(loggedInInfo, 456, "incorrect match", "127.0.0.1"))
                .thenReturn(new LabPatientUnlinkService.Result(false, 0, 0));

        action().execute();

        JsonNode answer = answer();
        assertThat(answer.get("success").asBoolean()).isFalse();
        assertThat(answer.get("archivedMeasurementCount").asInt()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should answer 405 before authorization or any unlink for a method other than POST")
    void shouldRejectMethod_otherThanPost(String method) throws Exception {
        request.setMethod(method);

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, unlinkService);
    }

    @Test
    @DisplayName("should refuse without the global _lab update privilege")
    void shouldRefuseUnlink_withoutLabUpdatePrivilege() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_lab"), eq("u"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");
        verifyNoInteractions(unlinkService);
    }

    @ParameterizedTest(name = "labNo={0}")
    @ValueSource(strings = {"", "0", "-1", "abc", "12345678901"})
    @DisplayName("should answer 400 without unlinking when the report number is not a report identifier")
    void shouldAnswerBadRequest_forInvalidReportNumber(String labNo) throws Exception {
        request.setParameter("labNo", labNo);

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(unlinkService);
    }

    @Test
    @DisplayName("should let the service's patient refusal propagate")
    void shouldPropagateRefusal_whenChartIsClosedToCaller() {
        when(unlinkService.unlinkReport(loggedInInfo, 456, "incorrect match", "127.0.0.1"))
                .thenThrow(new SecurityException("access to patient record is restricted"));

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("access to patient record is restricted");
    }

    @Test
    @DisplayName("should answer 500 and log no exception message when the unlink cannot be saved")
    void shouldAnswerServerError_whenUnlinkCannotBeSaved() throws Exception {
        when(unlinkService.unlinkReport(loggedInInfo, 456, "incorrect match", "127.0.0.1"))
                .thenThrow(new IllegalStateException("synthetic reading 5.6 could not be archived"));

        try (LogCapture capture = LogCapture.forLogger(UnlinkDemographic2Action.class)) {
            assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(capture.messages()).singleElement().satisfies(message -> {
                assertThat(message).startsWith("Lab 456 could not be unlinked:");
                assertThat(message).contains(IllegalStateException.class.getName());
                assertThat(message).doesNotContain("synthetic reading 5.6");
            });
        }
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(response.getContentAsString()).doesNotContain("\"success\":true");
    }
}
