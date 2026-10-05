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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;

/**
 * What a portal configuration failure may put in the log: the names of the settings involved,
 * and nothing else from any message, since a message or its causes could carry a configured value.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal configuration failure log")
class PortalConfigurationFailureLogUnitTest {

    @Test
    @DisplayName("should name the setting a configuration message mentions")
    void shouldNameTheSetting_whenAMessageMentionsIt() {
        assertThat(PortalJsonAction.settingsNamedBy(new PatientPortalConfigurationException(
                "patient portal is not configured: patient_portal.base_url is required")))
                .isEqualTo("patient_portal.base_url");
        assertThat(PortalJsonAction.settingsNamedBy(new PatientPortalConfigurationException(
                "patient_portal.timeout.request.ms must be at most 59000 milliseconds")))
                .isEqualTo("patient_portal.timeout.request.ms");
    }

    /** How it arrives in production: Spring wraps the settings failure in bean-creation errors. */
    @Test
    @DisplayName("should find the setting when Spring wraps the error")
    void shouldNameTheSetting_whenSpringWrapsTheError() {
        var wrapped = new BeanCreationException("patientPortalService", "dependency failed",
                new BeanCreationException("patientPortalSettings", "creation failed",
                        new BeanInstantiationException(Object.class, "factory method threw",
                                new PatientPortalConfigurationException(
                                        "patient_portal.certificate.pins entries must look like sha256/<pin>"))));

        assertThat(PortalJsonAction.settingsNamedBy(wrapped)).isEqualTo("patient_portal.certificate.pins");
    }

    @Test
    @DisplayName("should keep everything but setting names out of the log")
    void shouldOmitMessageText_otherThanSettingNames() {
        assertThat(PortalJsonAction.settingsNamedBy(new PatientPortalConfigurationException(
                "patient_portal.service_token is invalid: synthetic-secret-value")))
                .isEqualTo("patient_portal.service_token")
                .doesNotContain("synthetic-secret-value");
        assertThat(PortalJsonAction.settingsNamedBy(
                new PatientPortalConfigurationException("synthetic-secret-value is not valid")))
                .isEqualTo("no detail");
    }

    /** A platform or crypto failure names no setting; its cause's class says where to look. */
    @Test
    @DisplayName("should name the cause's class when the message names no setting")
    void shouldNameTheCauseClass_whenTheMessageNamesNoSetting() {
        assertThat(PortalJsonAction.settingsNamedBy(new PatientPortalConfigurationException(
                "could not configure portal certificate pinning",
                new java.security.NoSuchAlgorithmException("synthetic-secret-value"))))
                .isEqualTo("no detail (NoSuchAlgorithmException)")
                .doesNotContain("synthetic-secret-value");
    }

    @Test
    @DisplayName("should stop walking a cause chain that loops back on itself")
    void shouldReportNoDetail_whenTheCauseChainLoops() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);

        String named = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> PortalJsonAction.settingsNamedBy(first));
        assertThat(named).isEqualTo("no detail");
    }
}
