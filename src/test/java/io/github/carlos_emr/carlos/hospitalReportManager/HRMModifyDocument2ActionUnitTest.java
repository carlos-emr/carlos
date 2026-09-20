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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    @DisplayName("should report failure when one report in a bulk sign-off fails")
    void shouldReportFailure_whenOneBulkSignOffReportFails() throws Exception {
        request.addParameter("method", "signOff");
        request.addParameter("reportId", new String[] {"7", "8"});
        request.addParameter("signedOff", new String[] {"1", "1"});
        when(hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNo(eq(7), anyString()))
                .thenThrow(new RuntimeException("boom"));

        String result = new HRMModifyDocument2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        // The later report succeeding must not mask the earlier failure: the viewer would then
        // clear a still-unsigned report out of the inbox. clearedCount still reports the row that
        // did move — it is the truth, and the viewer ignores it on a failed reply anyway.
        assertThat(response.getContentAsString())
                .isEqualTo("{\"success\":false,\"message\":\"Error encountered\",\"clearedCount\":1}");
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
