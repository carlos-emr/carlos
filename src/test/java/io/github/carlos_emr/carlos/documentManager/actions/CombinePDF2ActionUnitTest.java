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

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("CombinePDF2Action")
@Tag("unit")
@Tag("documentManager")
class CombinePDF2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CtlDocumentDao ctlDocumentDao;
    private DocumentDao documentDao;
    private OutboundEmailArchiveDao outboundEmailArchiveDao;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private CombinePDF2Action action;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        documentDao = mock(DocumentDao.class);
        outboundEmailArchiveDao = mock(OutboundEmailArchiveDao.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, outboundEmailArchiveDao);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
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
    @DisplayName("should reject invalid document numbers before any lookup")
    void shouldRejectInvalidDocumentNumbers_beforeAnyLookup() {
        request.setParameter("docNo", "not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(ctlDocumentDao, documentDao, outboundEmailArchiveDao);
    }

    @Test
    @DisplayName("should reject missing documents before resolving their paths")
    void shouldRejectMissingDocumentsBeforeResolvingPaths() {
        when(ctlDocumentDao.findByDocumentNos(List.of(321)))
                .thenReturn(List.of(demographicLink(321, 123)));
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(documentDao.find(321)).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    @DisplayName("should reject callers without eDoc write access before querying documents")
    void shouldRejectCallerWithoutEdocWriteAccessBeforeQueryingDocuments() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");

        verifyNoInteractions(ctlDocumentDao, documentDao, outboundEmailArchiveDao);
    }

    @Test
    @DisplayName("should reject documents outside the caller's patient-record access")
    void shouldRejectDocumentsOutsidePatientRecordAccess() {
        Document document = new Document();
        document.setDocfilename("patient-document.pdf");
        when(ctlDocumentDao.findByDocumentNos(List.of(321)))
                .thenReturn(List.of(demographicLink(321, 123)));
        when(documentDao.find(321)).thenReturn(document);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(documentDao).find(321);
        verifyNoInteractions(outboundEmailArchiveDao);
    }

    @Test
    @DisplayName("should allow public provider documents with mixed-case legacy links")
    void shouldAllowPublicProviderDocumentsWithMixedCaseLegacyLinks() {
        Document document = new Document();
        document.setPublic1(1);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink("PrOvIdErS", 321, 123456)));

        assertThat(authorized).isTrue();
    }

    @Test
    @DisplayName("should enforce patient access for mixed-case demographic links")
    void shouldEnforcePatientAccessForMixedCaseDemographicLinks() {
        Document document = new Document();
        document.setPublic1(1);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(false);

        boolean authorized = action.isAuthorizedDocumentScope(loggedInInfo, document, List.of(
                demographicLink("DeMoGrApHiC", 321, 123),
                providerLink(321, 999998)));

        assertThat(authorized).isFalse();
        verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, 123);
    }

    @Test
    @DisplayName("should allow private provider documents only for the linked provider")
    void shouldAllowPrivateProviderDocumentsOnlyForLinkedProvider() {
        Document document = new Document();
        document.setPublic1(0);

        assertThat(action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)))).isTrue();
        assertThat(action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 123456)))).isFalse();
    }

    private CtlDocument demographicLink(Integer documentNo, Integer demographicNo) {
        return demographicLink("demographic", documentNo, demographicNo);
    }

    private CtlDocument demographicLink(String module, Integer documentNo, Integer demographicNo) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK(module, demographicNo, documentNo));
        return ctlDocument;
    }

    private CtlDocument providerLink(Integer documentNo, Integer providerNo) {
        return providerLink("provider", documentNo, providerNo);
    }

    private CtlDocument providerLink(String module, Integer documentNo, Integer providerNo) {
        CtlDocument ctlDocument = new CtlDocument();
        ctlDocument.setId(new CtlDocumentPK(module, providerNo, documentNo));
        return ctlDocument;
    }
}
