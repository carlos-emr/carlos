/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.upload;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HtmlUpload2Action Unit Tests")
@Tag("unit")
@Tag("eform")
class HtmlUpload2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @TempDir
    private Path tempDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerEFormUtilDependencies();
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);

        eFormUtilMock = mockStatic(EFormUtil.class);
    }

    @AfterEach
    void tearDown() {
        if (eFormUtilMock != null) {
            eFormUtilMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private void registerEFormUtilDependencies() {
        registerMock(CaseManagementManager.class, mock(CaseManagementManager.class));
        registerMock(CaseManagementNoteLinkDAO.class, mock(CaseManagementNoteLinkDAO.class));
        registerMock(EFormDataDao.class, mock(EFormDataDao.class));
        registerMock(EFormValueDao.class, mock(EFormValueDao.class));
        registerMock(EFormGroupDao.class, mock(EFormGroupDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(TicklerDao.class, mock(TicklerDao.class));
        registerMock(PreventionManager.class, mock(PreventionManager.class));
        registerMock(ProgramManager2.class, mock(ProgramManager2.class));
        registerMock(ConsultationRequestDao.class, mock(ConsultationRequestDao.class));
        registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
        registerMock(EFormDao.class, mock(EFormDao.class));
    }

    @Test
    @DisplayName("should validate Struts-bound filename again before saving")
    void shouldValidateStrutsBoundFilenameAgain_beforeSaving() throws Exception {
        File upload = tempDir.resolve("uploaded.html").toFile();
        Files.writeString(upload.toPath(), "<html></html>");

        eFormUtilMock.when(() -> EFormUtil.saveEForm(
                anyString(),
                anyString(),
                eq("bad_name.html"),
                anyString(),
                anyBoolean(),
                anyBoolean(),
                any()))
                .thenReturn("1");

        HtmlUpload2Action action = new HtmlUpload2Action();
        action.setFormHtml(upload);
        action.setFormHtmlFileName("../bad name!.html");
        action.setFormName("Test Form");
        action.setSubject("Subject");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("status")).isEqualTo("success");
        eFormUtilMock.verify(() -> EFormUtil.saveEForm(
                eq("Test Form"),
                eq("Subject"),
                eq("bad_name.html"),
                anyString(),
                eq(false),
                eq(false),
                isNull()));
    }
}
