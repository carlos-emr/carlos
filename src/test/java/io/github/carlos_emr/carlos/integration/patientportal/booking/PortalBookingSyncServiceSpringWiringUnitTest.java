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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The booking sync service as Spring builds it: component-scanned, with two constructors, so the
 * public one must stay the {@code @Autowired} one. Without that marker Spring looks for a no-arg
 * constructor and the whole CARLOS context fails to start, which unit tests that call a
 * constructor directly cannot see.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("PortalBookingSyncService Spring wiring")
class PortalBookingSyncServiceSpringWiringUnitTest {

    @Test
    @DisplayName("should be built by Spring from its two collaborators and start off without a portal")
    void shouldCreateTheBean_throughItsAutowiredConstructor() {
        PortalBookingChoiceService bookings = mock(PortalBookingChoiceService.class);
        PortalBookingOfferService offers = mock(PortalBookingOfferService.class);
        try (var portalSwitch = mockStatic(PatientPortalSettings.class);
                LogCapture capture = LogCapture.forLogger(PortalBookingSyncService.class);
                AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // The portal is off, so the start-up hook decides before any key is read.
            portalSwitch.when(PatientPortalSettings::isConfigured).thenReturn(false);
            context.getBeanFactory().registerSingleton("portalBookingChoiceService", bookings);
            context.getBeanFactory().registerSingleton("portalBookingOfferService", offers);
            context.register(PortalBookingSyncService.class);

            context.refresh();

            PortalBookingSyncService sync = context.getBean(PortalBookingSyncService.class);
            assertThat(ReflectionTestUtils.getField(sync, "bookings")).isSameAs(bookings);
            assertThat(ReflectionTestUtils.getField(sync, "offers")).isSameAs(offers);
            // The portal client is looked up lazily, at the first run, never while the context starts.
            assertThat(ReflectionTestUtils.getField(sync, "portal")).isNotNull();
            // @PostConstruct ran: one INFO line saying the job is off, no WARN, and nothing scheduled.
            assertThat(capture.events()).filteredOn(event -> event.getLevel() == Level.WARN).isEmpty();
            assertThat(capture.events()).filteredOn(event -> event.getLevel() == Level.INFO)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getMessage().getFormattedMessage())
                            .isEqualTo("Portal job booking-sync is off: the patient portal is off"));
            assertThat(ReflectionTestUtils.getField(sync, "executor")).isNull();
            verifyNoInteractions(bookings, offers);
        }
    }
}
