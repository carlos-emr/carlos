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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unset or bad settings fall back to offering nothing and not syncing (#3850). */
@Tag("unit")
@Tag("patient-portal")
class PortalBookingSettingsUnitTest {
    @Test
    void shouldOfferNothingAndNotSync_whenNothingIsConfigured() {
        PortalBookingSettings settings = PortalBookingSettings.fromProperties(key -> null);
        assertThat(settings.offerableCodes()).isEmpty();
        assertThat(settings.visitMode()).isEqualTo("in_person");
        assertThat(settings.locationCode()).isNull();
        assertThat(settings.leadHours()).isEqualTo(24);
        assertThat(settings.syncEnabled()).isFalse();
    }

    @Test
    void shouldReadTheClinicsChoices_whenConfigured() {
        Map<String, String> properties = Map.of(
                "patient_portal.booking.offerable_codes", "B, F _ xyz",
                "patient_portal.booking.visit_mode", "phone",
                "patient_portal.booking.location_code", "main",
                "patient_portal.booking.offer_lead_hours", "48",
                "patient_portal.booking.sync.enabled", "true",
                "patient_portal.booking.sync.provider_no", "-9",
                "patient_portal.booking.sync.interval_seconds", "5");
        PortalBookingSettings settings = PortalBookingSettings.fromProperties(properties::get);
        // '_' is an empty template slot and a multi-letter value is not a code: neither is offerable.
        assertThat(settings.offerableCodes()).containsExactly('B', 'F');
        assertThat(settings.visitMode()).isEqualTo("phone");
        assertThat(settings.locationCode()).isEqualTo("main");
        assertThat(settings.leadHours()).isEqualTo(48);
        assertThat(settings.syncEnabled()).isTrue();
        assertThat(settings.syncProviderNo()).isEqualTo("-9");
        // Below the 15-second floor: the default minute is used instead.
        assertThat(settings.syncIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void shouldStayOff_whenSyncHasNoProvider() {
        PortalBookingSettings settings = PortalBookingSettings.fromProperties(
                Map.of("patient_portal.booking.sync.enabled", "true", "patient_portal.booking.visit_mode", "home")::get);
        assertThat(settings.syncEnabled()).isFalse();
        assertThat(settings.visitMode()).isEqualTo("in_person");
    }
}
