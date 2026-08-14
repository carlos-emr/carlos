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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CombinePDF2Action")
@Tag("unit")
@Tag("documentManager")
class CombinePDF2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DocumentDao documentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private CombinePDF2Action action;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        documentDao = mock(DocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("docNo", "321");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)).thenReturn(true);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action = new CombinePDF2Action();
    }

    @AfterEach
    void tearDown() {
        if (servletActionContext != null) {
            servletActionContext.close();
        }
    }

    @Test
    @DisplayName("should reject archive-backed documents before combining PDFs")
    void shouldRejectOutboundArchiveDocumentsBeforeCombiningPdfs() {
        when(outboundEmailArchiveDao.findExistingDocumentNos(List.of(321))).thenReturn(Set.of(321));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(outboundEmailArchiveDao).findExistingDocumentNos(List.of(321));
        verifyNoInteractions(documentDao);
    }
}
