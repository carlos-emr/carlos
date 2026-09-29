/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanAPI;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanResponse;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanService;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanUserPassDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
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
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ManageTeleplan2Action method guard")
@Tag("unit")
@Tag("security")
class ManageTeleplan2ActionUnitTest extends CarlosUnitTestBase {

    private AutoCloseable mockitoCloseable;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private DemographicManager demographicManager;
    @Mock private LoggedInInfo loggedInInfo;
    @Mock private TeleplanAPI teleplanApi;
    @Mock private TeleplanResponse teleplanResponse;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DemographicManager.class, demographicManager);
        request = new MockHttpServletRequest("GET", "/billing/CA/BC/ManageTeleplan");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldStoreEligibilityLines_whenTeleplanResponseFileContainsDeniedDos() throws Exception {
        Path documentDir = Files.createTempDirectory("teleplan-docs");
        Path responseFile = Files.writeString(documentDir.resolve("elig-response.txt"),
                "claim <unsafe>\nELIG_ON_DOS: no\n");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        request.setMethod("POST");
        request.addParameter("demographic", "42");

        Demographic demographic = new Demographic();
        demographic.setHin("9735353315");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("02");
        when(demographicManager.getDemographic(loggedInInfo, "42")).thenReturn(demographic);
        when(teleplanResponse.getResult()).thenReturn("SUCCESS");
        when(teleplanResponse.isSuccess()).thenReturn(true);
        when(teleplanResponse.getRealFilename()).thenReturn(responseFile.getFileName().toString());
        when(teleplanResponse.getFile()).thenReturn(responseFile.toFile());
        when(teleplanResponse.toString()).thenReturn("teleplan response");
        when(teleplanApi.checkElig(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(teleplanResponse);

        try (MockedConstruction<TeleplanUserPassDAO> ignoredDao = mockConstruction(
                TeleplanUserPassDAO.class,
                (mock, context) -> when(mock.getUsernamePassword()).thenReturn(new String[]{"u", "p"}));
             MockedConstruction<TeleplanService> ignoredService = mockConstruction(
                     TeleplanService.class,
                     (mock, context) -> when(mock.getTeleplanAPI("u", "p")).thenReturn(teleplanApi))) {
            assertThat(new ManageTeleplan2Action().checkElig()).isEqualTo("checkElig");
        } finally {
            if (previousDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
        }

        assertThat(request.getAttribute("Result")).isEqualTo("Failure");
        @SuppressWarnings("unchecked")
        List<String> msgLines = (List<String>) request.getAttribute("MsgsLines");
        assertThat(msgLines).containsExactly("claim <unsafe>", "ELIG_ON_DOS: no");
        assertThat(request.getAttribute("Msgs")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "setUserName",
            "updateBillingCodes",
            "updateteleplanICDCodesList",
            "updateExplanatoryCodesList",
            "commitUpdateBillingCodes",
            "getSequenceNumber",
            "setSequenceNumber",
            "sendFile",
            "remit",
            "setPass",
            "changePass",
            "checkElig"
    })
    @DisplayName("should reject GET when Teleplan method dispatch requested")
    void shouldRejectGet_whenTeleplanMethodDispatchRequested(String method) throws Exception {
        request.addParameter("method", method);

        try (MockedConstruction<TeleplanUserPassDAO> teleplanUserPassDao =
                     mockConstruction(TeleplanUserPassDAO.class)) {
            String result = new ManageTeleplan2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(teleplanUserPassDao.constructed()).isEmpty();
            verifyNoInteractions(securityInfoManager);
        }
    }
}
