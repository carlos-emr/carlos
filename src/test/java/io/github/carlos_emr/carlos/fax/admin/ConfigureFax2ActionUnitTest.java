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
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        // Register mocks BEFORE construction: the action resolves SecurityInfoManager and
        // FaxManager via SpringUtils.getBean in field initializers, FaxConfigDao at call time.
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FaxManager.class, faxManager);
        registerMock(FaxConfigDao.class, faxConfigDao);
    }

    private void grantConfigureWrite(boolean granted) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(granted);
    }

    /** Sets the full SRFax account-row parameter arrays configure() reads via getParameterValues. */
    private void setSrfaxAccountRowParams(String id, String faxNumber, String faxPassword) {
        request.setParameter("method", "configure");
        request.setParameter("id", id);
        request.setParameter("faxUser", "srfax-account-1");
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
        assertThat(ConfigureFax2Action.normalizeFaxNumber("4165550100", 1)).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should strip punctuation and a leading country code from an eleven digit number")
    void shouldStripCountryCodeAndPunctuation_fromElevenDigitNumber() {
        assertThat(ConfigureFax2Action.normalizeFaxNumber("1 (416) 555-0100", 1)).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should strip dashes from a formatted ten digit number")
    void shouldStripDashes_fromFormattedTenDigitNumber() {
        assertThat(ConfigureFax2Action.normalizeFaxNumber("416-555-0100", 1)).isEqualTo("4165550100");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a nine digit number")
    void shouldThrowIllegalArgumentException_forNineDigitNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber("416555010", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a twelve digit number")
    void shouldThrowIllegalArgumentException_forTwelveDigitNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber("124165550100", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for a null fax number")
    void shouldThrowIllegalArgumentException_forNullFaxNumber() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit North American number");
    }

    @Test
    @DisplayName("should include the one-based account row number in the validation message")
    void shouldIncludeAccountRowNumber_inValidationMessage() {
        assertThatThrownBy(() -> ConfigureFax2Action.normalizeFaxNumber("12345", 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account row 3.");
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
                    .contains("account row 1.");
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

}
