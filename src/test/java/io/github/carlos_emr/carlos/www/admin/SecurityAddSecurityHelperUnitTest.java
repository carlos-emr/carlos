/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.www.admin;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.admin.service.ProviderLoginCreationService;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.jsp.PageContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the authoritative login-creation boundary, including direct POSTs.
 *
 * <p>Issue #2689: every created login carries the role the administrator chose, and a missing,
 * unknown or withheld role refuses the whole creation.</p>
 */
@Tag("unit")
@Isolated("Temporarily configures the clinic password policy")
class SecurityAddSecurityHelperUnitTest extends CarlosUnitTestBase {
    private final SecurityDao records = mock(SecurityDao.class);
    private final SecurityManager passwords = mock(SecurityManager.class);
    private final ProviderLoginCreationService loginCreation = mock(ProviderLoginCreationService.class);
    private final PageContext page = mock(PageContext.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final Map<String, String> original = new HashMap<>();

    @BeforeEach
    void configure() {
        registerMock(SecurityDao.class, records);
        registerMock(SecurityManager.class, passwords);
        registerMock(ProviderLoginCreationService.class, loginCreation);
        lenient().when(loginCreation.assignableRoleNames(any())).thenReturn(List.of("doctor", "receptionist"));
        lenient().when(loginCreation.isEligibleProvider(any(), eq("999998"))).thenReturn(true);
        when(page.getRequest()).thenReturn(request);
        lenient().when(page.getSession()).thenReturn(request.getSession());
        lenient().when(passwords.encodePassword(anyString())).thenReturn("encoded-test-password");
        request.setParameter("user_name", "fixture4167");
        request.setParameter("provider_no", "999998");
        request.setParameter("date_ExpireDate", "2100-01-01");
        request.setParameter("password", "Valid1!Password");
        request.setParameter("conPassword", "Valid1!Password");
        request.setParameter("role_name", "doctor");
        policy("IGNORE_PASSWORD_REQUIREMENTS", "false");
        policy("password_min_length", "8");
        policy("password_min_groups", "3");
        policy("password_group_lower_chars", "abcdefghijklmnopqrstuvwxyz");
        policy("password_group_upper_chars", "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        policy("password_group_digits", "0123456789");
        policy("password_group_special", "!");
    }

    private void policy(String name, String value) {
        CarlosProperties properties = CarlosProperties.getInstance();
        if (!original.containsKey(name)) original.put(name, properties.getProperty(name));
        properties.setProperty(name, value);
    }

    @AfterEach
    void restorePolicy() {
        original.forEach((name, value) -> {
            if (value == null) CarlosProperties.getInstance().remove(name);
            else CarlosProperties.getInstance().setProperty(name, value);
        });
    }

    private String reject() {
        new SecurityAddSecurityHelper().addProvider(page);
        verify(records, never()).persist(any(Security.class));
        verify(loginCreation, never()).createLoginWithRole(any(), any(), any());
        verify(passwords, never()).encodePassword(any());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(page).setAttribute(eq("message"), message.capture());
        assertThat(message.getValue()).isNotBlank().isNotEqualTo("admin.securityaddsecurity.msgAdditionSuccess");
        verify(page, never()).setAttribute(eq(SecurityAddSecurityHelper.ASSIGNED_ROLE_ATTRIBUTE), any());
        logActionMock.verifyNoInteractions();
        return message.getValue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ab1", "alllowercase", "OnlyLetters"})
    void shouldRejectWithoutWrites_whenPasswordViolatesPolicy(String password) {
        if (password == null) request.removeParameter("password");
        else request.setParameter("password", password);
        request.setParameter("conPassword", password == null ? "" : password);
        reject();
    }

    @Test
    void shouldRejectWithoutWrites_whenConfirmationDiffers() {
        request.setParameter("conPassword", "Different1!Password");
        reject();
    }

    @Test
    void shouldRequireConfirmation_whenComplexityIsDisabled() {
        policy("IGNORE_PASSWORD_REQUIREMENTS", "true");
        request.setParameter("password", "weak");
        request.setParameter("conPassword", "different");
        reject();
    }

    @Test
    void shouldUseClinicPolicy_whenConfiguredMinimumIsHigher() {
        policy("password_min_length", "30");
        reject();
    }

    @Test
    void shouldCreateEncodedLoginWithChosenRole_whenPasswordAndConfirmationAreValid() {
        new SecurityAddSecurityHelper().addProvider(page);
        ArgumentCaptor<Security> row = ArgumentCaptor.forClass(Security.class);
        verify(loginCreation).createLoginWithRole(any(), row.capture(), eq("doctor"));
        verify(passwords).encodePassword("Valid1!Password");
        assertThat(row.getValue().getPassword()).isEqualTo("encoded-test-password");
        assertThat(row.getValue().getUserName()).isEqualTo("fixture4167");
        assertThat(row.getValue().getProviderNo()).isEqualTo("999998");
        verify(page).setAttribute("message", "admin.securityaddsecurity.msgAdditionSuccess");
        verify(page).setAttribute(SecurityAddSecurityHelper.ASSIGNED_ROLE_ATTRIBUTE, "doctor");
    }

    @Test
    void shouldLeaveAuditToTheTransactionalService_whenLoginIsCreated() {
        // The service writes both audit entries strictly, inside the creating transaction; the
        // helper must not add best-effort asynchronous copies.
        new SecurityAddSecurityHelper().addProvider(page);
        verify(loginCreation).createLoginWithRole(any(), any(Security.class), eq("doctor"));
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ghost", "siteroot", " doctor"})
    void shouldRejectWithoutWrites_whenRoleIsMissingOrNotAssignable(String roleName) {
        if (roleName == null) request.removeParameter("role_name");
        else request.setParameter("role_name", roleName);
        assertThat(reject()).isEqualTo("admin.securityaddsecurity.msgRoleInvalid");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"700009", "-1"})
    void shouldRejectWithoutWrites_whenProviderIsNotOneTheFormOffers(String providerNo) {
        if (providerNo == null) request.removeParameter("provider_no");
        else request.setParameter("provider_no", providerNo);
        assertThat(reject()).isEqualTo("admin.securityaddsecurity.msgAdditionFailure");
    }

    @Test
    void shouldKeepEarlierRefusal_whenRoleIsAlsoMissing() {
        // Credential refusals keep their own message; the role is checked last.
        request.removeParameter("role_name");
        request.setParameter("conPassword", "Different1!Password");
        assertThat(reject()).isEqualTo("admin.securityrecord.msgPasswordNotConfirmed");
    }

    @Test
    void shouldReportRoleInvalidWithoutAudit_whenRoleIsRemovedBeforeTheWrite() {
        doThrow(new ProviderLoginCreationService.UnknownRoleException())
                .when(loginCreation).createLoginWithRole(any(), any(), any());
        new SecurityAddSecurityHelper().addProvider(page);
        verify(page).setAttribute("message", "admin.securityaddsecurity.msgRoleInvalid");
        verify(page, never()).setAttribute(eq(SecurityAddSecurityHelper.ASSIGNED_ROLE_ATTRIBUTE), any());
        logActionMock.verifyNoInteractions();
    }
}
