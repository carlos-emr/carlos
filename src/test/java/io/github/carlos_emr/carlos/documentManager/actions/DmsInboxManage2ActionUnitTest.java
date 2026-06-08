/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under GPL v2 or later.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DmsInboxManage2Action} logout redirect short-circuiting.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DmsInboxManage2Action logout redirect")
@Tag("unit")
@Tag("documentManager")
class DmsInboxManage2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Mock
    private QueueDocumentLinkDao queueDocumentLinkDao;

    @Mock
    private SecObjectNameDao secObjectNameDao;

    @Mock
    private SecUserRoleDao secUserRoleDao;

    @Mock
    private QueueDao queueDao;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProviderInboxRoutingDao.class, providerInboxRoutingDao);
        registerMock(QueueDocumentLinkDao.class, queueDocumentLinkDao);
        registerMock(SecObjectNameDao.class, secObjectNameDao);
        registerMock(SecUserRoleDao.class, secUserRoleDao);
        registerMock(QueueDao.class, queueDao);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return NONE when index session is unauthenticated")
    void shouldReturnNone_whenIndexSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForIndexPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @Test
    @DisplayName("should return NONE when content session is unauthenticated")
    void shouldReturnNone_whenContentSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForContentPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao);
    }

    @Test
    @DisplayName("should return NONE when queue session is unauthenticated")
    void shouldReturnNone_whenQueueSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.getDocumentsInQueues();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @Test
    @DisplayName("should return NONE when redirect fails")
    void shouldReturnNone_whenRedirectFails() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        doThrow(new IOException("already committed")).when(failingResponse).sendRedirect("/carlos/logoutPage");
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForIndexPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }
}
