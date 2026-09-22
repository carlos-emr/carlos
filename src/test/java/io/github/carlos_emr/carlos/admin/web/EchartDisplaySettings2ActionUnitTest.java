// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class EchartDisplaySettings2ActionUnitTest {
    @ParameterizedTest
    @CsvSource({"GET,r", "POST,w"})
    void shouldCheckRequiredPrivilege_beforeRenderingSettings(String method, String privilege) {
        var request = new MockHttpServletRequest();
        request.setMethod(method);
        var security = mock(SecurityInfoManager.class);
        var loggedInInfo = mock(LoggedInInfo.class);
        try (var servlet = mockStatic(ServletActionContext.class);
             var spring = mockStatic(SpringUtils.class);
             var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
            var action = new EchartDisplaySettings2Action();
            assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
            when(security.hasPrivilege(loggedInInfo, "_admin", privilege, null)).thenReturn(true);
            assertThat(action.execute()).isEqualTo("success");
            verify(security, times(2)).hasPrivilege(loggedInInfo, "_admin", privilege, null);
        }
    }
}
