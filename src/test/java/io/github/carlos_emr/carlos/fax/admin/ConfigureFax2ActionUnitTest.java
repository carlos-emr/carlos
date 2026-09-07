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
package io.github.carlos_emr.carlos.fax.admin;

import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConfigureFax2Action}: the GET/HEAD 405 gate on the mutator
 * dispatch targets (configure/restartFaxScheduler), the {@code _admin.fax}
 * privilege gate, fax-number normalization, sentinel-preserving password handling,
 * and non-exposure of submitted credentials in the JSON response.
 *
 * <p>This is the focused conditional-mutator test required by
 * {@code MutatorActionGetRejectionContractUnitTest} for this action.</p>
 */
@DisplayName("ConfigureFax2Action unit tests")
@Tag("unit")
@Tag("fax")
class ConfigureFax2ActionUnitTest extends CarlosUnitTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecurityInfoManager securityInfoManager;
    private FaxManager faxManager;
    private FaxConfigDao faxConfigDao;
    private FaxProviderClientFactory providerClientFactory;
    private FaxProviderClient providerClient;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private Field secretKeySpecField;
    private Object originalSecretKeySpec;

    /**
     * Seeds a deterministic AES key directly into {@code EncryptionUtils.SECRET_KEY_SPEC} so
     * {@code FaxConfig.setFaxPasswd}/{@code getFaxPasswd} can round-trip without the Startup
     * listener/CarlosProperties. Same save-and-restore pattern as StartupUnitTest.
     */
    @BeforeEach
    void seedEncryptionKey() throws Exception {
        secretKeySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        secretKeySpecField.setAccessible(true);
        originalSecretKeySpec = secretKeySpecField.get(null);
        secretKeySpecField.set(null, new SecretKeySpec(new byte[16], "AES"));
    }

    @AfterEach
    void restoreEncryptionKey() throws Exception {
        secretKeySpecField.set(null, originalSecretKeySpec);
    }

    private void setUpCommonMocks() {
        securityInfoManager = mock(SecurityInfoManager.class);
        faxManager = mock(FaxManager.class);
        faxConfigDao = mock(FaxConfigDao.class);

        request = new MockHttpServletRequest();
        // Messages resolve from oscarResources for the request locale; pin English so the
        // substring assertions below are deterministic on any JVM default locale.
        request.setPreferredLocales(java.util.List.of(java.util.Locale.ENGLISH));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        // Register mocks BEFORE construction: the action resolves SecurityInfoManager and
        // FaxManager via SpringUtils.getBean in field initializers, FaxConfigDao at call time.
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FaxManager.class, faxManager);
        registerMock(FaxConfigDao.class, faxConfigDao);

        // testConnection() resolves the provider client through the factory at call time.
        providerClientFactory = mock(FaxProviderClientFactory.class);
        providerClient = mock(FaxProviderClient.class);
        registerMock(FaxProviderClientFactory.class, providerClientFactory);
    }

    /** Stubs the factory to hand back the mocked provider client for any config. */
    private void stubProviderClient() throws FaxProviderException {
        when(providerClientFactory.getClient(any(FaxConfig.class))).thenReturn(providerClient);
    }

    /** Sets the form fields testConnection() reads (same names the Configure Fax form posts). */
    private void setTestConnectionParams(String id, String faxUser, String faxPassword) {
        request.setParameter("method", "testConnection");
        request.setParameter("id", id);
        request.setParameter("faxUser", faxUser);
        request.setParameter("faxPassword", faxPassword);
        request.setParameter("providerType", "SRFAX");
    }

    private void grantConfigureWrite(boolean granted) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(granted);
    }

    /** Sets the full SRFax account-row parameter arrays configure() reads via getParameterValues. */
    private void setSrfaxAccountRowParams(String id, String faxNumber, String faxPassword) {
        request.setParameter("method", "configure");
        request.setParameter("id", id);
        request.setParameter("faxUser", "123456");
        request.setParameter("faxPassword", faxPassword);
        request.setParameter("inboxQueue", "1");
        request.setParameter("activeState", "true");
        request.setParameter("faxNumber", faxNumber);
        request.setParameter("senderEmail", "fax-admin@example.com");
        request.setParameter("accountName", "Main line");
        request.setParameter("downloadState", "true");
        request.setParameter("providerType", "SRFAX");
    }

    @Test
    @DisplayName("should send 405 on GET with method configure before any side effect")
    void shouldSend405_onGetConfigure() {
        setUpCommonMocks();
        request.setMethod("GET");
        setSrfaxAccountRowParams("1", "4165550100", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getErrorMessage()).isEqualTo("Method not allowed");
            // The verb gate must fire before the config rows are touched.
            verifyNoInteractions(faxConfigDao);
        }
    }

    @Test
    @DisplayName("should send 405 on GET with method restartFaxScheduler before any side effect")
    void shouldSend405_onGetRestartFaxScheduler() {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", "restartFaxScheduler");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verify(faxManager, never()).restartFaxScheduler(any());
        }
    }

    @Test
    @DisplayName("should return scheduler status on POST with method getFaxSchedularStatus")
    void shouldReturnSchedulerStatus_onPostStatusPoll() throws Exception {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax.restart"), eq("r"), isNull()))
                .thenReturn(true);
        ObjectNode status = OBJECT_MAPPER.createObjectNode();
        status.put("schedulerRunning", true);
        when(faxManager.getFaxSchedularStatus(any(LoggedInInfo.class))).thenReturn(status);

        request.setMethod("POST");
        request.setParameter("method", "getFaxSchedularStatus");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            verify(faxManager).getFaxSchedularStatus(any(LoggedInInfo.class));
            assertThat(response.getContentAsString()).contains("schedulerRunning");
        }
    }

    @Test
    @DisplayName("should return scheduler status on GET because the status poll stays verb-open")
    void shouldReturnSchedulerStatus_onGetStatusPoll() throws Exception {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax.restart"), eq("r"), isNull()))
                .thenReturn(true);
        ObjectNode status = OBJECT_MAPPER.createObjectNode();
        status.put("schedulerRunning", false);
        when(faxManager.getFaxSchedularStatus(any(LoggedInInfo.class))).thenReturn(status);

        request.setMethod("GET");
        request.setParameter("method", "getFaxSchedularStatus");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            verify(faxManager).getFaxSchedularStatus(any(LoggedInInfo.class));
            assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getContentAsString()).contains("schedulerRunning");
        }
    }

    @Test
    @DisplayName("should throw SecurityException when the admin fax write privilege is missing on configure")
    void shouldThrowSecurityException_whenConfigureWritePrivilegeMissing() {
        setUpCommonMocks();
        grantConfigureWrite(false);
        request.setMethod("POST");
        setSrfaxAccountRowParams("1", "4165550100", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            ConfigureFax2Action action = new ConfigureFax2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_admin.fax)");
            verifyNoInteractions(faxConfigDao);
        }
    }

    @Test
    @DisplayName("should return the digits unchanged for a plain ten digit number")
    void shouldReturnDigitsUnchanged_forPlainTenDigitNumber() {
        assertThat(ConfigureFax2Action.normalizeFaxNumber("4165550100")).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should strip punctuation and a leading country code from an eleven digit number")
    void shouldStripCountryCodeAndPunctuation_fromElevenDigitNumber() {
        assertThat(ConfigureFax2Action.normalizeFaxNumber("1 (416) 555-0100")).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should strip dashes from a formatted ten digit number")
    void shouldStripDashes_fromFormattedTenDigitNumber() {
        assertThat(ConfigureFax2Action.normalizeFaxNumber("416-555-0100")).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a nine digit number")
    void shouldThrowIllegalArgumentException_forNineDigitNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber("416555010"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a twelve digit number")
    void shouldThrowIllegalArgumentException_forTwelveDigitNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber("124165550100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a null fax number")
    void shouldThrowIllegalArgumentException_forNullFaxNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should return the row validation error without persisting when a fax number is invalid")
    void shouldReturnRowValidationError_whenFaxNumberInvalid() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>());

        request.setMethod("POST");
        // New SRFax row ("0" id) whose fax number cannot normalize to 10 digits.
        setSrfaxAccountRowParams("0", "12345", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            String body = response.getContentAsString();
            assertThat(body)
                    .contains("\"success\":false")
                    .contains("Fax number must be a 10-digit North American number.");
            // The IllegalArgumentException aborts the save loop (and the wipe-path fallback
            // save further down) before any row is persisted.
            verify(faxConfigDao, never()).saveEntity(any());
        }
    }

    @Test
    @DisplayName("should preserve the stored fax password when the mask sentinel is submitted")
    void shouldPreserveStoredFaxPassword_whenMaskSentinelSubmitted() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);

        FaxConfig stored = new FaxConfig();
        stored.setId(1);
        stored.setProviderType(FaxConfig.ProviderType.SRFAX);
        stored.setFaxUser("srfax-account-1");
        stored.setFaxNumber("4165550100");
        stored.setActive(true);
        stored.setFaxPasswd("stored-secret-value");

        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>(List.of(stored)));
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setMethod("POST");
        setSrfaxAccountRowParams("1", "416-555-0100", ConfigureFax2Action.PASSWORD_MASK_SENTINEL);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> savedCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(faxConfigDao).saveEntity(savedCaptor.capture());
            // The sentinel round-trip must leave the stored (encrypted-at-rest) credential
            // intact: getFaxPasswd() decrypts back to the originally stored plain text.
            assertThat(savedCaptor.getValue().getFaxPasswd()).isEqualTo("stored-secret-value");
            assertThat(savedCaptor.getValue().getFaxNumber()).isEqualTo("4165550100");
            assertThat(response.getContentAsString()).contains("\"success\":true");
        }
    }

    @Test
    @DisplayName("should never echo a submitted fax password in the configure success response")
    void shouldNotEchoSubmittedPassword_inConfigureSuccessResponse() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);

        FaxConfig stored = new FaxConfig();
        stored.setId(1);
        stored.setProviderType(FaxConfig.ProviderType.SRFAX);
        stored.setFaxUser("srfax-account-1");
        stored.setFaxNumber("4165550100");
        stored.setActive(true);

        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>(List.of(stored)));
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setMethod("POST");
        setSrfaxAccountRowParams("1", "4165550100", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> savedCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(faxConfigDao).saveEntity(savedCaptor.capture());
            // The new credential is applied (encrypted at rest, decrypts back)...
            assertThat(savedCaptor.getValue().getFaxPasswd()).isEqualTo("test-secret-pw");

            String body = response.getContentAsString();
            // ...and the success response never carries the submitted credential.
            assertThat(body).contains("\"success\":true");
            assertThat(body).doesNotContain("test-secret-pw");
        }
    }

    @Test
    @DisplayName("should default the provider type to SRFAX when none is submitted")
    void shouldDefaultProviderType_toSrfaxWhenNoneSubmitted() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);

        // A stored MIDDLEWARE row saved through the (SRFax-only) admin UI: the form posts no
        // usable MIDDLEWARE option any more, so the resolve fallback decides the stored value.
        FaxConfig stored = new FaxConfig();
        stored.setId(1);
        stored.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
        stored.setFaxUser("srfax-account-1");
        stored.setFaxNumber("4165550100");
        stored.setActive(true);

        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>(List.of(stored)));
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setMethod("POST");
        setSrfaxAccountRowParams("1", "4165550100", "test-secret-pw");
        request.removeParameter("providerType");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> savedCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(faxConfigDao).saveEntity(savedCaptor.capture());
            // SRFAX is the documented default: a MIDDLEWARE row re-saved through the UI migrates.
            assertThat(savedCaptor.getValue().getProviderType()).isEqualTo(FaxConfig.ProviderType.SRFAX);
            assertThat(response.getContentAsString()).contains("\"success\":true");
        }
    }

    @Test
    @DisplayName("should send 405 on GET with method testConnection before contacting the provider")
    void shouldSend405_onGetTestConnection() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("GET");
        setTestConnectionParams("1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            // Submitted credentials must never travel to the provider on a GET.
            verifyNoInteractions(providerClientFactory, providerClient);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when the admin fax write privilege is missing on testConnection")
    void shouldThrowSecurityException_whenTestConnectionWritePrivilegeMissing() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(false);
        stubProviderClient();
        request.setMethod("POST");
        setTestConnectionParams("1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            ConfigureFax2Action action = new ConfigureFax2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_admin.fax)");
            verifyNoInteractions(providerClientFactory, providerClient);
        }
    }

    @Test
    @DisplayName("should return a success JSON body when the provider verifies the connection")
    void shouldReturnSuccessJson_whenProviderVerifiesConnection() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("POST");
        // -1 is the form's "no stored row yet" id: a brand-new account being tested before save.
        setTestConnectionParams("-1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> probeCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(providerClient).verifyConnection(probeCaptor.capture());
            // The probe carries the submitted values and the SRFax provider type...
            assertThat(probeCaptor.getValue().getFaxUser()).isEqualTo("123456");
            assertThat(probeCaptor.getValue().getFaxPasswd()).isEqualTo("test-secret-pw");
            assertThat(probeCaptor.getValue().getProviderType()).isEqualTo(FaxConfig.ProviderType.SRFAX);
            // ...nothing is persisted, and the JSON body reports success.
            verify(faxConfigDao, never()).saveEntity(any());
            assertThat(response.getContentAsString())
                    .contains("\"success\":true")
                    .contains("Connection successful");
            assertThat(result).isEqualTo(ActionSupport.NONE);
        }
    }

    @Test
    @DisplayName("should return the provider failure message when verification fails")
    void shouldReturnProviderMessage_whenVerificationFails() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        org.mockito.Mockito.doThrow(new FaxProviderException(
                "SRFax connection test failed: Invalid Access Code / Password"))
                .when(providerClient).verifyConnection(any(FaxConfig.class));
        request.setMethod("POST");
        setTestConnectionParams("-1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("Connection failed")
                    .contains("Invalid Access Code / Password");
        }
    }

    @Test
    @DisplayName("should use the stored password when the mask sentinel is submitted for testConnection")
    void shouldUseStoredPassword_whenMaskSentinelSubmittedForTestConnection() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();

        FaxConfig stored = new FaxConfig();
        stored.setId(1);
        stored.setProviderType(FaxConfig.ProviderType.SRFAX);
        stored.setFaxUser("123456");
        stored.setFaxPasswd("stored-secret-value");
        when(faxConfigDao.find(1)).thenReturn(stored);

        request.setMethod("POST");
        setTestConnectionParams("1", "123456", ConfigureFax2Action.PASSWORD_MASK_SENTINEL);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> probeCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(providerClient).verifyConnection(probeCaptor.capture());
            // The sentinel never reaches the provider; the stored credential is tested instead.
            assertThat(probeCaptor.getValue().getFaxPasswd()).isEqualTo("stored-secret-value");
            assertThat(response.getContentAsString()).contains("\"success\":true");
        }
    }

    @Test
    @DisplayName("should ask for the password when the mask sentinel is submitted with no stored config")
    void shouldRejectTest_whenSentinelSubmittedWithoutStoredConfig() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("POST");
        setTestConnectionParams("-1", "123456", ConfigureFax2Action.PASSWORD_MASK_SENTINEL);

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            verifyNoInteractions(providerClient);
            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("Enter the SRFax password");
        }
    }

    @Test
    @DisplayName("should never echo the submitted credentials in the testConnection response")
    void shouldNotEchoCredentials_inTestConnectionResponse() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        org.mockito.Mockito.doThrow(new FaxProviderException("SRFax API communication failure"))
                .when(providerClient).verifyConnection(any(FaxConfig.class));
        request.setMethod("POST");
        setTestConnectionParams("-1", "987654", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            String body = response.getContentAsString();
            assertThat(body).contains("\"success\":false");
            assertThat(body).doesNotContain("test-secret-pw");
            assertThat(body).doesNotContain("987654");
        }
    }

    @Test
    @DisplayName("should send 405 with Allow: POST on PUT with method testConnection before contacting the provider")
    void shouldSend405_onPutTestConnection() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("PUT");
        setTestConnectionParams("1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            // The gate is POST-only, not merely "not GET": a PUT/PATCH/DELETE body must not
            // carry credentials to the provider either.
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(providerClientFactory, providerClient);
        }
    }

    @Test
    @DisplayName("should reject a non-numeric account number on testConnection without contacting the provider")
    void shouldRejectTestConnection_whenAccountNumberNotNumeric() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("POST");
        // The classic mistake: the SRFax login email typed into the account-number field.
        setTestConnectionParams("-1", "someone@example.com", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            // The digits check runs before the client is resolved, so neither the factory nor
            // the client may be touched.
            verifyNoInteractions(providerClientFactory, providerClient);
            String body = response.getContentAsString();
            assertThat(body)
                    .contains("\"success\":false")
                    .contains("digits only")
                    .contains("not your login email");
            assertThat(body).doesNotContain("someone@example.com");
        }
    }

    @Test
    @DisplayName("should return a validation error without persisting when the account number is not numeric")
    void shouldReturnRowValidationError_whenAccountNumberNotNumeric() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>());

        request.setMethod("POST");
        setSrfaxAccountRowParams("0", "4165550100", "test-secret-pw");
        request.setParameter("faxUser", "someone@example.com");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("digits only");
            verify(faxConfigDao, never()).saveEntity(any());
        }
    }

    @Test
    @DisplayName("should persist the trimmed account number when the submitted value has surrounding whitespace")
    void shouldPersistTrimmedAccountNumber_whenSubmittedWithWhitespace() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(new ArrayList<>());
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setMethod("POST");
        setSrfaxAccountRowParams("0", "4165550100", "test-secret-pw");
        request.setParameter("faxUser", "  123456 ");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            ArgumentCaptor<FaxConfig> savedCaptor = ArgumentCaptor.forClass(FaxConfig.class);
            verify(faxConfigDao).saveEntity(savedCaptor.capture());
            // The stored access_id is exactly the validated digits, matching what the probe sends.
            assertThat(savedCaptor.getValue().getFaxUser()).isEqualTo("123456");
        }
    }

    @Test
    @DisplayName("should accept a numeric account number for the digits-only rule")
    void shouldAcceptDigits_forSrfaxAccountNumberRule() {
        assertThat(ConfigureFax2Action.isSrfaxAccountNumber(" 440000 ")).isTrue();
        assertThat(ConfigureFax2Action.isSrfaxAccountNumber("someone@example.com")).isFalse();
        assertThat(ConfigureFax2Action.isSrfaxAccountNumber("")).isFalse();
        assertThat(ConfigureFax2Action.isSrfaxAccountNumber(null)).isFalse();
    }

    @ParameterizedTest(name = "{0} with method testConnection is refused with 405")
    @ValueSource(strings = {"HEAD", "PATCH", "DELETE"})
    @DisplayName("should send 405 with Allow: POST on every non-POST verb for testConnection")
    void shouldSend405_onNonPostVerbsForTestConnection(String verb) throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod(verb);
        setTestConnectionParams("1", "123456", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(providerClientFactory, providerClient);
        }
    }

    @Test
    @DisplayName("should report the provider as unsupported when testConnection targets a MIDDLEWARE row")
    void shouldReturnUnsupportedProviderMessage_whenTestConnectionProviderIsMiddleware() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        // A legacy relay client without a verifyConnection override: exercise the interface
        // default rather than a stub, so the contract for non-SRFax providers is what is pinned.
        FaxProviderClient middlewareClient = mock(FaxProviderClient.class);
        when(middlewareClient.getProviderType()).thenReturn(FaxConfig.ProviderType.MIDDLEWARE);
        doCallRealMethod().when(middlewareClient).verifyConnection(any(FaxConfig.class));
        when(providerClientFactory.getClient(any(FaxConfig.class))).thenReturn(middlewareClient);
        request.setMethod("POST");
        // Middleware rows carry a relay username, so the SRFax digits rule must not apply.
        setTestConnectionParams("1", "relay-user", "test-secret-pw");
        request.setParameter("providerType", "MIDDLEWARE");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            String body = response.getContentAsString();
            assertThat(body)
                    .contains("\"success\":false")
                    .contains("Connection test is not supported for provider MIDDLEWARE");
            assertThat(body).doesNotContain("test-secret-pw");
        }
    }

    @Test
    @DisplayName("should return a sanitized validation failure when testConnection posts an unknown provider type")
    void shouldReturnValidationFailure_whenTestConnectionProviderTypeInvalid() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setMethod("POST");
        // -1 is the form's "no stored row" marker, so configId is null: the message must be
        // built without a NullPointerException and without a literal "null" id.
        setTestConnectionParams("-1", "123456", "test-secret-pw");
        request.setParameter("providerType", "BOGUS<script>");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            // resolveProviderType throws IllegalArgumentException: it must be caught and
            // rendered as a JSON failure (not escape as an HTML error page), with the raw
            // input sanitized, and before the client is ever resolved.
            String body = response.getContentAsString();
            assertThat(body)
                    .contains("\"success\":false")
                    .contains("Invalid provider type 'BOGUSscript'. Valid values are");
            assertThat(body).doesNotContain("<script>").doesNotContain("null");
            verifyNoInteractions(providerClientFactory, providerClient);
        }
    }

    @Test
    @DisplayName("should refuse to re-save a legacy row whose stored account number is not numeric")
    void shouldReturnRowValidationError_whenStoredLegacyAccountNumberNotNumeric() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        // A row saved before the digits rule existed, holding the login email as access_id.
        FaxConfig legacy = new FaxConfig();
        legacy.setId(1);
        legacy.setProviderType(FaxConfig.ProviderType.SRFAX);
        legacy.setFaxUser("someone@example.com");
        legacy.setFaxPasswd("stored-secret");
        legacy.setFaxNumber("4165550100");
        List<FaxConfig> stored = new ArrayList<>();
        stored.add(legacy);
        when(faxConfigDao.findAll(isNull(), isNull())).thenReturn(stored);

        request.setMethod("POST");
        // Re-submitting the row unchanged, password left as the mask sentinel.
        setSrfaxAccountRowParams("1", "4165550100", "**********");
        request.setParameter("faxUser", "someone@example.com");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            // The rule applies to legacy rows too: the admin is told to fix the value rather
            // than silently re-persisting an access_id SRFax will keep rejecting.
            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("digits only");
            verify(faxConfigDao, never()).saveEntity(any());
        }
    }

    @Test
    @DisplayName("should answer in the page language when the first preferred locale has no bundle")
    void shouldResolveMessageInPageLanguage_whenFirstPreferredLocaleHasNoBundle() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        // The fmt taglib renders this page in French for "de-DE,fr" (no German bundle); the
        // JSON response must pick the same bundle rather than the JVM default locale.
        request.setPreferredLocales(List.of(Locale.GERMANY, Locale.FRENCH));
        request.setMethod("POST");
        setTestConnectionParams("-1", "", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("Saisissez le num")
                    .doesNotContain("Enter the SRFax account number");
        }
    }

    @Test
    @DisplayName("should fall back to English when no preferred locale has a bundle")
    void shouldFallBackToEnglish_whenNoPreferredLocaleHasBundle() throws Exception {
        setUpCommonMocks();
        grantConfigureWrite(true);
        stubProviderClient();
        request.setPreferredLocales(List.of(Locale.GERMANY));
        request.setMethod("POST");
        setTestConnectionParams("-1", "", "test-secret-pw");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ConfigureFax2Action().execute();

            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("Enter the SRFax account number to test the connection.");
        }
    }

}
