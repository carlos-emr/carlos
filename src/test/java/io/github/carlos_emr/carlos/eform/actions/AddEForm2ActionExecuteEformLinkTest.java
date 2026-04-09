/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpSession;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for {@link AddEForm2Action#execute()} verifying that
 * the eform_link validation is properly wired into the action lifecycle.
 *
 * <p>These tests exercise the full {@code execute()} path (with mocked
 * dependencies) to confirm that invalid eform_link values do <b>not</b>
 * produce a session attribute write, while valid values do.</p>
 *
 * <p>Complements {@link AddEForm2ActionEformLinkValidationTest} which tests
 * the {@link AddEForm2Action#validateEformLink(String)} helper in isolation.</p>
 *
 * @since 2026-04-09
 */
@DisplayName("AddEForm2Action execute() eform_link session behavior")
@Tag("unit")
@Tag("eform")
@Tag("security")
class AddEForm2ActionExecuteEformLinkTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<EFormLoader> eFormLoaderMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EformDataManager mockEformDataManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private EmailManager mockEmailManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();

        // Make SpringUtils lenient so that static field initializers in EFormUtil, EForm, etc.
        // can call getBean() for any class without explicit registration
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class)))
            .thenAnswer(invocation -> {
                Class<?> clazz = invocation.getArgument(0);
                return mockedBeans.computeIfAbsent(clazz, Mockito::mock);
            });

        // Mock static contexts
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any()))
            .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");

        // Mock EFormUtil static methods — EForm constructor calls loadEForm(fid) internally
        eFormUtilMock = mockStatic(EFormUtil.class);
        HashMap<String, Object> eformData = new HashMap<>();
        eformData.put("formName", "TestForm");
        eformData.put("formHtml", "");
        eformData.put("formSubject", "");
        eformData.put("formDate", "");
        eformData.put("formFileName", "test.html");
        eformData.put("formCreator", "doc1");
        eFormUtilMock.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(eformData);
        eFormUtilMock.when(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()))
            .then(invocation -> null);

        // Mock EFormLoader for getOpenerNames() — with empty formHtml the loop body is unreachable,
        // but getInstance() is still called
        eFormLoaderMock = mockStatic(EFormLoader.class);
        eFormLoaderMock.when(EFormLoader::getInstance).thenReturn(mock(EFormLoader.class));
        eFormLoaderMock.when(EFormLoader::getOpener).thenReturn("oscarOPEN=");

        // Register Spring beans with explicit mock instances (overrides auto-created ones)
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EformDataManager.class, mockEformDataManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(EmailManager.class, mockEmailManager);

        // Allow eform write privilege
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
            .thenReturn(true);

        // Return a dummy fdid on save
        when(mockEformDataManager.saveEformData(any(LoggedInInfo.class), any()))
            .thenReturn(42);

        // Set required request parameters — minimal set for a clean execute() path
        mockRequest.setParameter("efmfid", "1");
        mockRequest.setParameter("efmdemographic_no", "123");
        // Use print=true to exit cleanly after session write, avoiding EctProgram DB lookup
        mockRequest.setParameter("print", "true");
    }

    @AfterEach
    void tearDown() {
        if (eFormLoaderMock != null) eFormLoaderMock.close();
        if (eFormUtilMock != null) eFormUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    @DisplayName("should write fdid to session when eform_link is valid")
    void shouldWriteFdidToSession_whenEformLinkIsValid() {
        String validLink = "doc1_123_1_referralForm";
        mockRequest.setParameter("eform_link", validLink);

        AddEForm2Action action = new AddEForm2Action();
        action.execute();

        HttpSession session = mockRequest.getSession();
        assertThat(session.getAttribute(validLink))
            .as("Session should have fdid stored under valid eform_link key")
            .isEqualTo("42");
    }

    @Test
    @DisplayName("should not write to session when eform_link is invalid")
    void shouldNotWriteToSession_whenEformLinkIsInvalid() {
        String invalidLink = "user";
        mockRequest.setParameter("eform_link", invalidLink);

        AddEForm2Action action = new AddEForm2Action();
        action.execute();

        HttpSession session = mockRequest.getSession();
        assertThat(session.getAttribute(invalidLink))
            .as("Session should NOT have any attribute for invalid eform_link")
            .isNull();
    }

    @Test
    @DisplayName("should not write to session when eform_link is absent")
    void shouldNotWriteToSession_whenEformLinkIsAbsent() {
        // No eform_link parameter set

        AddEForm2Action action = new AddEForm2Action();
        action.execute();

        HttpSession session = mockRequest.getSession();
        assertThat(session.getAttributeNames().hasMoreElements())
            .as("Session should remain empty when eform_link parameter is absent")
            .isFalse();
    }

    @Test
    @DisplayName("should not write to session when eform_link is a session poisoning attempt")
    void shouldNotWriteToSession_whenEformLinkIsSessionPoisoningAttempt() {
        String poisonKey = "CURRENT_FACILITY";
        mockRequest.setParameter("eform_link", poisonKey);

        AddEForm2Action action = new AddEForm2Action();
        action.execute();

        HttpSession session = mockRequest.getSession();
        assertThat(session.getAttribute(poisonKey))
            .as("Session attribute '%s' should NOT be overwritten by eform_link injection", poisonKey)
            .isNull();
    }
}
