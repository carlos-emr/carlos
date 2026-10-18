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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The OntarioMD username and password page opens with an empty password box, because the stored password is never
 * sent to the browser. Saving the page must keep the stored password unless a new one was typed.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("provider")
class ProviderProperty2ActionOntarioMdUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private UserPropertyDAO properties;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private ProviderProperty2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setMethod("POST");
        properties = mock(UserPropertyDAO.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("owner");
        registerMock(UserPropertyDAO.class, properties);
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        action = new ProviderProperty2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
    }

    private int ids = 40;

    private UserProperty stored(String name, String value) {
        UserProperty property = new UserProperty();
        property.setId(++ids);
        property.setProviderNo("owner");
        property.setName(name);
        property.setValue(value);
        when(properties.getProp("owner", name)).thenReturn(property);
        return property;
    }

    private static UserProperty typed(String value) {
        UserProperty property = new UserProperty();
        property.setValue(value);
        return property;
    }

    private void submit(String username, UserProperty passwordBox) {
        action.setDateProperty(typed(username));
        action.setDateProperty2(passwordBox);
        assertThat(action.saveOntarioMDId()).isEqualTo("gen");
        assertThat(request.getAttribute("status")).isEqualTo("success");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldKeepTheStoredPassword_whenThePasswordBoxIsLeftEmpty(String passwordBox) {
        UserProperty username = stored(UserProperty.ONTARIO_MD_USERNAME, "olduser");
        UserProperty password = stored(UserProperty.ONTARIO_MD_PASSWORD, "stored-secret");

        submit("drsmith", typed(passwordBox));

        assertThat(username.getValue()).isEqualTo("drsmith");
        assertThat(password.getValue()).isEqualTo("stored-secret");
        verify(properties).saveProp(same(username));
        verify(properties, never()).saveProp(same(password));
    }

    @Test
    void shouldKeepTheStoredPassword_whenNoPasswordBoxIsPosted() {
        UserProperty password = stored(UserProperty.ONTARIO_MD_PASSWORD, "stored-secret");

        submit("drsmith", null);

        assertThat(password.getValue()).isEqualTo("stored-secret");
        verify(properties, never()).saveProp(same(password));
    }

    @Test
    void shouldReplaceTheStoredPassword_whenANewOneIsTyped() {
        UserProperty password = stored(UserProperty.ONTARIO_MD_PASSWORD, "stored-secret");

        submit("drsmith", typed("new-secret"));

        assertThat(password.getValue()).isEqualTo("new-secret");
        verify(properties).saveProp(same(password));
    }

    @Test
    void shouldStoreAFirstPassword_forTheSignedInProvider() {
        submit("drsmith", typed("first-secret"));

        ArgumentCaptor<UserProperty> saved = ArgumentCaptor.forClass(UserProperty.class);
        verify(properties, times(2)).saveProp(saved.capture());
        assertThat(saved.getAllValues()).extracting(UserProperty::getName, UserProperty::getValue, UserProperty::getProviderNo)
                .containsExactly(tuple(UserProperty.ONTARIO_MD_USERNAME, "drsmith", "owner"),
                        tuple(UserProperty.ONTARIO_MD_PASSWORD, "first-secret", "owner"));
    }

    @Test
    void shouldCreateNoPasswordRow_whenNoneIsStoredAndTheBoxIsEmpty() {
        submit("drsmith", typed(""));

        ArgumentCaptor<UserProperty> saved = ArgumentCaptor.forClass(UserProperty.class);
        verify(properties).saveProp(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo(UserProperty.ONTARIO_MD_USERNAME);
    }
}
