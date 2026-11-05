/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.owasp.csrfguard.CsrfGuard;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("security")
@DisplayName("CsrfGuardFixture restores the JVM-wide CsrfGuard singleton")
class CsrfGuardFixtureUnitTest {

    @Test
    void shouldRestoreTheGuardStateItFound_whenClosed() throws Exception {
        boolean before = CsrfGuard.getInstance().isEnabled();
        try (CsrfGuardFixture fixture = CsrfGuardFixture.loadShippedConfiguration()) {
            assertThat(fixture.guard().isEnabled()).isTrue();
            assertThat(CsrfGuard.getInstance().isEnabled()).isTrue();
        }
        assertThat(CsrfGuard.getInstance().isEnabled()).as("state other tests in this fork see").isEqualTo(before);
    }

    @Test
    void shouldSurviveNestedUse_whenAnotherFixtureIsAlreadyOpen() throws Exception {
        boolean before = CsrfGuard.getInstance().isEnabled();
        try (CsrfGuardFixture outer = CsrfGuardFixture.loadShippedConfiguration()) {
            try (CsrfGuardFixture inner = CsrfGuardFixture.loadShippedConfiguration()) {
                assertThat(inner.guard().isEnabled()).isTrue();
            }
            // The inner close restores what the inner load found: an enabled guard.
            assertThat(CsrfGuard.getInstance().isEnabled()).isTrue();
            assertThat(outer.guard().isEnabled()).isTrue();
        }
        assertThat(CsrfGuard.getInstance().isEnabled()).isEqualTo(before);
    }
}
