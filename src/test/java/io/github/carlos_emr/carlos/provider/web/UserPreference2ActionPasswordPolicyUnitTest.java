/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.provider.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The general preferences page can also change the provider's password, and checked the
 * password policy only in the browser. A direct POST with the right current password stored any
 * new password. The save now applies the configured policy first and stores nothing on refusal.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("security")
@DisplayName("General preferences password change policy")
class UserPreference2ActionPasswordPolicyUnitTest extends CarlosUnitTestBase {

    // Synthetic candidates, never real credentials.
    private static final String CURRENT = new String(new char[] {'F', 'A', 'K', 'E', '-', 'o', 'l', 'd', '1'});
    private static final String COMPLIANT = new String(new char[] {'F', 'a', 'k', 'e', '-', 'P', 'w', '1', 'x'});
    private static final String TOO_SHORT = new String(new char[] {'a', 'b', '1'});
    private static final String TWO_GROUPS = new String(new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', '1'});

    private final SecurityManager securityManager = mock(SecurityManager.class);
    private final SecurityDao securityDao = mock(SecurityDao.class);
    private final UserPropertyDAO userPropertyDao = mock(UserPropertyDAO.class);
    private final Security stored = new Security();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(ProviderManager2.class, mock(ProviderManager2.class));
        registerMock(SecurityManager.class, securityManager);
        registerMock(SecurityDao.class, securityDao);
        registerMock(UserPropertyDAO.class, userPropertyDao);

        stored.setPassword("{bcrypt}FAKE-stored-hash");
        when(securityDao.getByProviderNo("999998")).thenReturn(stored);
        when(securityManager.matchesPassword(CURRENT, "{bcrypt}FAKE-stored-hash")).thenReturn(true);
        when(securityManager.encodePassword(any())).thenReturn("{bcrypt}FAKE-new-hash");
        when(userPropertyDao.getDemographicProperties("999998")).thenReturn(List.of());

        request = new MockHttpServletRequest("POST", "/provider/UserPreference");
        request.addPreferredLocale(Locale.ENGLISH);
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        request.setParameter("current_password", CURRENT);
        request.setParameter("pref.colour", "blue");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
    }

    @Test
    @DisplayName("should store a new password that meets the policy")
    void shouldStorePassword_whenNewPasswordMeetsPolicy() {
        request.setParameter("new_password", COMPLIANT);

        assertThat(save()).isEqualTo("form");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(stored.getPassword()).isEqualTo("{bcrypt}FAKE-new-hash");
        verify(securityDao).merge(stored);
        verify(userPropertyDao).saveProp(any(UserProperty.class));
        assertThat(request.getAttribute("preferenceValidationError")).isNull();
    }

    @Test
    @DisplayName("should store nothing when the new password is shorter than the minimum")
    void shouldStoreNothing_whenNewPasswordIsTooShort() {
        request.setParameter("new_password", TOO_SHORT);

        assertThat(save()).isEqualTo("form");

        assertRefused("Password is too short, minimum length is 8 symbols.");
    }

    @Test
    @DisplayName("should store nothing when the new password uses too few character groups")
    void shouldStoreNothing_whenNewPasswordUsesTooFewGroups() {
        request.setParameter("new_password", TWO_GROUPS);

        assertThat(save()).isEqualTo("form");

        assertRefused("Password must contain at least 3"
                + " of the following: capital chars, lower chars, digits, special chars.");
    }

    @Test
    @DisplayName("should keep the submitted preferences on the form when the password is refused")
    void shouldKeepSubmittedPreferences_whenPasswordIsRefused() {
        request.setParameter("new_password", TOO_SHORT);

        save();

        assertThat(request.getAttribute("prefs")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("pref.colour", "blue");
    }

    @Test
    @DisplayName("should leave preferences-only saves alone")
    void shouldSavePreferences_whenNoPasswordIsSubmitted() {
        assertThat(save()).isEqualTo("form");

        assertThat(response.getStatus()).isEqualTo(200);
        verify(userPropertyDao).saveProp(any(UserProperty.class));
        verify(securityDao, never()).merge(any(Security.class));
    }

    private String save() {
        UserPreference2Action action = spy(new UserPreference2Action());
        doNothing().when(action).init();
        return action.saveGeneral();
    }

    private void assertRefused(String message) {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(request.getAttribute("preferenceValidationError")).isEqualTo(message);
        assertThat(stored.getPassword()).isEqualTo("{bcrypt}FAKE-stored-hash");
        verify(securityManager, never()).encodePassword(any());
        verify(securityDao, never()).merge(any(Security.class));
        verify(userPropertyDao, never()).saveProp(any(UserProperty.class));
    }
}
