/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentComment;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentSubClass;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Contract coverage for the HRM report viewer's modify endpoint.
 *
 * <p>The reply shape is the point of these tests. The endpoint used to forward to a
 * {@code text/html} JSP fragment, which the response-decorating filters were free to append a
 * {@code <script>} block to; {@code hrmActions.js} wrote that body into the page, so a clinician
 * adding a comment saw JavaScript printed beside the comment box. Pinning
 * {@code application/json} plus {@link ActionSupport#NONE} keeps those filters off the body.
 */
@Tag("unit")
@Tag("security")
@DisplayName("HRMModifyDocument2Action")
class HRMModifyDocument2ActionUnitTest extends CarlosUnitTestBase {

    private HRMDocumentDao hrmDocumentDao;
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private HRMDocumentToProviderDao hrmDocumentToProviderDao;
    private HRMDocumentSubClassDao hrmDocumentSubClassDao;
    private HRMDocumentCommentDao hrmDocumentCommentDao;
    private IncomingLabRulesDao incomingLabRulesDao;
    private SecurityInfoManager securityInfoManager;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        hrmDocumentDao = mock(HRMDocumentDao.class);
        hrmDocumentToDemographicDao = mock(HRMDocumentToDemographicDao.class);
        hrmDocumentToProviderDao = mock(HRMDocumentToProviderDao.class);
        hrmDocumentSubClassDao = mock(HRMDocumentSubClassDao.class);
        hrmDocumentCommentDao = mock(HRMDocumentCommentDao.class);
        incomingLabRulesDao = mock(IncomingLabRulesDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);

        registerMock(HRMDocumentDao.class, hrmDocumentDao);
        registerMock(HRMDocumentToDemographicDao.class, hrmDocumentToDemographicDao);
        registerMock(HRMDocumentToProviderDao.class, hrmDocumentToProviderDao);
        registerMock(HRMDocumentSubClassDao.class, hrmDocumentSubClassDao);
        registerMock(HRMDocumentCommentDao.class, hrmDocumentCommentDao);
        registerMock(IncomingLabRulesDao.class, incomingLabRulesDao);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        request = new MockHttpServletRequest("POST", "/hospitalReportManager/Modify");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_hrm"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should reject GET before any DAO write")
    void shouldRejectGet_beforeAnyDaoWrite() throws Exception {
        request.setMethod("GET");
        request.addParameter("method", "addComment");
        request.addParameter("reportId", "7");
        request.addParameter("comment", "note");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(hrmDocumentCommentDao);
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should reject HEAD before any DAO write")
    void shouldRejectHead_beforeAnyDaoWrite() throws Exception {
        request.setMethod("HEAD");
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should reply with JSON rather than an HTML fragment when a comment is added")
    void shouldReplyWithJson_whenCommentIsAdded() throws Exception {
        request.addParameter("method", "addComment");
        request.addParameter("reportId", "7");
        request.addParameter("comment", "follow up with patient");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true,\"message\":\"Success\"}");
        verify(hrmDocumentCommentDao).merge(any(HRMDocumentComment.class));
    }

    @Test
    @DisplayName("should reply with a failure message when the comment cannot be persisted")
    void shouldReplyWithFailure_whenCommentCannotBePersisted() throws Exception {
        doThrow(new RuntimeException("boom")).when(hrmDocumentCommentDao).merge(any(HRMDocumentComment.class));
        request.addParameter("method", "addComment");
        request.addParameter("reportId", "7");
        request.addParameter("comment", "follow up with patient");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
    }

    @Test
    @DisplayName("should reply with JSON when a description is set")
    void shouldReplyWithJson_whenDescriptionIsSet() throws Exception {
        when(hrmDocumentDao.find(7)).thenReturn(null);
        request.addParameter("method", "setDescription");
        request.addParameter("reportId", "7");
        request.addParameter("description", "CT chest");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentType()).startsWith("application/json");
        // No such document: the viewer must be told the description did NOT take.
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
    }

    @Test
    @DisplayName("should reject a sign-off that names no report")
    void shouldRejectSignOff_whenNoReportIsNamed() throws Exception {
        request.addParameter("method", "signOff");
        request.addParameter("signedOff", "1");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should reject a sign-off naming more than one report")
    void shouldRejectSignOff_whenMoreThanOneReportIsNamed() throws Exception {
        // One success flag and one clearedCount cannot honestly describe a partly-applied batch:
        // a first report that persisted and a second that threw would report failure, the viewer
        // would suppress the inbox notification, and the report already signed off in the database
        // would sit in the inbox until a full reload. Nothing is written at all instead.
        request.addParameter("method", "signOff");
        request.addParameter("reportId", new String[] {"7", "8"});
        request.addParameter("signedOff", new String[] {"1", "1"});

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should report nothing cleared when the sign-off write throws")
    void shouldReportNothingCleared_whenSignOffWriteThrows() throws Exception {
        // success=false and clearedCount>0 together would let the viewer and the database
        // disagree, so a failed write reports zero.
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), anyString()))
                .thenThrow(new RuntimeException("boom"));
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\",\"clearedCount\":0}");
    }

    @Test
    @DisplayName("should reject a sign-off whose signedOff flag is missing")
    void shouldRejectSignOff_whenSignedOffFlagIsMissing() throws Exception {
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should report one cleared routing row when a sign-off takes")
    void shouldReportOneClearedRow_whenSignOffTakes() throws Exception {
        HRMDocumentToProvider mapping = new HRMDocumentToProvider();
        mapping.setHrmDocumentId(7);
        mapping.setProviderNo("999998");
        mapping.setSignedOff(0);
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), eq("999998")))
                .thenReturn(mapping);
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":true,\"message\":\"Success\",\"clearedCount\":1}");
    }

    @Test
    @DisplayName("should report no cleared row when the report was already signed off")
    void shouldReportNoClearedRow_whenReportWasAlreadySignedOff() throws Exception {
        // The inbox badge counts routing rows and is adjusted client-side. Claiming a row was
        // cleared when it had already left the inbox walks that badge below the truth until the
        // clinician reloads the page.
        HRMDocumentToProvider mapping = new HRMDocumentToProvider();
        mapping.setHrmDocumentId(7);
        mapping.setProviderNo("999998");
        mapping.setSignedOff(1);
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), eq("999998")))
                .thenReturn(mapping);
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("\"clearedCount\":0");
    }

    @Test
    @DisplayName("should report no cleared row when a sign-off is revoked")
    void shouldReportNoClearedRow_whenSignOffIsRevoked() throws Exception {
        HRMDocumentToProvider mapping = new HRMDocumentToProvider();
        mapping.setHrmDocumentId(7);
        mapping.setProviderNo("999998");
        mapping.setSignedOff(1);
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), eq("999998")))
                .thenReturn(mapping);
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "0");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getContentAsString()).contains("\"clearedCount\":0");
    }

    @Test
    @DisplayName("should report no cleared row when the routing row had to be created")
    void shouldReportNoClearedRow_whenRoutingRowHadToBeCreated() throws Exception {
        // No routing row for this provider and no unclaimed one either, so sign-off creates a row
        // that never sat in anybody's inbox. Counting it would decrement a badge for an item that
        // badge had never counted — signing off a standalone report does exactly this.
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), anyString()))
                .thenReturn(null);
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString()).contains("\"clearedCount\":0");
        verify(hrmDocumentToProviderDao).persist(any(HRMDocumentToProvider.class));
    }

    @Test
    @DisplayName("should reject a signedOff value the inbox cannot see")
    void shouldRejectSignedOffValue_thatInboxCannotSee() throws Exception {
        // 2 is the DAO's "any" sentinel, not a state: a row persisted as 2 matches neither the
        // unsigned (=0) nor the signed-off (=1) query, so the report vanishes from every view.
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "2");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should reject a signedOff value that is not a number")
    void shouldRejectSignedOffValue_thatIsNotANumber() throws Exception {
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "yes");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(hrmDocumentToProviderDao);
    }

    @Test
    @DisplayName("should report no cleared row when the previous signedOff was null")
    void shouldReportNoClearedRow_whenPreviousSignedOffWasNull() throws Exception {
        // signedOff is nullable (int(11) DEFAULT NULL) and the badge counts "signedOff=0"
        // exactly, which SQL does not match against NULL — so a legacy null row was never in the
        // count, and reporting it as cleared would walk the badge below the server's figure.
        HRMDocumentToProvider legacyRow = new HRMDocumentToProvider();
        legacyRow.setHrmDocumentId(7);
        legacyRow.setProviderNo("999998");
        legacyRow.setSignedOff(null);
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), eq("999998")))
                .thenReturn(legacyRow);
        request.addParameter("method", "signOff");
        request.addParameter("reportId", "7");
        request.addParameter("signedOff", "1");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getContentAsString()).contains("\"success\":true");
        assertThat(response.getContentAsString()).contains("\"clearedCount\":0");
        // The row is still signed off — only the badge arithmetic is withheld.
        assertThat(legacyRow.getSignedOff()).isEqualTo(1);
    }

    @Test
    @DisplayName("should refuse a sub-class that belongs to another report")
    void shouldRefuseSubClass_thatBelongsToAnotherReport() throws Exception {
        // No setId: the id is JPA-assigned, and the DAO stub below is what supplies this row.
        HRMDocumentSubClass otherReportsSubClass = new HRMDocumentSubClass();
        otherReportsSubClass.setHrmDocumentId(999);
        when(hrmDocumentSubClassDao.find(55)).thenReturn(otherReportsSubClass);
        request.addParameter("method", "makeActiveSubClass");
        request.addParameter("reportId", "7");
        request.addParameter("subClassId", "55");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
        // The report's own sub-classes must be left alone: deactivating first would have stranded
        // it with none active while still reporting success.
        verify(hrmDocumentSubClassDao, never()).setAllSubClassesForDocumentAsInactive(anyInt());
        verify(hrmDocumentSubClassDao, never()).merge(any(HRMDocumentSubClass.class));
    }

    @Test
    @DisplayName("should refuse a sub-class id that no longer exists")
    void shouldRefuseSubClassId_thatNoLongerExists() throws Exception {
        when(hrmDocumentSubClassDao.find(55)).thenReturn(null);
        request.addParameter("method", "makeActiveSubClass");
        request.addParameter("reportId", "7");
        request.addParameter("subClassId", "55");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getContentAsString()).contains("\"success\":false");
        verify(hrmDocumentSubClassDao, never()).setAllSubClassesForDocumentAsInactive(anyInt());
    }

    @Test
    @DisplayName("should activate a sub-class that belongs to the report")
    void shouldActivateSubClass_thatBelongsToTheReport() throws Exception {
        HRMDocumentSubClass ownSubClass = new HRMDocumentSubClass();
        ownSubClass.setHrmDocumentId(7);
        when(hrmDocumentSubClassDao.find(55)).thenReturn(ownSubClass);
        request.addParameter("method", "makeActiveSubClass");
        request.addParameter("reportId", "7");
        request.addParameter("subClassId", "55");

        new HRMModifyDocument2Action().execute();

        assertThat(response.getContentAsString()).contains("\"success\":true");
        verify(hrmDocumentSubClassDao).setAllSubClassesForDocumentAsInactive(7);
        verify(hrmDocumentSubClassDao).merge(ownSubClass);
        assertThat(ownSubClass.isActive()).isTrue();
    }

    @Test
    @DisplayName("should report failure when the category report cannot be found")
    void shouldReportFailure_whenCategoryReportCannotBeFound() throws Exception {
        when(hrmDocumentDao.find(7)).thenReturn(null);
        request.addParameter("method", "updateCategory");
        request.addParameter("reportId", "7");
        request.addParameter("categoryId", "3");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
    }

    @Test
    @DisplayName("should report failure when the category id is not a number")
    void shouldReportFailure_whenCategoryIdIsNotANumber() throws Exception {
        request.addParameter("method", "updateCategory");
        request.addParameter("reportId", "7");
        request.addParameter("categoryId", "not-a-number");

        new HRMModifyDocument2Action().execute();

        // The old nested catch swallowed this and still answered "Success", so the viewer
        // relabelled the category for a change the database never took.
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\"}");
        verifyNoInteractions(hrmDocumentDao);
    }

    @Test
    @DisplayName("should reject an unrecognised dispatch rather than report success")
    void shouldRejectUnrecognisedDispatch_ratherThanReportSuccess() throws Exception {
        request.addParameter("method", "definitelyNotAMethod");

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).contains("\"success\":false");
    }

    @Test
    @DisplayName("should throw SecurityException when HRM write rights are missing")
    void shouldThrowSecurityException_whenHrmWriteRightsAreMissing() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_hrm"), eq("w"), isNull()))
                .thenReturn(false);
        request.addParameter("method", "addComment");
        request.addParameter("reportId", "7");

        HRMModifyDocument2Action action = new HRMModifyDocument2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_hrm)");
        verifyNoInteractions(hrmDocumentCommentDao);
    }
}
