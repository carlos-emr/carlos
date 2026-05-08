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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * End-to-end unit tests for {@link ConfigureFax2Action#configure()}.
 *
 * <p>Pins the privilege gate, error mapping, and persist-then-restart-scheduler sequence by driving
 * the action method against in-memory request/response and mocked Spring collaborators.</p>
 *
 * @since 2026-05-07
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("ConfigureFax2Action.configure() Unit Tests")
class ConfigureFax2ActionConfigureTest extends CarlosUnitTestBase {

    @BeforeAll
    static void primeEncryptionKey() {
        // FaxConfig setters encrypt password fields via EncryptionUtils. Provide a deterministic
        // AES-128 key so encrypt/decrypt run without depending on the deployment-time secret.
        byte[] key = new byte[16];
        new SecureRandom().nextBytes(key);
        CarlosProperties.getInstance().setProperty(
                EncryptionUtils.SECRET_KEY_ENV_VAR, Base64.getEncoder().encodeToString(key));
        EncryptionUtils.prepareSecretKeySpec();
    }

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private FaxManager faxManager;
    private FaxConfigDao faxConfigDao;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        securityInfoManager = mock(SecurityInfoManager.class);
        faxManager = mock(FaxManager.class);
        faxConfigDao = mock(FaxConfigDao.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FaxManager.class, faxManager);
        registerMock(FaxConfigDao.class, faxConfigDao);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should throw SecurityException when caller lacks _admin.fax write privilege")
    void shouldThrowSecurityException_whenCallerLacksAdminFaxWrite() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(false);

        ConfigureFax2Action action = new ConfigureFax2Action();

        assertThatThrownBy(action::configure)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.fax");
        verify(faxConfigDao, never()).saveEntity(any(FaxConfig.class));
        verify(faxManager, never()).startFaxSchedulerIfNotRunning(any(LoggedInInfo.class));
    }

    @Test
    @DisplayName("should persist new MIDDLEWARE row and start scheduler when privilege granted")
    void shouldPersistAndStartScheduler_whenPrivilegeGranted() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.emptyList());
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setParameter("faxUrl", "https://relay.example/");
        request.setParameter("siteUser", "site");
        request.setParameter("sitePasswd", "site-secret");
        request.setParameter("id", "0");
        request.setParameter("faxUser", "fax-user");
        request.setParameter("faxPassword", "fax-secret");
        request.setParameter("faxNumber", "4165551234");
        request.setParameter("senderEmail", "ops@example.com");
        request.setParameter("accountName", "Default");
        request.setParameter("inboxQueue", "1");
        request.setParameter("activeState", "true");
        request.setParameter("downloadState", "true");
        request.setParameter("providerType", "MIDDLEWARE");

        ConfigureFax2Action action = new ConfigureFax2Action();

        String result = action.configure();

        // Surface the JSON body in the failure message so an unexpected validation error is
        // immediately diagnosable rather than appearing as an opaque "saveEntity not invoked" miss.
        String body = response.getContentAsString();
        assertThat(result).isNull();
        assertThat(body).as("response JSON").contains("\"success\":true");
        verify(faxConfigDao, atLeastOnce()).saveEntity(any(FaxConfig.class));
        verify(faxManager, times(1)).startFaxSchedulerIfNotRunning(loggedInInfo);
    }

    @Test
    @DisplayName("should report success:false in JSON when validation fails")
    void shouldReportSuccessFalse_whenValidationFails() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.emptyList());

        // Provide an id but omit faxNumber/senderEmail/etc. so the array-length check fires.
        request.setParameter("id", "1");
        request.setParameter("providerType", "MIDDLEWARE");

        ConfigureFax2Action action = new ConfigureFax2Action();

        String result = action.configure();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("\"success\":false");
        verify(faxConfigDao, never()).saveEntity(any(FaxConfig.class));
        verify(faxManager, never()).startFaxSchedulerIfNotRunning(any(LoggedInInfo.class));
    }

    @Test
    @DisplayName("should not start scheduler when no active config rows exist after save")
    void shouldNotStartScheduler_whenNoActiveRowsAfterSave() {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(true);
        when(faxConfigDao.findAll(null, null)).thenReturn(Collections.emptyList());
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setParameter("faxUrl", "https://relay.example/");
        request.setParameter("siteUser", "site");
        request.setParameter("sitePasswd", "site-secret");
        request.setParameter("id", "0");
        request.setParameter("faxUser", "fax-user");
        request.setParameter("faxPassword", "fax-secret");
        request.setParameter("faxNumber", "4165551234");
        request.setParameter("senderEmail", "ops@example.com");
        request.setParameter("accountName", "Default");
        request.setParameter("inboxQueue", "1");
        request.setParameter("activeState", "false");
        request.setParameter("downloadState", "false");
        request.setParameter("providerType", "MIDDLEWARE");

        ConfigureFax2Action action = new ConfigureFax2Action();

        action.configure();

        // The row still gets persisted; only the scheduler bootstrap is skipped because the
        // saved row is inactive. Asserting both pins the behaviour against a regression that
        // would short-circuit the persist itself.
        verify(faxConfigDao, atLeastOnce()).saveEntity(any(FaxConfig.class));
        verify(faxManager, never()).startFaxSchedulerIfNotRunning(any(LoggedInInfo.class));
    }

    @Test
    @DisplayName("should preserve stored MIDDLEWARE password when sentinel mask is submitted")
    void shouldPreserveStoredPassword_whenSentinelSubmitted() throws Exception {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(true);

        // Existing row with an encrypted stored password. The admin form re-submits the masked
        // sentinel "**********" to indicate "do not change". A regression where the action
        // overwrites the stored credential with the sentinel would silently corrupt
        // authentication for every MIDDLEWARE deployment.
        FaxConfig existing = new FaxConfig();
        existing.setId(42);
        existing.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
        existing.setUrl("https://relay.example/");
        existing.setSiteUser("existing-user");
        existing.setPasswd("real-stored-password");  // round-trip-encrypted via setter
        existing.setFaxUser("fax-user");
        existing.setFaxPasswd("fax-real");
        existing.setFaxNumber("4165550000");
        existing.setSenderEmail("ops@example.com");
        existing.setQueue(1);
        existing.setActive(true);
        existing.setDownload(true);
        when(faxConfigDao.findAll(null, null)).thenReturn(java.util.List.of(existing));
        when(faxConfigDao.getCountAll()).thenReturn(1);

        request.setParameter("faxUrl", "https://relay.example/");
        request.setParameter("siteUser", "existing-user");
        request.setParameter("sitePasswd", ConfigureFax2Action.PASSWORD_MASK_SENTINEL);
        request.setParameter("id", "42");
        request.setParameter("faxUser", "fax-user");
        request.setParameter("faxPassword", ConfigureFax2Action.PASSWORD_MASK_SENTINEL);
        request.setParameter("faxNumber", "4165550000");
        request.setParameter("senderEmail", "ops@example.com");
        request.setParameter("accountName", "Default");
        request.setParameter("inboxQueue", "1");
        request.setParameter("activeState", "true");
        request.setParameter("downloadState", "true");
        request.setParameter("providerType", "MIDDLEWARE");

        ConfigureFax2Action action = new ConfigureFax2Action();

        action.configure();

        ArgumentCaptor<FaxConfig> persisted = ArgumentCaptor.forClass(FaxConfig.class);
        verify(faxConfigDao, atLeastOnce()).saveEntity(persisted.capture());
        FaxConfig saved = persisted.getValue();
        assertThat(saved.getPasswd()).as("stored MIDDLEWARE password preserved").isEqualTo("real-stored-password");
        assertThat(saved.getFaxPasswd()).as("stored fax password preserved").isEqualTo("fax-real");
    }
}
