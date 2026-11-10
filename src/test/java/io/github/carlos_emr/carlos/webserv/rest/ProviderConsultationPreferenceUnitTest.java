/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderExtDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.model.ProviderSettings;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
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

    @Test
    void shouldRefuseMissingRestSettingsBeforeAnyWrite() {
        var service = mock(ProviderService.class, CALLS_REAL_METHODS);
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        doReturn(user).when(service).getLoggedInInfo();
        var manager = mock(ProviderManager2.class);
        injectDependency(service, "providerManager", manager);

        assertThatThrownBy(() -> service.saveProviderSettings(null, "999998"))
                .isInstanceOfSatisfying(WebApplicationException.class,
                        error -> assertThat(error.getResponse().getStatus()).isEqualTo(400));

        verifyNoInteractions(manager);
    }

    @Test
    void shouldDelegateValidRestSettingsToTheAuthenticatedProvider() {
        var service = mock(ProviderService.class, CALLS_REAL_METHODS);
        var user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        doReturn(user).when(service).getLoggedInInfo();
        var manager = mock(ProviderManager2.class);
        injectDependency(service, "providerManager", manager);
        var settings = new ProviderSettings();
        settings.setConsultationTimePeriodWarning(" 6 ");

        assertThat(service.saveProviderSettings(settings, "999998")).isNotNull();

        verify(manager).updateProviderSettings(user, "999998", settings);
        verifyNoMoreInteractions(manager);
    }

    @ParameterizedTest
    @CsvSource(value = {"' 6 ',6", "' ',NULL"}, nullValues = "NULL")
    void shouldSaveNormalizedMonthsAndRetainUnrelatedProperties_whenManagerSettingsAreValid(
            String submitted, String expected) {
        var manager = mock(ProviderManager2.class, CALLS_REAL_METHODS);
        var properties = mock(PropertyDao.class);
        injectDependency(manager, "propertyDao", properties);
        injectDependency(manager, "providerPreferenceDao", mock(ProviderPreferenceDao.class));
        injectDependency(manager, "providerExtDao", mock(ProviderExtDao.class));
        var retained = new Property();
        retained.setProviderNo("999998");
        retained.setName("clinic_owned_preference");
        retained.setValue("retained value");
        var priorPeriod = new Property();
        priorPeriod.setProviderNo("999998");
        priorPeriod.setName(UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
        priorPeriod.setValue("12");
        var unrelatedNull = new Property();
        unrelatedNull.setProviderNo("999998");
        unrelatedNull.setName("unrelated_nullable_preference");
        when(properties.findByProvider("999998")).thenReturn(List.of(retained, priorPeriod, unrelatedNull));
        var settings = new ProviderSettings();
        settings.setConsultationTimePeriodWarning(submitted);

        manager.updateProviderSettings(mock(LoggedInInfo.class), "999998", settings);

        var stored = ArgumentCaptor.forClass(Property.class);
        verify(properties, atLeastOnce()).merge(stored.capture());
        assertThat(stored.getAllValues())
                .filteredOn(property -> UserProperty.CONSULTATION_TIME_PERIOD_WARNING.equals(property.getName()))
                .singleElement().satisfies(property -> {
                    assertThat(property).isSameAs(priorPeriod);
                    assertThat(property.getProviderNo()).isEqualTo("999998");
                    assertThat(property.getValue()).isEqualTo(expected);
                });
        assertThat(retained.getValue()).isEqualTo("retained value");
        assertThat(stored.getAllValues()).anyMatch(property -> property == retained)
                .noneMatch(property -> property == unrelatedNull);
    }
}
