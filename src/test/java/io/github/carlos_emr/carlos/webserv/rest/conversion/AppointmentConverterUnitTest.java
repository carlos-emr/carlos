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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppointmentConverter#getAsDomainObject}, which was a null-returning
 * stub. updateAppointment relied on it and therefore failed downstream (issue #2957).
 *
 * @since 2026-06-22
 */
@DisplayName("AppointmentConverter getAsDomainObject")
class AppointmentConverterUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void setUp() {
        // AppointmentConverter resolves these DAOs via SpringUtils in its field initializers.
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
    }

    @Test
    @DisplayName("should copy editable JavaBean properties onto a domain Appointment")
    void shouldCopyEditableProperties_toDomainAppointment() throws Exception {
        AppointmentTo1 to = new AppointmentTo1();
        to.setProviderNo("999");
        to.setDemographicNo(123);
        to.setStatus("t");
        to.setType("Consult");
        to.setReason("checkup");
        to.setNotes("note");
        Date appointmentDate = new Date();
        to.setAppointmentDate(appointmentDate);

        Appointment domain = new AppointmentConverter().getAsDomainObject(null, to);

        assertThat(domain).isNotNull();
        assertThat(domain.getProviderNo()).isEqualTo("999");
        assertThat(domain.getDemographicNo()).isEqualTo(123);
        assertThat(domain.getStatus()).isEqualTo("t");
        assertThat(domain.getType()).isEqualTo("Consult");
        assertThat(domain.getReason()).isEqualTo("checkup");
        assertThat(domain.getNotes()).isEqualTo("note");
        assertThat(domain.getAppointmentDate()).isEqualTo(appointmentDate);
    }

    @Test
    @DisplayName("should not copy identity or server-managed audit fields from the transfer object")
    void shouldNotCopyServerManagedFields_fromTransferObject() throws Exception {
        AppointmentTo1 to = new AppointmentTo1();
        to.setId(42);
        to.setCreator("hacker");
        to.setCreatorSecurityId(999);
        to.setLastUpdateUser("hacker");
        to.setCreateDateTime(new Date(0));

        Appointment domain = new AppointmentConverter().getAsDomainObject(null, to);

        assertThat(domain.getId()).isNull();
        assertThat(domain.getCreator()).isNull();
        assertThat(domain.getCreatorSecurityId()).isNull();
        assertThat(domain.getLastUpdateUser()).isNull();
        // createDateTime keeps the entity's own default, not the DTO-supplied epoch value.
        assertThat(domain.getCreateDateTime()).isNotEqualTo(new Date(0));
    }

    @Test
    @DisplayName("should apply only editable fields onto an existing appointment, preserving audit fields")
    void shouldApplyEditableFields_ontoExistingAppointment() throws Exception {
        Date originalCreate = new Date(1_000_000_000_000L);
        Appointment existing = new Appointment();
        existing.setId(42);
        existing.setCreateDateTime(originalCreate);
        existing.setCreator("origCreator");
        existing.setCreatorSecurityId(7);
        existing.setReason("old reason");

        AppointmentTo1 to = new AppointmentTo1();
        to.setId(42);
        to.setReason("new reason");
        to.setCreator("hacker");
        to.setCreatorSecurityId(999);
        to.setCreateDateTime(new Date(0));

        new AppointmentConverter().applyEditableProperties(to, existing);

        assertThat(existing.getReason()).isEqualTo("new reason");
        assertThat(existing.getId()).isEqualTo(42);
        assertThat(existing.getCreateDateTime()).isEqualTo(originalCreate);
        assertThat(existing.getCreator()).isEqualTo("origCreator");
        assertThat(existing.getCreatorSecurityId()).isEqualTo(7);
    }

    @Test
    @DisplayName("should return null when transfer object is null")
    void shouldReturnNull_whenTransferObjectIsNull() throws Exception {
        assertThat(new AppointmentConverter().getAsDomainObject(null, null)).isNull();
    }
}
