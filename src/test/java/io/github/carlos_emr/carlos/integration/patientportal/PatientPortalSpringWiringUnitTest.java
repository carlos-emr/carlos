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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

/**
 * The Spring wiring, and specifically the property every CARLOS deployment depends on.
 *
 * <p>Most clinics will never configure a patient portal. For them the correct behaviour is that
 * CARLOS starts normally and the portal features simply are not there — <b>not</b> that the
 * container fails to come up.
 *
 * <p>That is a real risk rather than a hypothetical one. {@code PatientPortalSettings} is
 * deliberately fail-closed: it throws when the portal is unconfigured, because a portal call that
 * silently became a no-op would let a clinic believe an invitation had been sent. Registered as an
 * ordinary eager bean, that same correct rule would abort the Spring context refresh and take the
 * whole EMR down for every clinic not using the feature. {@code lazy-init} is what separates the
 * two, and nothing else in the codebase would notice if it were removed.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Patient portal Spring wiring")
class PatientPortalSpringWiringUnitTest {

    private static final String CONTEXT = "classpath:applicationContextPatientPortal.xml";

    private GenericApplicationContext contextWithSecurityManager() {
        GenericApplicationContext context = new GenericApplicationContext();
        DefaultListableBeanFactory factory = context.getDefaultListableBeanFactory();
        // Registered under the name the XML actually references. An earlier version of this test
        // registered it as "securityInfoManager", which is not a bean in CARLOS — SecurityInfoManager
        // is an interface and its @Service implementation registers as "securityInfoManagerImpl".
        // The test invented the name it was asserting against and passed while a deployed CARLOS
        // failed to start.
        factory.registerSingleton("securityInfoManagerImpl", mock(SecurityInfoManager.class));
        new XmlBeanDefinitionReader(context).loadBeanDefinitions(CONTEXT);
        return context;
    }

    /**
     * The load-bearing assertion. If any bean in that file loses {@code lazy-init}, this fails —
     * which is the only warning anyone gets before an unconfigured clinic cannot start CARLOS.
     */
    @Test
    @DisplayName("should start cleanly on a server with no portal configured")
    void shouldRefreshContext_whenNoPortalIsConfigured() {
        try (GenericApplicationContext context = contextWithSecurityManager()) {
            assertThatCode(context::refresh).doesNotThrowAnyException();
            assertThat(context.isActive()).isTrue();
        }
    }

    @Test
    @DisplayName("should define the portal beans without instantiating them")
    void shouldRegisterBeansLazily_ratherThanEagerly() {
        try (GenericApplicationContext context = contextWithSecurityManager()) {
            context.refresh();

            for (String bean :
                    new String[] {
                        "patientPortalSettings", "patientPortalService", "portalStaffContextResolver"
                    }) {
                assertThat(context.containsBeanDefinition(bean)).isTrue();
                assertThat(context.getBeanFactory().getBeanDefinition(bean).isLazyInit())
                        .withFailMessage(
                                "%s must stay lazy-init: an eager portal bean stops CARLOS starting"
                                        + " on any server without portal configuration",
                                bean)
                        .isTrue();
            }
        }
    }

    /**
     * lazy-init alone is not enough, which only a deployed CARLOS revealed.
     *
     * <p>{@code spring_ws.xml} declares beans that autowire by type, and by-type resolution
     * instantiates candidate beans in order to inspect them. That constructed the portal settings
     * during context refresh on a server with no portal configured, and the whole webapp failed to
     * start — the exact outage lazy-init was chosen to prevent. Excluding these beans from
     * autowiring restores the guarantee.
     */
    @Test
    @DisplayName("should exclude the portal beans from autowiring, which defeats lazy-init")
    void shouldMarkBeansNonAutowireCandidates_soByTypeResolutionCannotInstantiateThem() {
        try (GenericApplicationContext context = contextWithSecurityManager()) {
            context.refresh();

            for (String bean :
                    new String[] {
                        "patientPortalSettings", "patientPortalService", "portalStaffContextResolver"
                    }) {
                assertThat(context.getBeanFactory().getBeanDefinition(bean).isAutowireCandidate())
                        .withFailMessage(
                                "%s must not be an autowire candidate: by-type autowiring elsewhere"
                                        + " in the context instantiates candidates and defeats"
                                        + " lazy-init, stopping CARLOS from starting",
                                bean)
                        .isFalse();
            }
        }
    }

    /**
     * The other half of the contract: a configured-but-broken portal must still fail loudly at the
     * point of use, so a half-configured deployment cannot be mistaken for an absent one.
     */
    @Test
    @DisplayName("should still fail closed when the portal is configured but invalid")
    void shouldThrow_whenConfiguredWithAPlaintextUrl() {
        Map<String, String> properties = new HashMap<>();
        properties.put(PatientPortalSettings.BASE_URL_KEY, "http://portal.clinic.example");
        properties.put(PatientPortalSettings.CLINIC_ID_KEY, "maplecreek");
        properties.put(PatientPortalSettings.SERVICE_TOKEN_KEY, "token-value-0000000000000001");

        assertThatThrownBy(() -> PatientPortalSettings.fromProperties(properties))
                .isInstanceOf(PatientPortalConfigurationException.class);
    }

    @Test
    @DisplayName("should report an absent portal without throwing")
    void shouldReportNotConfigured_whenRequiredKeysAreMissing() {
        assertThat(PatientPortalSettings.isConfigured(key -> null)).isFalse();
        assertThat(PatientPortalSettings.isConfigured(key -> "  ")).isFalse();
    }

    /**
     * Presence only. A configured-but-invalid portal reports as configured here and throws on
     * construction, so a broken deployment surfaces as an error rather than quietly disappearing.
     */
    @Test
    @DisplayName("should report a fully keyed portal as configured, even if a value is invalid")
    void shouldReportConfigured_whenEveryRequiredKeyIsPresent() {
        Map<String, String> present = new HashMap<>();
        present.put(PatientPortalSettings.BASE_URL_KEY, "http://not-https.example");
        present.put(PatientPortalSettings.CLINIC_ID_KEY, "maplecreek");
        present.put(PatientPortalSettings.SERVICE_TOKEN_KEY, "token-value-0000000000000001");

        assertThat(PatientPortalSettings.isConfigured(present::get)).isTrue();
    }
}
