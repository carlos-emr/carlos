/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("eform")
class ManageEForm2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Well Baby 0/6 months|Well Baby 0_6 months.zip|WellBaby0_6months",
            "Export Plain|Export Plain.zip|ExportPlain",
            "Ça \"va\" Łódź|Ça \"va\" Łódź.zip|Ça_va_Łódź",
            "../outside|__outside.zip|__outside",
            "CON|_CON.zip|_CON", "nul.txt|_nul.txt.zip|_nul.txt",
            "COM¹|_COM¹.zip|_COM¹", "LPT9|_LPT9.zip|_LPT9",
            "Report.|Report_.zip|Report_", "Clinic\u0085Form|Clinic_Form.zip|Clinic_Form"})
    void shouldDownloadNamedArchive_whenExportTitleContainsSpecialCharacters(String title, String filename, String folder) throws Exception {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo user = mock(LoggedInInfo.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("fid", "123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HashMap<String, Object> form = new HashMap<>();
        form.put("formName", title);
        form.put("formFileName", "form.html");
        form.put("formHtml", "<p>Zoë Ł</p>");
        try (var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var forms = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
            forms.when(() -> EFormUtil.loadEForm("123")).thenReturn(form);
            when(security.hasPrivilege(user, "_eform", "r", null)).thenReturn(true);
            assertThat(new ManageEForm2Action().exportEForm()).isNull();
            assertThat(response.getContentType()).isEqualTo("application/zip");
            String header = response.getHeader("Content-Disposition");
            assertThat(header).isNotNull().matches("[\\x20-\\x7E\\xA0-\\xFF]+").contains("filename*=UTF-8''", "; filename=\"");
            assertThat(ContentDisposition.parse(header).getFilename()).isEqualTo(filename);
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertThat(zip.getNextEntry().getName()).isEqualTo(folder + "/eform.properties");
                assertThat(zip.readAllBytes()).isNotEmpty();
                assertThat(zip.getNextEntry().getName()).isEqualTo(folder + "/form.html");
                assertThat(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("<p>Zoë Ł</p>");
                assertThat(zip.getNextEntry()).isNull();
            }
        }
    }

    @Test
    void shouldDenyExport_whenReadPrivilegeMissing() {
        createAndRegisterMock(SecurityInfoManager.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var login = mockStatic(LoggedInInfo.class);
             var forms = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(mock(LoggedInInfo.class));
            assertThatThrownBy(() -> new ManageEForm2Action().exportEForm()).isInstanceOf(SecurityException.class);
            forms.verifyNoInteractions();
            assertThat(response.getContentAsByteArray()).isEmpty();
        }
    }
}
