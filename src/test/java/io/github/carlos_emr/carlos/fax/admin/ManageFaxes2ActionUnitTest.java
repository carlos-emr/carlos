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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.util.Collections;

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
 * Unit tests for {@link ManageFaxes2Action}: the GET/HEAD 405 gate on the mutator
 * dispatch targets (CancelFax/ResendFax/SetCompleted), the {@code _admin.fax}
 * privilege gates, the provider-abstracted CancelFax flow, and the null-safe
 * fetchFaxStatus filter handling.
 *
 * <p>This is the focused conditional-mutator test required by
 * {@code MutatorActionGetRejectionContractUnitTest} for this action.</p>
 */
@DisplayName("ManageFaxes2Action unit tests")
@Tag("unit")
@Tag("fax")
class ManageFaxes2ActionUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private FaxProviderClientFactory faxProviderClientFactory;
    private FaxJobDao faxJobDao;
    private FaxConfigDao faxConfigDao;
    private FaxClientLogDao faxClientLogDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private void setUpCommonMocks() {
        securityInfoManager = mock(SecurityInfoManager.class);
        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        faxProviderClientFactory = mock(FaxProviderClientFactory.class);
        faxJobDao = mock(FaxJobDao.class);
        faxConfigDao = mock(FaxConfigDao.class);
        faxClientLogDao = mock(FaxClientLogDao.class);

        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        response = new MockHttpServletResponse();

        // Register mocks BEFORE construction: the action (and its Fax2Action parent)
        // resolves all of these via SpringUtils.getBean in field initializers.
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(FaxProviderClientFactory.class, faxProviderClientFactory);
        registerMock(FaxJobDao.class, faxJobDao);
        registerMock(FaxConfigDao.class, faxConfigDao);
        registerMock(FaxClientLogDao.class, faxClientLogDao);
    }

    private void grantAdminFaxWrite(boolean granted) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("w"), isNull()))
                .thenReturn(granted);
    }

    private FaxJob waitingFaxJob(Integer id, Long providerJobId) {
        FaxJob faxJob = new FaxJob();
        faxJob.setId(id);
        faxJob.setJobId(providerJobId);
        faxJob.setStatus(FaxJob.STATUS.WAITING);
        faxJob.setFax_line("4165550100");
        return faxJob;
    }

    private FaxConfig activeSrfaxConfig() {
        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setId(1);
        faxConfig.setActive(true);
        faxConfig.setFaxNumber("4165550100");
        faxConfig.setProviderType(FaxConfig.ProviderType.SRFAX);
        return faxConfig;
    }

    @ParameterizedTest(name = "GET method={0} is rejected with 405 before dispatch")
    @ValueSource(strings = {"CancelFax", "ResendFax", "SetCompleted"})
    @DisplayName("should send 405 on GET with a mutator method before any side effect")
    void shouldSend405_onGetWithMutatorMethod(String mutatorMethod) {
        setUpCommonMocks();
        request.setMethod("GET");
        request.setParameter("method", mutatorMethod);
        request.setParameter("jobId", "5");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ManageFaxes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getErrorMessage()).isEqualTo("Method not allowed");
            // The verb gate must fire before any DAO lookup or provider call.
            verifyNoInteractions(faxJobDao);
            verifyNoInteractions(faxProviderClientFactory);
        }
    }

    @Test
    @DisplayName("should send 405 on HEAD with method CancelFax before any side effect")
    void shouldSend405_onHeadCancelFax() {
        setUpCommonMocks();
        request.setMethod("HEAD");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "5");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ManageFaxes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(faxJobDao);
            verifyNoInteractions(faxProviderClientFactory);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when the admin fax write privilege is missing on CancelFax")
    void shouldThrowSecurityException_whenCancelFaxWritePrivilegeMissing() {
        setUpCommonMocks();
        grantAdminFaxWrite(false);
        request.setMethod("POST");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "5");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            ManageFaxes2Action action = new ManageFaxes2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_admin.fax)");
            verifyNoInteractions(faxJobDao);
            verifyNoInteractions(faxProviderClientFactory);
        }
    }

    @Test
    @DisplayName("should send 400 without any DAO lookup when the CancelFax jobId parameter is missing")
    void shouldSend400_whenCancelFaxJobIdMissing() {
        setUpCommonMocks();
        grantAdminFaxWrite(true);
        request.setMethod("POST");
        request.setParameter("method", "CancelFax");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ManageFaxes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            // Param validation fails before the DAO is ever consulted.
            verifyNoInteractions(faxJobDao);
            verifyNoInteractions(faxProviderClientFactory);
        }
    }

    @Test
    @DisplayName("should send 400 without a merge when the CancelFax jobId is unknown")
    void shouldSend400_whenCancelFaxJobIdUnknown() {
        setUpCommonMocks();
        grantAdminFaxWrite(true);
        // CancelFax passes an Integer, binding the find(Object) overload.
        when(faxJobDao.find((Object) Integer.valueOf(99))).thenReturn(null);
        request.setMethod("POST");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "99");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ManageFaxes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verify(faxJobDao, never()).merge(any());
            verifyNoInteractions(faxProviderClientFactory);
        }
    }

    @Test
    @DisplayName("should cancel at the provider and merge CANCELLED when the provider confirms cancellation")
    void shouldReportSuccess_whenProviderConfirmsCancellation() throws Exception {
        setUpCommonMocks();
        grantAdminFaxWrite(true);

        FaxJob faxJob = waitingFaxJob(5, 123L);
        FaxConfig faxConfig = activeSrfaxConfig();
        // CancelFax passes an Integer, binding the find(Object) overload.
        when(faxJobDao.find((Object) Integer.valueOf(5))).thenReturn(faxJob);
        when(faxConfigDao.getConfigByNumber("4165550100")).thenReturn(faxConfig);

        FaxProviderClient providerClient = mock(FaxProviderClient.class);
        when(faxProviderClientFactory.getClient(faxConfig)).thenReturn(providerClient);
        FaxJob cancelled = new FaxJob();
        cancelled.setId(5);
        cancelled.setStatus(FaxJob.STATUS.CANCELLED);
        cancelled.setStatusString("Fax Cancelled");
        when(providerClient.cancelFax(faxConfig, faxJob)).thenReturn(cancelled);

        request.setMethod("POST");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "5");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ManageFaxes2Action().execute();

            ArgumentCaptor<FaxJob> mergedCaptor = ArgumentCaptor.forClass(FaxJob.class);
            verify(faxJobDao).merge(mergedCaptor.capture());
            assertThat(mergedCaptor.getValue().getStatus()).isEqualTo(FaxJob.STATUS.CANCELLED);
            assertThat(mergedCaptor.getValue().getStatusString()).isEqualTo("Fax Cancelled");
            assertThat(response.getContentAsString()).contains("\"success\":true");
        }
    }

    @Test
    @DisplayName("should cancel locally without a provider call when the job never reached the provider")
    void shouldCancelLocally_whenJobIdNullAndStatusWaiting() throws Exception {
        setUpCommonMocks();
        grantAdminFaxWrite(true);

        FaxJob faxJob = waitingFaxJob(7, null);
        FaxConfig faxConfig = activeSrfaxConfig();
        // CancelFax passes an Integer, binding the find(Object) overload.
        when(faxJobDao.find((Object) Integer.valueOf(7))).thenReturn(faxJob);
        when(faxConfigDao.getConfigByNumber("4165550100")).thenReturn(faxConfig);

        request.setMethod("POST");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "7");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ManageFaxes2Action().execute();

            // A WAITING job with no provider job id must never reach the provider.
            verifyNoInteractions(faxProviderClientFactory);
            ArgumentCaptor<FaxJob> mergedCaptor = ArgumentCaptor.forClass(FaxJob.class);
            verify(faxJobDao).merge(mergedCaptor.capture());
            assertThat(mergedCaptor.getValue().getStatus()).isEqualTo(FaxJob.STATUS.CANCELLED);
            assertThat(response.getContentAsString()).contains("\"success\":true");
        }
    }

    @Test
    @DisplayName("should report failure with the provider message when the fax already transmitted")
    void shouldReportFailure_whenProviderSaysFaxAlreadyTransmitted() throws Exception {
        setUpCommonMocks();
        grantAdminFaxWrite(true);

        FaxJob faxJob = waitingFaxJob(5, 123L);
        FaxConfig faxConfig = activeSrfaxConfig();
        // CancelFax passes an Integer, binding the find(Object) overload.
        when(faxJobDao.find((Object) Integer.valueOf(5))).thenReturn(faxJob);
        when(faxConfigDao.getConfigByNumber("4165550100")).thenReturn(faxConfig);

        FaxProviderClient providerClient = mock(FaxProviderClient.class);
        when(faxProviderClientFactory.getClient(faxConfig)).thenReturn(providerClient);
        FaxJob transmitted = new FaxJob();
        transmitted.setId(5);
        transmitted.setStatus(FaxJob.STATUS.SENT);
        transmitted.setStatusString("Fax transmission completed");
        when(providerClient.cancelFax(faxConfig, faxJob)).thenReturn(transmitted);

        request.setMethod("POST");
        request.setParameter("method", "CancelFax");
        request.setParameter("jobId", "5");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ManageFaxes2Action().execute();

            ArgumentCaptor<FaxJob> mergedCaptor = ArgumentCaptor.forClass(FaxJob.class);
            verify(faxJobDao).merge(mergedCaptor.capture());
            // The provider outcome is persisted verbatim; success stays false.
            assertThat(mergedCaptor.getValue().getStatus()).isEqualTo(FaxJob.STATUS.SENT);
            assertThat(response.getContentAsString())
                    .contains("\"success\":false")
                    .contains("Fax transmission completed");
        }
    }

    @Test
    @DisplayName("should throw SecurityException when the admin fax read privilege is missing on fetchFaxStatus")
    void shouldThrowSecurityException_whenFetchFaxStatusReadPrivilegeMissing() {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("r"), isNull()))
                .thenReturn(false);
        request.setMethod("POST");
        request.setParameter("method", "fetchFaxStatus");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            ManageFaxes2Action action = new ManageFaxes2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("(_admin.fax)");
            verifyNoInteractions(faxJobDao);
        }
    }

    @Test
    @DisplayName("should treat absent filter parameters as null filters without an NPE on fetchFaxStatus")
    void shouldReturnFaxstatus_whenFilterParametersAbsent() {
        setUpCommonMocks();
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.fax"), eq("r"), isNull()))
                .thenReturn(true);
        when(faxJobDao.getFaxStatusByDateDemographicProviderStatusTeam(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());
        when(faxClientLogDao.findClientLogbyFaxIds(any())).thenReturn(Collections.emptyList());

        request.setMethod("POST");
        request.setParameter("method", "fetchFaxStatus");
        // Deliberately no status/team/oscarUser/demographic_no/date params: the
        // constant-first comparisons must treat them all as null filters.

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            String result = new ManageFaxes2Action().execute();

            assertThat(result).isEqualTo("faxstatus");
            verify(faxJobDao).getFaxStatusByDateDemographicProviderStatusTeam(
                    isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        }
    }

    @Test
    @DisplayName("should send 400 without a merge when the SetCompleted jobId is unknown")
    void shouldSend400_whenSetCompletedJobIdUnknown() {
        setUpCommonMocks();
        grantAdminFaxWrite(true);
        when(faxJobDao.find(99)).thenReturn(null);
        request.setMethod("POST");
        request.setParameter("method", "SetCompleted");
        request.setParameter("jobId", "99");

        try (MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            new ManageFaxes2Action().execute();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verify(faxJobDao, never()).merge(any());
        }
    }
}
