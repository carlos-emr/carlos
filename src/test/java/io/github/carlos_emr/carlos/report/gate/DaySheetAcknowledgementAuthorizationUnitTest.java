/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Element;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class DaySheetAcknowledgementAuthorizationUnitTest extends CarlosUnitTestBase {
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    @BeforeEach void prepare() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        request = new MockHttpServletRequest("POST", "/report/ViewReportdaysheet");
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), actor);
        when(security.hasPrivilege(actor, "_report", "r", null)).thenReturn(true);
        request.setParameter("acknowledge", "true");
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
    }
    @AfterEach void closeServlet() { servlet.close(); }
    private ActionSupport mappedAction() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        var actions = factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/classes/struts-report.xml").toFile())
                .getElementsByTagName("action");
        for (int i=0; i<actions.getLength(); i++) {
            var action = (Element) actions.item(i);
            if ("report/ViewReportdaysheet".equals(action.getAttribute("name"))) {
                return (ActionSupport) Class.forName(action.getAttribute("class")).getDeclaredConstructor().newInstance();
            }
        }
        throw new AssertionError("The real day-sheet route is absent");
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"new"})
    void shouldRefuseAcknowledgement_whenReportReaderCannotWriteAppointments(String mode) throws Exception {
        if (mode != null) request.setParameter("dsmode", mode);
        var action = mappedAction();
        assertThrows(SecurityException.class, action::execute);
    }
    @ParameterizedTest @ValueSource(strings={"GET", "HEAD"})
    void shouldAllowReadOnlyPreview_withoutAppointmentWrite(String method) throws Exception {
        request.setMethod(method);
        assertEquals("success", mappedAction().execute());
        verify(security, never()).hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null);
    }
    @Test void shouldAllowAcknowledgement_whenAppointmentWriteIsGranted() throws Exception {
        when(security.hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null)).thenReturn(true);
        assertEquals("success", mappedAction().execute());
        verify(security).hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null);
    }
    @Test void shouldAllowAllModePost_withoutAppointmentWrite() throws Exception {
        request.setParameter("dsmode", "all");
        assertEquals("success", mappedAction().execute());
        verify(security, never()).hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"false"})
    void shouldAllowAFilterPost_withoutAcknowledgement(String acknowledge) throws Exception {
        request.removeParameter("acknowledge");
        if (acknowledge != null) request.setParameter("acknowledge", acknowledge);
        assertEquals("success", mappedAction().execute());
        verify(security, never()).hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null);
    }
    @Test void shouldKeepTheReportReadRequirement_whenAppointmentWriteIsGranted() throws Exception {
        when(security.hasPrivilege(actor, "_appointment", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(security.hasPrivilege(actor, "_report", "r", null)).thenReturn(false);
        var action = mappedAction();assertThrows(SecurityException.class, action::execute);
    }
    @Test void shouldRefuseAnAnonymousRequest() throws Exception {
        request.getSession().invalidate();
        var action = mappedAction();assertThrows(SecurityException.class, action::execute);
    }
    @ParameterizedTest @ValueSource(strings={"PUT", "DELETE"})
    void shouldKeepMethodRefusal_beforeAnyPrivilegeCheck(String method) throws Exception {
        request.setMethod(method);
        assertEquals("none", mappedAction().execute());assertEquals(405, response.getStatus());
        assertEquals("GET, HEAD, POST", response.getHeader("Allow"));
        verifyNoInteractions(security);
    }
}
