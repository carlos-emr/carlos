/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.forms.FormsDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Patient-bound message-position navigation distinguishes absent results from database failures
 * and never resolves message identifiers for an inaccessible patient.
 *
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("messenger")
@Tag("security")
class MsgViewMessageByPosition2ActionUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private FormsDao forms;
    private Query query;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loginContext;

    @BeforeEach
    void prepare() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("101");
        when(security.hasPrivilege(eq(login), eq("_msg"), eq("r"), nullable(String.class))).thenReturn(true);
        when(security.hasPrivilege(login, "_demographic", "r", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(true);
        forms = createAndRegisterMock(FormsDao.class);
        var entityManager = mock(EntityManager.class);
        when(forms.getEntityManager()).thenReturn(entityManager);
        query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        var request = new MockHttpServletRequest("GET", "/messenger/ViewMessageByPosition");
        request.addParameter("demographic_no", "42");
        request.addParameter("messagePosition", "1");
        request.getSession().setAttribute("user", "101");
        request.getSession().setAttribute("msgSessionBean", new MsgSessionBean());
        response = new MockHttpServletResponse();
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        loginContext = mockStatic(LoggedInInfo.class);
        loginContext.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(login);
    }

    @AfterEach
    void closeContexts() {
        loginContext.close();
        servletContext.close();
    }

    @Test
    void shouldReturnNativeNumericIdentifier_whenPatientAccessIsAuthorized() throws Exception {
        when(query.getSingleResult()).thenReturn(1000000000L);

        var action = new MsgViewMessageByPosition2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getMessageID()).isEqualTo("1000000000");
        verify(query).setParameter("demographic_no", "42");
        verify(query).setFirstResult(1);
        verify(query).setMaxResults(1);
    }

    @Test
    void shouldReturnQuiet404WithoutPreviousIdentifier_whenPositionHasNoMessage() throws Exception {
        when(query.getSingleResult()).thenReturn(100L).thenThrow(new NoResultException());
        var action = new MsgViewMessageByPosition2Action();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        try (var logs = LogCapture.forLogger(MsgViewMessageByPosition2Action.class)) {
            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(logs.messages()).isEmpty();
        }
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(action.getMessageID()).isNull();
    }

    @Test
    void shouldReturn500_whenDatabaseLookupFails() throws Exception {
        when(query.getSingleResult()).thenThrow(new jakarta.persistence.PersistenceException("FAKE database failure"));

        assertThat(new MsgViewMessageByPosition2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void shouldAvoidQueryingMessageIdentifiers_whenPatientAccessIsDenied() throws Exception {
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(false);

        assertThat(new MsgViewMessageByPosition2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        verifyNoInteractions(forms, query);
    }
}
