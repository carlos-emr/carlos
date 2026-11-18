/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.www.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/** The version the Security Records edit form carries of the login it was rendered from. */
@Tag("unit")
@Tag("security")
@DisplayName("SecurityEditVersion")
class SecurityEditVersionUnitTest extends CarlosUnitTestBase {

    private Security login;

    @BeforeEach
    void setUp() {
        login = new Security();
        login.setSecurityNo(41);
        login.setUserName("pwlogin");
        login.setProviderNo("700123");
        login.setBExpireset(0);
        login.setDateExpiredate(java.sql.Date.valueOf("2100-01-01"));
        login.setBLocallockset(1);
        login.setBRemotelockset(1);
        login.setForcePasswordReset(Boolean.FALSE);
        login.setUsingMfa(false);
        login.setLastUpdateDate(new Date(1_800_000_000_000L));
        login.setPasswordUpdateDate(new Date(1_790_000_000_000L));
        login.setPinUpdateDate(new Date(1_780_000_000_000L));
        login.setPassword("stored-password-hash-marker");
        login.setPin("stored-pin-marker");
        login.setMfaSecret("stored-mfa-secret-marker");
        login.setOneIdKey("stored-one-id-key-marker");
    }

    @Test
    void shouldIgnoreEveryCredential_whenComputingTheVersion() {
        String rendered = SecurityEditVersion.of(login);

        login.setPassword("another-password-hash");
        login.setPin("another-pin");
        login.setMfaSecret("another-mfa-secret");
        login.setOneIdKey("another-one-id-key");
        login.setOneIdEmail("someone@example.invalid");
        login.setLastUpdateUser("999997");

        // No credential reaches the token: changing every one of them leaves it as it was.
        assertThat(SecurityEditVersion.of(login)).isEqualTo(rendered).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(rendered).doesNotContain("stored-password-hash-marker", "stored-pin-marker",
                "stored-mfa-secret-marker", "stored-one-id-key-marker");
    }

    @Test
    void shouldChange_whenAnyValueTheFormShowsOrWritesChanges() {
        List<Consumer<Security>> changes = List.of(
                s -> s.setUserName("pwrenamed"),
                s -> s.setProviderNo("700124"),
                s -> s.setBExpireset(1),
                s -> s.setDateExpiredate(java.sql.Date.valueOf("2099-12-31")),
                s -> s.setBLocallockset(0),
                s -> s.setBRemotelockset(0),
                s -> s.setForcePasswordReset(Boolean.TRUE),
                s -> s.setUsingMfa(true),
                s -> s.setLastUpdateDate(new Date(1_800_000_001_000L)),
                s -> s.setPasswordUpdateDate(new Date(1_800_000_001_000L)),
                s -> s.setPinUpdateDate(new Date(1_800_000_001_000L)));
        List<String> versions = new ArrayList<>();
        versions.add(SecurityEditVersion.of(login));

        for (Consumer<Security> change : changes) {
            change.accept(login);
            versions.add(SecurityEditVersion.of(login));
        }

        assertThat(versions).hasSize(changes.size() + 1).doesNotHaveDuplicates();
    }

    @Test
    void shouldChange_whenAnotherAdministratorForcesAResetWithinTheSameSecond() {
        String rendered = SecurityEditVersion.of(login);
        // lastUpdateDate is stored to the second, so a second save need not move it.
        login.setForcePasswordReset(Boolean.TRUE);

        assertThat(SecurityEditVersion.matches(rendered, login)).isFalse();
    }

    @Test
    void shouldMatchOnlyTheCurrentVersion_forAPostedValue() {
        String current = SecurityEditVersion.of(login);

        assertThat(SecurityEditVersion.matches(current, login)).isTrue();
        assertThat(SecurityEditVersion.matches(" " + current + " ", login)).isTrue();
        assertThat(SecurityEditVersion.matches(current.replace(current.charAt(0), current.charAt(0) == 'a' ? 'b' : 'a'), login)).isFalse();
        assertThat(SecurityEditVersion.matches(null, login)).isFalse();
    }

    @Test
    void shouldTreatBlankAsNoVersion_forUnversionedPosts() {
        assertThat(SecurityEditVersion.isPresent(null)).isFalse();
        assertThat(SecurityEditVersion.isPresent("  ")).isFalse();
        assertThat(SecurityEditVersion.isPresent(SecurityEditVersion.of(login))).isTrue();
    }

    @Test
    void shouldComputeAVersion_forARowWithEmptyColumns() {
        assertThat(SecurityEditVersion.of(new Security())).hasSize(64);
    }
}
