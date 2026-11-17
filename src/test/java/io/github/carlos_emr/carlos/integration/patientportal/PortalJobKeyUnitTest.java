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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Reading a job's own key from the CARLOS properties: never the staff key, never a fallback. */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalJobKey")
class PortalJobKeyUnitTest extends CarlosUnitTestBase {

    private static final PortalJob JOB = PortalJob.BOOKING_SYNC;
    private static final String KID = "booking-sync-20261020-a1b2c3";

    private static Map<String, String> staffOnly() {
        Map<String, String> properties = new HashMap<>();
        properties.put(PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY);
        properties.put(PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "primary");
        return properties;
    }

    private static Map<String, String> withJobKey() {
        Map<String, String> properties = staffOnly();
        properties.put("patient_portal.job.booking-sync.private_key", PortalTestKeys.JOB_PRIVATE_KEY);
        properties.put("patient_portal.job.booking-sync.key_id", KID);
        return properties;
    }

    @Test
    @DisplayName("should name the job's two settings under patient_portal.job.booking-sync")
    void shouldNameTheSettings_forTheBookingSyncJob() {
        assertThat(JOB.privateKeyProperty()).isEqualTo("patient_portal.job.booking-sync.private_key");
        assertThat(JOB.keyIdProperty()).isEqualTo("patient_portal.job.booking-sync.key_id");
        assertThat(JOB.permissions()).containsExactly("portal.booking_prompt.sync");
    }

    @Test
    @DisplayName("should read the job's own key and key id")
    void shouldReadTheJobKey_whenBothSettingsAreValid() {
        PortalJobKey key = PortalJobKey.fromProperties(JOB, withJobKey()::get).orElseThrow();
        assertThat(key.job()).isEqualTo(JOB);
        assertThat(key.keyId()).isEqualTo(KID);
        assertThat(key.privateKey().expose()).isEqualTo(PortalTestKeys.JOB_PRIVATE_KEY);
    }

    @Test
    @DisplayName("should be absent, not the staff key, when the job has no key")
    void shouldNotFallBackToTheStaffKey_whenTheJobHasNoKey() {
        assertThat(PortalJobKey.fromProperties(JOB, staffOnly()::get)).isEmpty();
        assertThat(PortalJobKey.fromProperties(JOB, key -> null)).isEmpty();
    }

    @Test
    @DisplayName("should never read the staff private key or the staged .next settings")
    void shouldNeverReadTheStaffKey_whenReadingAJobKey() {
        Map<String, String> properties = withJobKey();
        properties.put("patient_portal.job.booking-sync.private_key.next", "not-a-key");
        properties.put("patient_portal.job.booking-sync.key_id.next", "booking-sync-20261101-0a1b2c");
        List<String> read = new ArrayList<>();
        Function<String, String> lookup = name -> {
            read.add(name);
            return properties.get(name);
        };

        PortalJobKey key = PortalJobKey.fromProperties(JOB, lookup).orElseThrow();

        assertThat(read).doesNotContain(PatientPortalSettings.STAFF_ASSERTION_KEY);
        assertThat(read).noneMatch(name -> name.endsWith(".next"));
        assertThat(key.keyId()).isEqualTo(KID);
    }

    @Test
    @DisplayName("should ignore staged .next settings when the active ones are unset")
    void shouldStayAbsent_whenOnlyNextSettingsAreStaged() {
        Map<String, String> properties = staffOnly();
        properties.put("patient_portal.job.booking-sync.private_key.next", PortalTestKeys.JOB_PRIVATE_KEY);
        properties.put("patient_portal.job.booking-sync.key_id.next", KID);
        assertThat(PortalJobKey.fromProperties(JOB, properties::get)).isEmpty();
    }

    @Test
    @DisplayName("should refuse a job key id equal to the staff key id")
    void shouldRefuseKeyId_whenItEqualsTheStaffKeyId() {
        Map<String, String> properties = withJobKey();
        properties.put("patient_portal.job.booking-sync.key_id", "primary");
        assertThatThrownBy(() -> PortalJobKey.fromProperties(JOB, properties::get))
                .isInstanceOf(PatientPortalConfigurationException.class)
                .hasMessage("patient_portal.job.booking-sync.key_id must differ from "
                        + "patient_portal.staff_assertion.key_id");
    }

    @Test
    @DisplayName("should refuse an invalid private key, naming only the setting")
    void shouldRefuseInvalidKey_withAMessageNamingOnlyTheSetting() {
        for (String bad : List.of("not-a-key", PortalTestKeys.JOB_PRIVATE_KEY + "=",
                PortalTestKeys.JOB_PUBLIC_KEY, "MC4CAQAwBQYDK2VwBCIEIEzNCJso_5ban")) {
            Map<String, String> properties = withJobKey();
            properties.put("patient_portal.job.booking-sync.private_key", bad);
            assertThatThrownBy(() -> PortalJobKey.fromProperties(JOB, properties::get))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageStartingWith("patient_portal.job.booking-sync.private_key must be")
                    .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(bad));
        }
    }

    @Test
    @DisplayName("should refuse a malformed key id, or one setting without the other")
    void shouldRefuseKeyId_whenMalformedOrMissing() {
        for (String bad : List.of("has space", "x".repeat(65), "kid/with/slash", "kidé")) {
            Map<String, String> properties = withJobKey();
            properties.put("patient_portal.job.booking-sync.key_id", bad);
            assertThatThrownBy(() -> PortalJobKey.fromProperties(JOB, properties::get))
                    .isInstanceOf(PatientPortalConfigurationException.class)
                    .hasMessageStartingWith("patient_portal.job.booking-sync.key_id must contain");
        }
        Map<String, String> keyOnly = withJobKey();
        keyOnly.remove("patient_portal.job.booking-sync.key_id");
        assertThatThrownBy(() -> PortalJobKey.fromProperties(JOB, keyOnly::get))
                .isInstanceOf(PatientPortalConfigurationException.class)
                .hasMessageStartingWith("patient_portal.job.booking-sync.key_id is required");
        Map<String, String> idOnly = withJobKey();
        idOnly.remove("patient_portal.job.booking-sync.private_key");
        assertThatThrownBy(() -> PortalJobKey.fromProperties(JOB, idOnly::get))
                .isInstanceOf(PatientPortalConfigurationException.class)
                .hasMessageStartingWith("patient_portal.job.booking-sync.private_key is required");
    }

    @Test
    @DisplayName("should accept a 64-character key id and strip surrounding spaces")
    void shouldAcceptKeyId_atItsLongestLength() {
        Map<String, String> properties = withJobKey();
        properties.put("patient_portal.job.booking-sync.key_id", "  " + "k".repeat(64) + " ");
        assertThat(PortalJobKey.fromProperties(JOB, properties::get).orElseThrow().keyId()).isEqualTo("k".repeat(64));
    }
}
