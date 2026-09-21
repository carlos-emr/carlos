/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
class DelImage2ActionUnitTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "0", "true", "1&other=value"})
    void shouldRestoreScheduleShellOnly_whenFlagIsExactlyEnabled(String flag) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/deleteImage");
        if (flag != null) request.addParameter("scheduleNav", flag);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
                MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class))
                    .thenReturn(mock(SecurityInfoManager.class));
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            String target = new DelImage2Action().getRedirectTarget();
            assertThat(target).isEqualTo("1".equals(flag)
                    ? "/administration?show=ImageUpload&scheduleNav=1"
                    : "/eform/efmimagemanager");
        }
    }
}
