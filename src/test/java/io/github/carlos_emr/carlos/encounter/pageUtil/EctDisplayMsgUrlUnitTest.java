/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("encounter")
class EctDisplayMsgUrlUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveProviderDisplayName_inTheMessengerPopupQuery() {
        var manager = createAndRegisterMock(MessagingManager.class);
        var security = createAndRegisterMock(SecurityInfoManager.class);
        var user = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(user), eq("_msg"), eq("r"), isNull())).thenReturn(true);
        when(manager.getMessagesLinkedToDemographic(user, 101)).thenReturn(List.of());
        var request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        Object previous = ReflectionTestUtils.getField(EctDisplayMsg2Action.class, "messagingManager");
        ReflectionTestUtils.setField(EctDisplayMsg2Action.class, "messagingManager", manager);
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            var action = spy(new EctDisplayMsg2Action());
            doReturn("Messages").when(action).getText(anyString());
            var bean = mock(EctSessionBean.class);
            bean.demographicNo = "101";
            bean.providerNo = "999998";
            for (String name : List.of("Élodie & O'Neil", "A+B", "Name#suffix", "100% name", "漢字 🙂", "A&boxType=9")) {
                bean.userName = name;
                var panel = new NavBarDisplayDAO();
                assertThat(action.getInfo(bean, request, panel)).isTrue();
                URI url = URI.create(panel.getLeftPopup().url());
                Map<String, String> parameters = Arrays.stream(url.getRawQuery().split("&"))
                        .map(pair -> pair.split("=", 2))
                        .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
                assertThat(url.getPath()).isEqualTo("/carlos/messenger/DisplayDemographicMessages");
                assertThat(url.getRawFragment()).isNull();
                assertThat(parameters).containsExactlyInAnyOrderEntriesOf(Map.of("orderby", "date", "boxType", "3",
                        "demographic_no", "101", "providerNo", "999998", "userName", name));
            }
        } finally {
            ReflectionTestUtils.setField(EctDisplayMsg2Action.class, "messagingManager", previous);
        }
    }
}
