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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.password.PasswordHashHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Exercises the real password encoders and migration with a mocked account store.
 * @since 2026-10-05
 */
@Tag("unit")
@Tag("security")
@DisplayName("Login password whitespace")
class LoginPasswordWhitespaceUnitTest extends CarlosUnitTestBase {
    // Synthetic historical fixtures: signed-byte SHA-1 of the literal / transformed value.
    private static final String RAW = " Legacy1!  X ";
    private static final String RAW_SHA = "7267-11149695-127-16-336863-53-84-12179-12519-1289323";
    private static final String TRANSFORMED_SHA = "-51-96-92-5847585-113118-44-100112-8974-1940754051-124";
    private SecurityDao dao;
    private SecurityManager manager;
    private Security account;

    @BeforeEach
    void setUp() {
        dao = mock(SecurityDao.class);
        manager = spy(new SecurityManager());
        ReflectionTestUtils.setField(manager, "securityDao", dao);
        registerMock(SecurityDao.class, dao);
        registerMock(SecurityManager.class, manager);
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        SecUserRoleDao roles = mock(SecUserRoleDao.class);
        registerMock(SecUserRoleDao.class, roles);
        when(roles.getUserRoles("999998")).thenReturn(Collections.emptyList());
        account = new Security();
        account.setProviderNo("999998");
        account.setBLocallockset(0);
        account.setBRemotelockset(0);
        account.setBExpireset(0);
        when(dao.findByUserName("whitespaceFixture")).thenReturn(Collections.singletonList(account));
    }

    private String[] login(String value) {
        LoginCheckLoginBean bean = new LoginCheckLoginBean();
        bean.ini("whitespaceFixture", value, "", "127.0.0.1");
        return bean.authenticate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Leading1! ", " Leading1!", "Middle1! X", "Many1!  X", " Both1!  X ", "Tabs1!\tX", "Unicode1!\u00a0X", "Ordinary1!"})
    void shouldAuthenticateExactNewPassword_withoutChangingItsHash(String raw) {
        String hash = PasswordHashHelper.encodePassword(raw);
        account.setPassword(hash);
        assertThat(login(raw)).hasSize(7);
        assertThat(account.getPassword()).isEqualTo(hash);
        verify(dao, never()).merge(any(Security.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plaintext", "sha", "bcrypt"})
    void shouldMigrateHistoricalSpaceTransformation_toTheExactEnteredValue(String format) {
        String transformed = RAW.replace(' ', '\b');
        account.setPassword(switch (format) {
            case "plaintext" -> transformed;
            case "sha" -> TRANSFORMED_SHA;
            default -> PasswordHashHelper.encodePassword(transformed);
        });
        assertThat(login(RAW)).hasSize(7);
        assertThat(account.getPassword()).startsWith("{bcrypt}");
        assertThat(PasswordHashHelper.matches(RAW, account.getPassword())).isTrue();
        assertThat(PasswordHashHelper.matches(transformed, account.getPassword())).isFalse();
        verify(manager).upgradeSavePasswordHash(RAW, account);
        verify(dao).merge(account);
        String migratedHash = account.getPassword();
        assertThat(login(RAW)).hasSize(7);
        assertThat(login(transformed)).isNull();
        assertThat(account.getPassword()).isEqualTo(migratedHash);
        verify(dao, times(1)).merge(account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plaintext", "sha"})
    void shouldMigrateLiteralLegacySpaces_withoutTransformingThem(String format) {
        account.setPassword("sha".equals(format) ? RAW_SHA : RAW);
        assertThat(login(RAW)).hasSize(7);
        assertThat(PasswordHashHelper.matches(RAW, account.getPassword())).isTrue();
        verify(manager).upgradeSavePasswordHash(RAW, account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Legacy1!  X", " Legacy1!  X", " Legacy1! X ", " Legacy1!  Y "})
    void shouldRejectTrimmedCollapsedAndIncorrectPasswords_withoutMutation(String wrong) {
        String hash = PasswordHashHelper.encodePassword(RAW);
        account.setPassword(hash);
        assertThat(login(wrong)).isNull();
        assertThat(account.getPassword()).isEqualTo(hash);
        verify(dao, never()).merge(any(Security.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "plaintext", "bcrypt"})
    void shouldPerformTwoEncoderChecks_forFailedSpacePasswords(String kind) {
        if ("missing".equals(kind)) {
            when(dao.findByUserName("whitespaceFixture")).thenReturn(Collections.emptyList());
        } else {
            account.setPassword("plaintext".equals(kind) ? "different" : PasswordHashHelper.encodePassword("different"));
        }
        assertThat(login(RAW)).isNull();
        verify(manager, times(2)).matchesPassword(any(), anyString());
        verify(dao, never()).merge(any(Security.class));
    }

    @Test
    void shouldRejectExpiredAccount_beforeCompatibilityMigration() {
        String hash = PasswordHashHelper.encodePassword(RAW.replace(' ', '\b'));
        account.setPassword(hash);
        account.setBExpireset(1);
        account.setDateExpiredate(new Date(0));
        assertThat(login(RAW)).containsExactly("expired");
        assertThat(account.getPassword()).isEqualTo(hash);
        verifyNoInteractions(manager);
        verify(dao, never()).merge(any(Security.class));
    }

    @Test
    void shouldRejectMissingRequiredPin_beforeCompatibilityMigration() {
        account.setPassword(PasswordHashHelper.encodePassword(RAW.replace(' ', '\b')));
        account.setBRemotelockset(1);
        account.setPin("1234");
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("mfa.legacy.pin.enable", "true")).thenReturn("true");
        try (MockedStatic<CarlosProperties> settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            LoginCheckLoginBean bean = spy(new LoginCheckLoginBean());
            doReturn(true).when(bean).isWAN();
            bean.ini("whitespaceFixture", RAW, "", "127.0.0.1");
            assertThat(bean.authenticate()).isNull();
        }
        // The PIN is checked through SecurityManager (develop #2502 hashes PINs); nothing else on it
        // runs: no password validation and no hash migration once the required PIN is missing.
        verify(manager).validatePin("", account);
        verifyNoMoreInteractions(manager);
        verify(dao, never()).merge(any(Security.class));
    }
}
