/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanAPI;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanResponse;
import io.github.carlos_emr.carlos.billings.ca.bc.Teleplan.TeleplanSequenceDAO;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
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
    void shouldDispatchEligibilityPost_whenTeleplanResponseFileContainsDeniedDos() throws Exception {
        Path documentDir = Files.createTempDirectory("teleplan-docs");
        Path responseFile = Files.writeString(documentDir.resolve("elig-response.txt"),
                "claim <unsafe>\nELIG_ON_DOS: no\n");
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        request.setMethod("POST");
        request.addParameter("method", "checkElig");
        request.addParameter("demographic", "42");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)).thenReturn(true);

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
            assertThat(new ManageTeleplan2Action().execute()).isEqualTo("checkElig");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("Allow")).isNull();
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

    /** A billing-read login posting one of the superuser writes, with BILLING_SUPERUSER set to {@code superUser}. */
    private String postAs(String providerNo, String superUser, String method) throws Exception {
        return postAs(providerNo, superUser, method, "42");
    }

    /** As {@link #postAs(String, String, String)}, with {@code num} as the posted sequence number. */
    private String postAs(String providerNo, String superUser, String method, String num) throws Exception {
        request.setMethod("POST");
        request.addParameter("method", method);
        request.addParameter("num", num);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), nullable(String.class)))
                .thenReturn(true);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(providerNo);
        String previous = CarlosProperties.getInstance().getProperty("BILLING_SUPERUSER");
        if (superUser == null) CarlosProperties.getInstance().remove("BILLING_SUPERUSER");
        else CarlosProperties.getInstance().setProperty("BILLING_SUPERUSER", superUser);
        try {
            return new ManageTeleplan2Action().execute();
        } finally {
            if (previous == null) CarlosProperties.getInstance().remove("BILLING_SUPERUSER");
            else CarlosProperties.getInstance().setProperty("BILLING_SUPERUSER", previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"setSequenceNumber", "getSequenceNumber", "setUserName", "setPass"})
    @DisplayName("should refuse the superuser writes when the login is not the billing superuser")
    void shouldRefuseSuperuserWrite_whenTheLoginIsNotTheBillingSuperuser(String method) {
        try (MockedConstruction<TeleplanSequenceDAO> sequenceDao = mockConstruction(TeleplanSequenceDAO.class);
             MockedConstruction<TeleplanUserPassDAO> userPassDao = mockConstruction(TeleplanUserPassDAO.class)) {
            assertThatThrownBy(() -> postAs("828693", "999998", method))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("BILLING_SUPERUSER");
            assertThat(sequenceDao.constructed()).isEmpty();
            assertThat(userPassDao.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("should refuse the sequence write when no billing superuser is configured")
    void shouldRefuseSequenceWrite_whenNoBillingSuperuserIsConfigured() {
        try (MockedConstruction<TeleplanSequenceDAO> sequenceDao = mockConstruction(TeleplanSequenceDAO.class)) {
            assertThatThrownBy(() -> postAs("999998", null, "setSequenceNumber")).isInstanceOf(SecurityException.class);
            assertThat(sequenceDao.constructed()).isEmpty();
        }
    }

    @Test
    @DisplayName("should store the sequence for the billing superuser")
    void shouldStoreSequence_forTheBillingSuperuser() throws Exception {
        try (MockedConstruction<TeleplanSequenceDAO> sequenceDao = mockConstruction(TeleplanSequenceDAO.class)) {
            assertThat(postAs("999998", "999998", "setSequenceNumber")).isEqualTo(ActionSupport.SUCCESS);
            assertThat(sequenceDao.constructed()).hasSize(1);
            verify(sequenceDao.constructed().get(0)).saveUpdateSequence(42);
        }
    }

    /** Posts Manually Set Sequence # with {@code number}, as the billing superuser. */
    private String postSequence(String number) throws Exception {
        return postAs("999998", "999998", "setSequenceNumber", number);
    }

    /** Issue #5066: a refused sequence number stored nothing and the page said nothing. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12a      | The sequence number must be a whole number. Nothing was saved.",
            "''       | The sequence number must be a whole number. Nothing was saved.",
            "-1       | The sequence number must be from 0 to 9999999. Nothing was saved.",
            "10000000 | The sequence number must be from 0 to 9999999. Nothing was saved."
    })
    @DisplayName("should say why and store nothing when the sequence number is refused")
    void shouldSayWhyAndStoreNothing_whenSequenceNumberIsRefused(String number, String message) throws Exception {
        try (MockedConstruction<TeleplanSequenceDAO> sequenceDao = mockConstruction(TeleplanSequenceDAO.class)) {
            assertThat(postSequence(number)).isEqualTo(ActionSupport.SUCCESS);
            assertThat(sequenceDao.constructed()).isEmpty();
        }
        assertThat(request.getAttribute("error")).isEqualTo(message);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 42, 9999999})
    @DisplayName("should store the sequence with no message when the number is in range")
    void shouldStoreSequenceWithoutMessage_whenNumberIsInRange(int number) throws Exception {
        try (MockedConstruction<TeleplanSequenceDAO> sequenceDao = mockConstruction(TeleplanSequenceDAO.class)) {
            assertThat(postSequence(String.valueOf(number))).isEqualTo(ActionSupport.SUCCESS);
            assertThat(sequenceDao.constructed()).hasSize(1);
            verify(sequenceDao.constructed().get(0)).saveUpdateSequence(number);
        }
        assertThat(request.getAttribute("error")).isNull();
    }
}
