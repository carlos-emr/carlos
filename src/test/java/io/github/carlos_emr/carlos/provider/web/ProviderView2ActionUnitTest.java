/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.provider.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.ViewDao;
import io.github.carlos_emr.carlos.commn.model.View;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link ProviderView2Action} (#2778): the Tickler page's saved filters are stored from an
 * allowlist, under the session's role and provider, and the action answers with {@code NONE}.
 */
@Tag("unit")
@DisplayName("ProviderView2Action saveWorkView")
class ProviderView2ActionUnitTest extends CarlosUnitTestBase {

    private ViewDao viewDao;

    @BeforeEach
    void registerDependencies() {
        authorizeEndpoint("w", "_pref");
        viewDao = createAndRegisterMock(ViewDao.class);
    }

    private static MockHttpServletRequest request(String... keysAndValues) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/saveWorkView");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), mock(LoggedInInfo.class));
        for (int i = 0; i < keysAndValues.length; i += 2) {
            request.setParameter(keysAndValues[i], keysAndValues[i + 1]);
        }
        return request;
    }

    private static String execute(MockHttpServletRequest request, MockHttpServletResponse response) {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new ProviderView2Action().execute();
        }
    }

    @Test
    void shouldStoreOnlyTheFourTicklerFilters_underTheSessionRoleAndProvider() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(viewDao.getView("tickler", "doctor", "999998")).thenReturn(new HashMap<>());

        String result = execute(request(
                "method", "save", "view_name", "tickler",
                "userrole", "admin", "providerno", "111111",
                "ticklerview", "C", "providerview", "all", "assignedTo", "999998", "mrpview", "all",
                "<script>alert(1)</script>", "x"), response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<View> saved = ArgumentCaptor.forClass(View.class);
        verify(viewDao, times(4)).saveView(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(view -> {
            assertThat(view.getView_name()).isEqualTo("tickler");
            assertThat(view.getRole()).isEqualTo("doctor");
            assertThat(view.getProviderNo()).isEqualTo("999998");
        });
        assertThat(saved.getAllValues().stream().collect(Collectors.toMap(View::getName, View::getValue)))
                .containsOnly(Map.entry("ticklerview", "C"), Map.entry("providerview", "all"),
                        Map.entry("assignedTo", "999998"), Map.entry("mrpview", "all"));
    }

    @Test
    void shouldUpdateTheExistingRow_insteadOfCreatingASecondOne() {
        View existing = new View();
        existing.setId(5);
        existing.setView_name("tickler");
        existing.setName("ticklerview");
        existing.setValue("A");
        existing.setRole("doctor");
        existing.setProviderNo("999998");
        Map<String, View> current = new HashMap<>();
        current.put("ticklerview", existing);
        when(viewDao.getView("tickler", "doctor", "999998")).thenReturn(current);

        String result = execute(request("view_name", "tickler", "ticklerview", "D"), new MockHttpServletResponse());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        ArgumentCaptor<View> saved = ArgumentCaptor.forClass(View.class);
        verify(viewDao, times(1)).saveView(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getId()).isEqualTo(5);
        assertThat(existing.getValue()).isEqualTo("D");
    }

    @Test
    void shouldAnswer400AndStoreNothing_whenAValueIsNotAllowed() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = execute(request("view_name", "tickler", "ticklerview", "A",
                "assignedTo", "<img src=x onerror=alert(1)>"), response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(viewDao, never()).saveView(any(View.class));
    }

    @Test
    void shouldAnswer400AndStoreNothing_whenTheViewIsUnknown() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = execute(request("view_name", "anything", "ticklerview", "A"), response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(viewDao, never()).getView(anyString(), anyString(), anyString());
        verify(viewDao, never()).saveView(any(View.class));
    }

    @Test
    void shouldAnswer400_whenNoSettingOfTheViewIsSubmitted() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = execute(request("view_name", "tickler", "method", "save"), response);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(viewDao, never()).saveView(any(View.class));
    }

    @Test
    void shouldRefuseToSave_whenTheProviderCannotWritePreferences() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

        assertThatThrownBy(() -> execute(request("view_name", "tickler", "ticklerview", "A"),
                new MockHttpServletResponse()))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pref)");
        verifyNoInteractions(viewDao);
    }
}
