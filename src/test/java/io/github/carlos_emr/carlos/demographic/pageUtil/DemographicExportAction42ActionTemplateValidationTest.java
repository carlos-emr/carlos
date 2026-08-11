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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for export template validation on the Demographic Export screen.
 *
 * <p>Covers GitHub issue #3405: the E2E template was offered by the JSP but had no
 * implementation, so submitting it fell through the export switch and produced the generic
 * "export failed" UI. The JSP must offer only templates the action implements, and any other
 * template value must be refused with an explicit validation error.</p>
 *
 * @since 2026-08-11
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action export template validation")
class DemographicExportAction42ActionTemplateValidationTest extends CarlosUnitTestBase {

    private static final Path EXPORT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicExport.jsp");

    /** Matches the template picker rendered by the export JSP. */
    private static final Pattern TEMPLATE_PICKER =
            Pattern.compile("<select[^>]*name=\"template\"[^>]*>(.*?)</select>", Pattern.DOTALL);

    private static final Pattern OPTION_VALUE = Pattern.compile("<option[^>]*value=\"([^\"]*)\"");

    /** Matches a template constant referenced from a JSP expression, e.g. {@code CMS4}. */
    private static final Pattern TEMPLATE_CONSTANT =
            Pattern.compile("DemographicExportAction42Action\\.(\\w+)");

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private DemographicArchiveDao demographicArchiveDao;
    @Mock
    private DemographicContactDao demographicContactDao;
    @Mock
    private PartialDateDao partialDateDao;
    @Mock
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    @Mock
    private HRMDocumentDao hrmDocumentDao;
    @Mock
    private HRMDocumentCommentDao hrmDocumentCommentDao;
    @Mock
    private CaseManagementManager caseManagementManager;
    @Mock
    private Hl7TextInfoDao hl7TextInfoDao;
    @Mock
    private Hl7TextMessageDao hl7TextMessageDao;
    @Mock
    private DemographicExtDao demographicExtDao;

    private DemographicExportAction42Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        registerMock(DemographicArchiveDao.class, demographicArchiveDao);
        registerMock(DemographicContactDao.class, demographicContactDao);
        registerMock(PartialDateDao.class, partialDateDao);
        registerMock(HRMDocumentToDemographicDao.class, hrmDocumentToDemographicDao);
        registerMock(HRMDocumentDao.class, hrmDocumentDao);
        registerMock(HRMDocumentCommentDao.class, hrmDocumentCommentDao);
        registerMock(CaseManagementManager.class, caseManagementManager);
        registerMock(Hl7TextInfoDao.class, hl7TextInfoDao);
        registerMock(Hl7TextMessageDao.class, hl7TextMessageDao);
        registerMock(DemographicExtDao.class, demographicExtDao);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull()))
                .thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull()))
                .thenReturn(true);

        action = new DemographicExportAction42Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    /**
     * A POST carrying a template the action does not implement must be refused explicitly:
     * HTTP 400 plus a reason header, with no export work attempted.
     */
    @Nested
    @DisplayName("Unsupported template rejection")
    class UnsupportedTemplateRejection {

        @Test
        @DisplayName("should reject the retired E2E template with a validation error")
        void shouldRejectExport_whenTemplateIsE2E() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

            String result = action.execute();

            assertThat(result).isNotEqualTo(ActionSupport.SUCCESS);
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            verify(response).setHeader("X-Export-Status", "error");
            verify(response).setHeader("X-Export-Error",
                    DemographicExportAction42Action.UNSUPPORTED_TEMPLATE_MESSAGE);
            verify(response, never()).getOutputStream();
            verifyNoInteractions(demographicExtDao);
        }

        @Test
        @DisplayName("should reject a template value that is not an integer")
        void shouldRejectExport_whenTemplateIsNotNumeric() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate("not-a-template");

            String result = action.execute();

            assertThat(result).isNotEqualTo(ActionSupport.SUCCESS);
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
            verify(response).setHeader("X-Export-Error",
                    DemographicExportAction42Action.UNSUPPORTED_TEMPLATE_MESSAGE);
            // An unparseable value must not quietly run the CMS4 export instead.
            verifyNoInteractions(demographicExtDao);
        }

        @Test
        @DisplayName("should still audit the attempt when the template is rejected")
        void shouldAuditExportAttempt_whenTemplateIsRejected() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            action.setDemographicNo("123");
            action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

            action.execute();

            ArgumentCaptor<OscarLog> auditLogCaptor = ArgumentCaptor.forClass(OscarLog.class);
            logActionMock.verify(() -> LogAction.addLogSynchronous(auditLogCaptor.capture()));
            OscarLog auditLog = auditLogCaptor.getValue();
            assertThat(auditLog.getAction()).isEqualTo(LogConst.EXPORT);
            assertThat(auditLog.getContent()).isEqualTo(LogConst.CON_DEMOGRAPHIC);
            assertThat(auditLog.getData()).contains("outcome=fail");
        }
    }

    /**
     * Ties the templates the JSP renders to the templates the action implements, so a future
     * option cannot be added to the picker without a supporting export path.
     */
    @Nested
    @DisplayName("JSP and action template parity")
    class TemplateParity {

        @Test
        @DisplayName("should offer only supported templates in the export picker")
        void shouldOfferOnlySupportedTemplates_inExportJsp() throws Exception {
            assertThat(offeredTemplates()).isEqualTo(DemographicExportAction42Action.SUPPORTED_TEMPLATES);
        }

        @Test
        @DisplayName("should no longer offer the unimplemented E2E template")
        void shouldNotOfferE2ETemplate_inExportJsp() throws Exception {
            assertThat(templatePickerMarkup()).doesNotContain("E2E");
            assertThat(offeredTemplates()).doesNotContain(DemographicExportAction42Action.E2E);
        }

        @Test
        @DisplayName("should keep EMR DM 5.0 as the supported export template")
        void shouldKeepCms4_asSupportedTemplate() throws Exception {
            assertThat(DemographicExportAction42Action.SUPPORTED_TEMPLATES)
                    .containsExactly(DemographicExportAction42Action.CMS4);
            assertThat(templatePickerMarkup()).contains("EMR DM 5.0");
        }
    }

    /** Returns the markup inside the template picker element of the export JSP. */
    private static String templatePickerMarkup() throws IOException {
        String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);
        Matcher picker = TEMPLATE_PICKER.matcher(jsp);
        assertThat(picker.find())
                .as("template picker element of %s", EXPORT_JSP)
                .isTrue();
        return picker.group(1);
    }

    /**
     * Resolves the template values the JSP offers. Option values are JSP expressions naming a
     * constant on the action (or plain integers), so both forms are resolved to their int value.
     */
    private static Set<Integer> offeredTemplates() throws Exception {
        Set<Integer> templates = new LinkedHashSet<>();
        Matcher option = OPTION_VALUE.matcher(templatePickerMarkup());
        while (option.find()) {
            templates.add(resolveTemplateValue(option.group(1).trim()));
        }
        assertThat(templates).as("template options offered by %s", EXPORT_JSP).isNotEmpty();
        return templates;
    }

    private static Integer resolveTemplateValue(String optionValue) throws Exception {
        Matcher constant = TEMPLATE_CONSTANT.matcher(optionValue);
        if (constant.find()) {
            return DemographicExportAction42Action.class.getField(constant.group(1)).getInt(null);
        }
        String literal = optionValue.replace("<%=", "").replace("%>", "").trim();
        return Integer.valueOf(literal);
    }
}
