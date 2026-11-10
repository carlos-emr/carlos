/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.model.ProviderSettings;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProviderConsultationPreferenceUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldRefuseInvalidMonthsBeforeAnySettingIsWritten_whenSavingRestPreferences() {
        var service = mock(ProviderService.class, CALLS_REAL_METHODS);
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        doReturn(user).when(service).getLoggedInInfo();
        var manager = mock(ProviderManager2.class);
        injectDependency(service, "providerManager", manager);
        var settings = new ProviderSettings();
        settings.setConsultationTimePeriodWarning("6 months");
        assertThatThrownBy(() -> service.saveProviderSettings(settings, "999998"))
                .isInstanceOfSatisfying(WebApplicationException.class,
                        error -> assertThat(error.getResponse().getStatus()).isEqualTo(400));
        verifyNoInteractions(manager);
    }

    @Test
    void shouldValidateBeforeLoadingMutableEntities_whenCalledThroughTheManager() {
        var manager = mock(ProviderManager2.class, CALLS_REAL_METHODS);
        var settings = new ProviderSettings();
        settings.setConsultationTimePeriodWarning("0");
        // The uninitialized DAO fields would fail if mutable settings were loaded first.
        assertThatThrownBy(() -> manager.updateProviderSettings(mock(LoggedInInfo.class), "999998", settings))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
