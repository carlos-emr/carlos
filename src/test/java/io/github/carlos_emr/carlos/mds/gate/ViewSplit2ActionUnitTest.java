/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GNU General Public License, version 2
 * or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.mds.gate;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ViewSplit2ActionUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private MockedStatic<ServletActionContext> context;
    private ViewSplit2Action action;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(CtlDocumentDao.class, links);
        context = mockStatic(ServletActionContext.class);
        context.when(ServletActionContext::getRequest).thenReturn(request);
        context.when(ServletActionContext::getResponse).thenReturn(response);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        request.setParameter("document", "42");
        when(security.hasPrivilege(any(), eq("_lab"), eq("r"), isNull())).thenReturn(true);
        action = new ViewSplit2Action();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void shouldRefuseSplitViewBeforeJspMetadata_whenSecondaryPatientLinkIsRestricted() {
        when(links.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(link(10), link(20)));
        when(security.isAllowedAccessToPatientRecord(any(), eq(10))).thenReturn(true);
        request.setParameter("demoNo", "10");

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(security).isAllowedAccessToPatientRecord(any(), eq(20));
    }

    @Test
    void shouldOpenSplitView_whenEveryLinkedPatientIsAllowed() {
        when(links.findByDocumentNoAndModule(42, "demographic"))
                .thenReturn(List.of(link(10), link(20)));
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(security).isAllowedAccessToPatientRecord(any(), eq(10));
        verify(security).isAllowedAccessToPatientRecord(any(), eq(20));
    }

    @Test
    void shouldPreserveUnfiledProviderAccess_whenNoPatientIsLinked() {
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link(-1), link(0)));
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @Test
    void shouldRetainLabPrivilegeGate_beforeLookingUpPatientLinks() {
        when(security.hasPrivilege(any(), eq("_lab"), eq("r"), isNull())).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(links);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "0", "-1", "42\r\n", "abc", "2147483648" })
    void shouldRejectMalformedDocumentBeforeJspDispatch(String id) {
        if (id == null) request.removeParameter("document");
        else request.setParameter("document", id);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(links);
    }

    private CtlDocument link(int patient) {
        CtlDocument row = new CtlDocument();
        row.setId(new CtlDocumentPK("demographic", patient, 42));
        return row;
    }
}
