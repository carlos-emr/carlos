package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.lang.reflect.Method;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@Tag("unit")
class ManageDocument2ActionTest extends CarlosUnitTestBase {

    @Mock
    private DocumentDao documentDao;

    @Mock
    private CtlDocumentDao ctlDocumentDao;

    @Mock
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ManageDocument2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(DocumentDao.class, documentDao);
        registerMock(CtlDocumentDao.class, ctlDocumentDao);
        registerMock(ProviderInboxRoutingDao.class, providerInboxRoutingDao);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        request = new MockHttpServletRequest();
        response = spy(new MockHttpServletResponse());
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action = new ManageDocument2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContext != null) {
            servletActionContext.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void shouldReturnNoneAndSendError_whenDirectResponseHandlerFailsBeforeCommit() throws Exception {
        request.setParameter("method", "viewDocumentInfo");
        doThrow(new IllegalStateException("writer failed")).when(response).getWriter();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldReturnNone_whenDirectResponseHandlerFailsAfterCommit() {
        request.setParameter("method", "viewDocumentInfo");
        CommittedFailingResponse committedResponse = new CommittedFailingResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(committedResponse);
        action = new ManageDocument2Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldReturnNoneAndSendForbidden_whenDirectResponseHandlerDeniesAuthorization() {
        request.setParameter("method", "display");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }


    @Test
    void shouldReturnNoneAndSendForbidden_whenNonDirectHandlerDeniesAuthorization() {
        request.setParameter("method", "refileDocumentAjax");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(action.getActionErrors()).isEmpty();
    }

    @Test
    void shouldSanitizeFilename_whenBuildingContentDispositionHeader() throws Exception {
        Method sanitize = ManageDocument2Action.class.getDeclaredMethod("sanitizeHeaderValue", String.class);
        sanitize.setAccessible(true);

        String sanitized = (String) sanitize.invoke(action, "chart\r\nContent-Length: 0.pdf");

        assertThat(sanitized).isEqualTo("chartContent-Length: 0.pdf");
    }

    @Test
    void shouldLogWarning_whenProviderRoutingDenied() throws Exception {
        doThrow(new SecurityException("missing _edoc")).when(providerInboxRoutingDao)
                .addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
        Method route = ManageDocument2Action.class.getDeclaredMethod(
                "routeDocumentToProviders", String[].class, String.class, String.class);
        route.setAccessible(true);

        route.invoke(action, new String[] { "999998" }, "42", "100");

        verify(providerInboxRoutingDao).addToProviderInbox("999998", 42, LabResultData.DOCUMENT);
    }

    private static final class CommittedFailingResponse extends MockHttpServletResponse {
        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public PrintWriter getWriter() {
            throw new IllegalStateException("writer failed after commit");
        }

        @Override
        public void sendError(int status) {
            throw new AssertionError("sendError must not be called for committed responses");
        }

        @Override
        public void sendError(int status, String errorMessage) {
            throw new AssertionError("sendError must not be called for committed responses");
        }
    }
}
