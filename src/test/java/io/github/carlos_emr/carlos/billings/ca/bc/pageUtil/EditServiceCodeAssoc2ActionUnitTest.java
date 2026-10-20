/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("billing")
class EditServiceCodeAssoc2ActionUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldOpenAnEmptyWritableEditor_whenCreatingAnAssociation() {
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(user), eq("_billing"), eq("w"), isNull())).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class);
             MockedStatic<LoggedInInfo> login = mockStatic(LoggedInInfo.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(user);
            EditServiceCodeAssoc2Action action = new EditServiceCodeAssoc2Action();
            action.setMode("create");
            action.validate();
            assertThat(action.hasFieldErrors()).isFalse();
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(request.getAttribute("mode")).isEqualTo("");
            ServiceCodeAssociation association = (ServiceCodeAssociation) request.getAttribute("serviceCodeAssociation");
            assertThat(association.getServiceCode()).isNull();
            assertThat(association.getDxCodes()).isEmpty();
        }
    }
}
