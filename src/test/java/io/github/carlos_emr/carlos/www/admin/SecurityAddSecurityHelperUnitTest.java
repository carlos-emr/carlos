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
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.jsp.PageContext;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Verifies the authoritative login-creation boundary, including direct POSTs: password policy and
 * confirmation are enforced before any credential is hashed or persisted, PINs are hashed only when
 * one is actually supplied, and malformed lock settings default to disabled.
 */
@Tag("unit")
@Tag("security")
@DisplayName("SecurityAddSecurityHelper")
@Isolated("Temporarily configures the clinic password policy")
class SecurityAddSecurityHelperUnitTest extends CarlosUnitTestBase {

    private static final String VALID_PASSWORD = "Valid1!Password";
    private static final String HASHED_PASSWORD = "encoded-test-password";
    private static final String RAW_PIN = "1234";
    private static final String HASHED_PIN = "{bcrypt}pin";

    private final SecurityDao records = mock(SecurityDao.class);
    private final SecurityManager passwords = mock(SecurityManager.class);
    private final PageContext page = mock(PageContext.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final Map<String, String> original = new HashMap<>();

    @BeforeEach
    void configure() {
        registerMock(SecurityDao.class, records);
        registerMock(SecurityManager.class, passwords);
        when(page.getRequest()).thenReturn(request);
        lenient().when(page.getSession()).thenReturn(request.getSession());
        lenient().when(passwords.encodePassword(anyString())).thenReturn(HASHED_PASSWORD);
        lenient().when(passwords.encodePin(RAW_PIN)).thenReturn(HASHED_PIN);
        request.setParameter("user_name", "fixture4167");
        request.setParameter("provider_no", "999998");
        request.setParameter("date_ExpireDate", "2100-01-01");
        request.setParameter("password", VALID_PASSWORD);
        request.setParameter("conPassword", VALID_PASSWORD);
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

    private void reject() {
        new SecurityAddSecurityHelper().addProvider(page);
        verify(records, never()).persist(any(Security.class));
        verify(passwords, never()).encodePassword(any());
        verify(passwords, never()).encodePin(any());
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(page).setAttribute(eq("message"), message.capture());
        assertThat(message.getValue()).isNotBlank().isNotEqualTo("admin.securityaddsecurity.msgAdditionSuccess");
        logActionMock.verifyNoInteractions();
    }

    private Security addAndCapturePersisted() {
        new SecurityAddSecurityHelper().addProvider(page);
        ArgumentCaptor<Security> row = ArgumentCaptor.forClass(Security.class);
        verify(records).persist(row.capture());
        verify(page).setAttribute("message", "admin.securityaddsecurity.msgAdditionSuccess");
        return row.getValue();
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
    void shouldRejectWithoutHashingPin_whenPasswordIsRejected() {
        // A rejected request must not pay for, or leave behind, any credential hash.
        request.setParameter("pin", RAW_PIN);
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
    void shouldCreateEncodedLogin_whenPasswordAndConfirmationAreValid() {
        Security persisted = addAndCapturePersisted();

        verify(passwords).encodePassword(VALID_PASSWORD);
        assertThat(persisted.getPassword()).isEqualTo(HASHED_PASSWORD);
        assertThat(persisted.getUserName()).isEqualTo("fixture4167");
    }

    @Test
    @DisplayName("should persist hashed PIN and default invalid lock settings when adding provider")
    void shouldPersistHashedPinAndDefaultInvalidLockSettings_whenAddingProvider() {
        request.setParameter("pin", RAW_PIN);
        request.setParameter("b_ExpireSet", "invalid");
        request.setParameter("b_LocalLockSet", "1");
        request.setParameter("forcePasswordReset", "1");

        Security persisted = addAndCapturePersisted();

        assertThat(persisted.getPassword()).isEqualTo(HASHED_PASSWORD);
        assertThat(persisted.getPin()).isEqualTo(HASHED_PIN);
        assertThat(persisted.getBExpireset()).isZero();
        assertThat(persisted.getBLocallockset()).isOne();
        assertThat(persisted.getBRemotelockset()).isZero();
        assertThat(persisted.getPasswordUpdateDate()).isNotNull();
        assertThat(persisted.getPinUpdateDate()).isNotNull();
    }

    @Test
    @DisplayName("should persist provider without PIN when the PIN field is absent")
    void shouldPersistProviderWithoutPin_whenPinFieldIsAbsent() {
        // The add form omits the PIN controls entirely when legacy PINs are globally disabled, so
        // the parameter is null. Hashing it anyway stamps a pinUpdateDate on an account that has
        // no PIN; the empty-string case below is worse still, because the encoder happily returns
        // a valid bcrypt hash of "" and the row then looks PIN-protected.
        Security persisted = addAndCapturePersisted();

        assertThat(persisted.getPin()).isNull();
        assertThat(persisted.getPinUpdateDate()).isNull();
        assertThat(persisted.getPassword()).isEqualTo(HASHED_PASSWORD);
        verify(passwords, never()).encodePin(any());
    }

    @Test
    @DisplayName("should persist provider without PIN when MFA disables the PIN controls")
    void shouldPersistProviderWithoutPin_whenMfaDisablesThePinControls() {
        // Selecting MFA disables the PIN inputs, so the value arrives empty rather than absent.
        request.setParameter("pin", "");
        request.setParameter("enableMfa", "1");

        Security persisted = addAndCapturePersisted();

        assertThat(persisted.getPin()).isNull();
        assertThat(persisted.getPinUpdateDate()).isNull();
        assertThat(persisted.isUsingMfa()).isTrue();
        verify(passwords, never()).encodePin(any());
    }

    @Test
    @DisplayName("should parse lock setting when value is valid")
    void shouldParseLockSetting_whenValueIsValid() {
        assertThat(SecurityAddSecurityHelper.parseLockSetting("0")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("1")).isOne();
    }

    @Test
    @DisplayName("should default lock setting when value is missing or invalid")
    void shouldDefaultLockSetting_whenValueIsMissingOrInvalid() {
        assertThat(SecurityAddSecurityHelper.parseLockSetting(null)).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("invalid")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("-1")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("2")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("1.5")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting(" 1 ")).isZero();
        assertThat(SecurityAddSecurityHelper.parseLockSetting("2147483648")).isZero();
    }
}
