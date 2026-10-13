/*
 * Copyright (c) 2026 CARLOS EMR Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.www.admin;

import java.util.Date;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Credential-state regressions for absent, unchanged and explicit administrator PIN edits. */
@Tag("unit")
class SecurityUpdatePinHandlerUnitTest extends CarlosUnitTestBase {
    private Security record() {
        Security security = new Security();
        security.setPin("stored-credential-marker");
        security.setPinUpdateDate(new Date(1234));
        security.setPassword("stored-password-marker");
        security.setPasswordUpdateDate(new Date(5678));
        security.setUsingMfa(true);
        security.setMfaSecret("encrypted-fixture-marker");
        return security;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreservePinAndDate_whenDisabledControlIsOmitted(boolean legacyEncoding) {
        Security security = record();
        SecurityUpdatePinHandler.apply(security, null, legacyEncoding);
        assertThat(security.getPin()).isEqualTo("stored-credential-marker");
        assertThat(security.getPinUpdateDate()).isEqualTo(new Date(1234));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreservePinAndDate_whenUnchangedSentinelIsPosted(boolean legacyEncoding) {
        Security security = record();
        SecurityUpdatePinHandler.apply(security, "****", legacyEncoding);
        assertThat(security.getPin()).isEqualTo("stored-credential-marker");
        assertThat(security.getPinUpdateDate()).isEqualTo(new Date(1234));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldApplyExplicitPinEdit_withoutChangingOtherCredentials(boolean legacyEncoding) {
        Security security = record();
        String pin = "617204";
        Date before = new Date();
        SecurityUpdatePinHandler.apply(security, pin, legacyEncoding);
        assertThat(security.getPin()).isEqualTo(legacyEncoding ? Misc.encryptPIN(pin) : pin);
        assertThat(security.getPinUpdateDate()).isAfterOrEqualTo(before).isBeforeOrEqualTo(new Date());
        assertThat(security.getPassword()).isEqualTo("stored-password-marker");
        assertThat(security.getPasswordUpdateDate()).isEqualTo(new Date(5678));
        assertThat(security.isUsingMfa()).isTrue();
        assertThat(security.getMfaSecret()).isEqualTo("encrypted-fixture-marker");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainExplicitBlankPinEditBehavior(boolean legacyEncoding) {
        Security security = record();
        Date before = new Date();
        SecurityUpdatePinHandler.apply(security, "", legacyEncoding);
        assertThat(security.getPin()).isEmpty();
        assertThat(security.getPinUpdateDate()).isAfterOrEqualTo(before).isBeforeOrEqualTo(new Date());
    }
}
