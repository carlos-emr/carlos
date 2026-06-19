/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pageUtil;

import io.github.carlos_emr.carlos.form.data.FrmData;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("security")
@DisplayName("FormForward2Action")
class FormForward2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should omit form name when action path cannot be resolved")
    void shouldOmitFormName_whenActionPathCannotBeResolved() throws Exception {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EncounterFormDao.class, mock(EncounterFormDao.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/form/forward");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContextPath("/carlos");
        request.addParameter("demographic_no", "123");
        request.addParameter("formname", "bad%0D%0Aform");
        when(securityInfoManager.hasPrivilege(any(), eq("_form"), eq(SecurityInfoManager.READ), eq("123")))
                .thenReturn(true);

        try (MockedStatic<ServletActionContext> servletActionContext = mockStatic(ServletActionContext.class);
             MockedConstruction<FrmData> frmDataConstruction = mockConstruction(FrmData.class,
                     (frmData, context) -> when(frmData.getShortcutFormValue("123", "bad\r\nform"))
                             .thenReturn(new String[]{"/unknown-form"}));
             LogCapture capture = LogCapture.forLogger(FormForward2Action.class)) {
            servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

            FormForward2Action action = new FormForward2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(frmDataConstruction.constructed()).hasSize(1);
            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Failed to resolve action path"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).doesNotContain("bad\r\nform", "bad\\r\\nform");
        }
    }
}
