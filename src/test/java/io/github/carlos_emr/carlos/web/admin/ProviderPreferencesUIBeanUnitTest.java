/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.web.admin;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression coverage for the billing preference session fallback (issue #5529). */
@DisplayName("Provider preferences billing session fallback")
class ProviderPreferencesUIBeanUnitTest extends CarlosUnitTestBase {

    private static final String BILLING_PREFERENCE = "caisiBillingPreferenceNotDelete";
    private MockHttpServletRequest request;
    private ProviderPreference preference;
    private ProviderPreferenceDao preferenceDao;
    private URLClassLoader beanLoader;
    private Class<?> beanClass;

    @BeforeEach
    void setUp() throws Exception {
        preferenceDao = createAndRegisterMock(ProviderPreferenceDao.class);
        createAndRegisterMock(EFormDao.class);
        createAndRegisterMock(EncounterFormDao.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(loggedInInfo, "_pref", "w", null)).thenReturn(true);
        request = new MockHttpServletRequest();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        preference = new ProviderPreference();
        preference.setProviderNo("999998");
        preference.setDefaultDoNotDeleteBilling(7);
        when(preferenceDao.find("999998")).thenReturn(preference);

        // The bean caches DAOs in static final fields. Isolate only this class so
        // its mocks neither depend on nor leak into other tests in the same fork.
        String beanName = ProviderPreferencesUIBean.class.getName();
        beanLoader = new URLClassLoader(new URL[] {
                ProviderPreferencesUIBean.class.getProtectionDomain().getCodeSource().getLocation()
        }, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(beanName)) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = findClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        };
        beanClass = Class.forName(beanName, true, beanLoader);
    }

    @AfterEach
    void closeBeanLoader() throws Exception {
        if (beanLoader != null) {
            beanLoader.close();
        }
    }

    static Stream<Arguments> sessionValues() {
        return Stream.of(
                Arguments.of(null, 0, false),
                Arguments.of("", 0, false),
                Arguments.of(" \t ", 0, false),
                Arguments.of(Integer.valueOf(0), 0, false),
                Arguments.of(Integer.valueOf(1), 1, false),
                Arguments.of(" 1 ", 1, false),
                Arguments.of("null", 0, true),
                Arguments.of("invalid", 0, true),
                Arguments.of("2147483648", 0, true));
    }

    @ParameterizedTest
    @MethodSource("sessionValues")
    void shouldSaveBillingDefaultAndWarnOnlyForMalformedSessionValues(
            Object sessionValue, int expected, boolean shouldWarn) throws Exception {
        request.getSession().setAttribute(BILLING_PREFERENCE, sessionValue);

        try (LogCapture logs = LogCapture.forLogger(beanClass)) {
            assertThat(savePreferences()).isSameAs(preference);
            assertThat(preference.getDefaultDoNotDeleteBilling()).isEqualTo(expected);
            if (shouldWarn) {
                assertThat(logs.events()).singleElement().satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(org.apache.logging.log4j.Level.WARN);
                    assertThat(event.getMessage().getFormattedMessage())
                            .contains("Invalid caisiBillingPreferenceNotDelete value from session");
                    assertThat(event.getThrown()).isInstanceOf(NumberFormatException.class);
                });
            } else {
                assertThat(logs.events()).isEmpty();
            }
        }
        verify(preferenceDao).merge(preference);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "invalid"})
    void shouldPreferSubmittedBillingValueOverSessionValue(String sessionValue) throws Exception {
        request.getSession().setAttribute(BILLING_PREFERENCE, sessionValue);
        request.setParameter(BILLING_PREFERENCE, " 1 ");

        try (LogCapture logs = LogCapture.forLogger(beanClass)) {
            savePreferences();
            assertThat(preference.getDefaultDoNotDeleteBilling()).isEqualTo(1);
            assertThat(logs.events()).isEmpty();
        }
        verify(preferenceDao).merge(preference);
    }

    private ProviderPreference savePreferences() throws Exception {
        return (ProviderPreference) beanClass
                .getMethod("updateOrCreateProviderPreferences", HttpServletRequest.class)
                .invoke(null, request);
    }
}
