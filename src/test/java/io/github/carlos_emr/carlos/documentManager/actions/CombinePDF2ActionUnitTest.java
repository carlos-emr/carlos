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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private DemographicDao demographicDao;
    private ProgramManager programManager;
    private ProgramManager2 programManager2;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private CombinePDF2Action action;
    private String previousFacilityFilter;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        ctlDocumentDao = mock(CtlDocumentDao.class);
        documentDao = mock(DocumentDao.class);
        demographicDao = mock(DemographicDao.class);
        programManager = mock(ProgramManager.class);
        programManager2 = mock(ProgramManager2.class);

        previousFacilityFilter = CarlosProperties.getInstance().getProperty("FILTER_ON_FACILITY");
        CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", "true");

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("docNo", "321");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)).thenReturn(true);
        when(demographicDao.getDemographicById(anyInt())).thenAnswer(invocation -> {
            Demographic demographic = new Demographic();
            demographic.setDemographicNo(invocation.getArgument(0));
            return demographic;
        });

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action = new CombinePDF2Action(
                securityInfoManager,
                ctlDocumentDao,
                documentDao,
                demographicDao,
                programManager,
                programManager2);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContext != null) {
            servletActionContext.close();
        }
        if (previousFacilityFilter == null) {
            CarlosProperties.getInstance().remove("FILTER_ON_FACILITY");
        } else {
            CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", previousFacilityFilter);
        }
    }

    @Test
    @DisplayName("should reject invalid document numbers before any lookup")
    void shouldRejectInvalidDocumentNumbers_beforeAnyLookup() {
        request.setParameter("docNo", "not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(ctlDocumentDao, documentDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    @DisplayName("should reject non-positive document numbers before any lookup")
    void shouldRejectNonPositiveDocumentNumbers_beforeAnyLookup(String documentNo) {
        request.setParameter("docNo", documentNo);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(ctlDocumentDao, documentDao);
    }

    @Test
    @DisplayName("should reject an excessive number of documents before any lookup")
    void shouldRejectExcessiveDocumentCount_beforeAnyLookup() {
        String[] documentNos = IntStream.rangeClosed(1, 101)
                .mapToObj(String::valueOf)
                .toArray(String[]::new);
        request.setParameter("docNo", documentNos);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verifyNoInteractions(ctlDocumentDao, documentDao);
    }

    @Test
    @DisplayName("should deduplicate document numbers while preserving request order")
    void shouldDeduplicateDocumentNumbers_preservingRequestOrder() {
        request.setParameter("docNo", "321", "654", "321");
        when(ctlDocumentDao.findByDocumentNos(List.of(321, 654))).thenReturn(List.of());

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(ctlDocumentDao).findByDocumentNos(List.of(321, 654));
        verify(documentDao).find(321);
    }

    @Test
    @DisplayName("should reject missing documents before resolving their paths")
    void shouldRejectMissingDocuments_beforeResolvingPaths() {
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
    void shouldRejectCallerWithoutEdocWriteAccess_beforeQueryingDocuments() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_edoc");

        verifyNoInteractions(ctlDocumentDao, documentDao);
    }

    @Test
    @DisplayName("should reject documents outside the caller's patient-record access")
    void shouldRejectDocuments_outsidePatientRecordAccess() {
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
    }

    @Test
    @DisplayName("should allow an existing demographic within the caller's patient-record access")
    void shouldAllowExistingDemographic_withinPatientRecordAccess() {
        Document document = new Document();
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(demographicLink(321, 123)));

        assertThat(authorized).isTrue();
        verify(demographicDao).getDemographicById(123);
        verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, 123);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    @DisplayName("should reject unmatched or invalid demographic links")
    void shouldRejectNonPositiveDemographicLinks_whenUnmatchedOrInvalid(int demographicNo) {
        Document document = new Document();

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(demographicLink(321, demographicNo)));

        assertThat(authorized).isFalse();
        verifyNoInteractions(demographicDao);
    }

    @Test
    @DisplayName("should reject demographic links that do not resolve to a patient")
    void shouldRejectNonexistentDemographicLinks_whenPatientDoesNotResolve() {
        Document document = new Document();
        when(demographicDao.getDemographicById(456)).thenReturn(null);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(demographicLink(321, 456)));

        assertThat(authorized).isFalse();
        verify(demographicDao).getDemographicById(456);
    }

    @Test
    @DisplayName("should use an explicit provider link when a demographic link is unmatched")
    void shouldUseProviderLink_whenDemographicLinkIsUnmatched() {
        Document document = new Document();
        document.setPublic1(0);

        boolean authorized = action.isAuthorizedDocumentScope(loggedInInfo, document, List.of(
                demographicLink(321, -1),
                providerLink(321, 999998)));

        assertThat(authorized).isTrue();
        verifyNoInteractions(demographicDao);
    }

    @Test
    @DisplayName("should reject when any valid demographic link is outside patient-record access")
    void shouldRejectDocument_whenAnyValidDemographicLinkIsUnauthorized() {
        Document document = new Document();
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 456)).thenReturn(false);

        boolean authorized = action.isAuthorizedDocumentScope(loggedInInfo, document, List.of(
                demographicLink(321, 123),
                demographicLink(321, 456)));

        assertThat(authorized).isFalse();
        verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, 123);
        verify(securityInfoManager).isAllowedAccessToPatientRecord(loggedInInfo, 456);
    }

    @Test
    @DisplayName("should reject documents outside the current facility")
    void shouldRejectDocuments_outsideCurrentFacility() {
        Document document = new Document();
        document.setProgramId(77);
        when(programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, 77)).thenReturn(false);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)));

        assertThat(authorized).isFalse();
        verifyNoInteractions(demographicDao);
    }

    @Test
    @DisplayName("should reject program-restricted documents outside the provider domain")
    void shouldRejectProgramRestrictedDocuments_outsideProviderDomain() {
        Document document = new Document();
        document.setProgramId(77);
        document.setRestrictToProgram(true);
        when(programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, 77)).thenReturn(true);
        when(programManager2.getProgramDomain(loggedInInfo, "999998")).thenReturn(List.of());

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)));

        assertThat(authorized).isFalse();
    }

    @Test
    @DisplayName("should allow program-restricted documents inside the provider domain")
    void shouldAllowProgramRestrictedDocuments_insideProviderDomain() {
        Document document = new Document();
        document.setProgramId(77);
        document.setRestrictToProgram(true);
        ProgramProvider programProvider = mock(ProgramProvider.class);
        when(programProvider.getProgramId()).thenReturn(77L);
        when(programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, 77)).thenReturn(true);
        when(programManager2.getProgramDomain(loggedInInfo, "999998"))
                .thenReturn(List.of(programProvider));

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)));

        assertThat(authorized).isTrue();
    }

    @Test
    @DisplayName("should honor disabled facility filtering")
    void shouldHonorFacilitySetting_whenFilteringIsDisabled() {
        CarlosProperties.getInstance().setProperty("FILTER_ON_FACILITY", "false");
        Document document = new Document();
        document.setProgramId(77);
        when(programManager.hasAccessBasedOnCurrentFacility(loggedInInfo, 77)).thenReturn(false);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)));

        assertThat(authorized).isTrue();
        verifyNoInteractions(programManager);
    }

    @Test
    @DisplayName("should allow public provider documents with mixed-case legacy links")
    void shouldAllowPublicProviderDocuments_withMixedCaseLegacyLinks() {
        Document document = new Document();
        document.setPublic1(1);

        boolean authorized = action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink("PrOvIdErS", 321, 123456)));

        assertThat(authorized).isTrue();
    }

    @Test
    @DisplayName("should enforce patient access for mixed-case demographic links")
    void shouldEnforcePatientAccess_forMixedCaseDemographicLinks() {
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
    void shouldAllowPrivateProviderDocuments_forLinkedProviderOnly() {
        Document document = new Document();
        document.setPublic1(0);

        assertThat(action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 999998)))).isTrue();
        assertThat(action.isAuthorizedDocumentScope(
                loggedInInfo, document, List.of(providerLink(321, 123456)))).isFalse();
    }

    @Test
    @DisplayName("should mark PDF responses private and non-cacheable")
    void shouldConfigurePdfResponses_asPrivateAndNonCacheable() {
        action.configurePdfResponse("inline");

        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store, max-age=0");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isZero();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition")).startsWith("inline; filename=\"combinedPDF-");
    }

    @Test
    @DisplayName("should audit every document read for a combined PDF")
    void shouldAuditEveryDocumentRead_forCombinedPdf() {
        Document first = new Document();
        first.setDocumentNo(321);
        Document second = new Document();
        second.setDocumentNo(654);

        action.auditDocumentReads(loggedInInfo, List.of(first, second));

        logActionMock.verify(() -> LogAction.addLog(
                loggedInInfo, LogConst.READ, LogConst.CON_DOCUMENT, "321", null, "combined PDF"));
        logActionMock.verify(() -> LogAction.addLog(
                loggedInInfo, LogConst.READ, LogConst.CON_DOCUMENT, "654", null, "combined PDF"));
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
