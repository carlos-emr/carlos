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
package io.github.carlos_emr.carlos.appointment.reminder;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A lock on one patient's record that names the system account (#5887): the reminder channels run as provider
 * {@code -1} with no HTTP session, so their access check must refuse quietly. The session-marking check would throw
 * inside the privilege evaluation and log an error with a stack trace for every such reminder.
 *
 * <p>The real {@link SecurityInfoManagerImpl} is used, with the system account's seeded rights and the lock.</p>
 */
@Tag("unit")
@DisplayName("Reminder channels and a record lock on the system account")
class ReminderPatientLockUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 42;
    private static final ClaimedReminder REMINDER =
            new ClaimedReminder(7L, 101, PATIENT, LocalDateTime.of(2026, 11, 13, 9, 0));
    private static final String SENDER = "fake-reminders@clinic.example.invalid";

    private final SecuserroleDao roles = mock(SecuserroleDao.class);
    private final SecObjPrivilegeDao privileges = mock(SecObjPrivilegeDao.class);
    private final SecurityInfoManagerImpl security = new SecurityInfoManagerImpl();
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    /** The system account's rights from the province seeds, and a lock on this patient that names it. */
    private List<SecObjPrivilege> granted =
            List.of(grant("_email", "x"), grant("_demographic", "r"), grant("_demographic$" + PATIENT, "o"));

    @BeforeEach
    void setUp() {
        registerMock(SecObjPrivilegeDao.class, privileges);
        injectDependency(security, "secUserRoleDao", roles);
        Provider system = new Provider();
        system.setProviderNo(Provider.SYSTEM_PROVIDER_NO);
        when(providers.getProvider(Provider.SYSTEM_PROVIDER_NO)).thenReturn(system);
        when(roles.findActiveByProviderNo(Provider.SYSTEM_PROVIDER_NO)).thenReturn(List.of());
        when(privileges.findByObjectNames(any())).thenAnswer(call -> {
            Collection<String> names = call.getArgument(0);
            return granted.stream().filter(row -> names.contains(row.getId().getObjectName())).toList();
        });
    }

    private static SecObjPrivilege grant(String object, String right) {
        SecObjPrivilege row = new SecObjPrivilege();
        row.setId(new SecObjPrivilegePrimaryKey(Provider.SYSTEM_PROVIDER_NO, object));
        row.setPrivilege(right);
        row.setPriority(0);
        return row;
    }

    @Test
    @DisplayName("should record no access, and log nothing, when the email channel meets the lock")
    void shouldRecordNoAccessQuietly_whenTheEmailChannelMeetsTheLock() {
        EmailManager emails = mock(EmailManager.class);
        EmailConfigDao senders = mock(EmailConfigDao.class);
        when(senders.findActiveEmailConfig(SENDER)).thenReturn(mock(EmailConfig.class));
        when(emails.clinicFooterMissing()).thenReturn(false);
        EmailReminderChannel channel = new EmailReminderChannel(emails, mock(EmailComposeManager.class), senders,
                demographics, mock(ClinicDAO.class), providers, security, () -> SENDER);

        try (LogCapture log = LogCapture.forLogger(SecurityInfoManagerImpl.class)) {
            assertThat(channel.send(REMINDER)).isEqualTo(
                    ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, "noPatientAccess"));
            assertThat(log.messages()).isEmpty();
        }
        verifyNoInteractions(demographics);
    }

    /** Guards the fixture: without the lock the same channel reaches the patient, so the lock is what refuses. */
    @Test
    @DisplayName("should read the patient when there is no lock")
    void shouldReadThePatient_whenThereIsNoLock() {
        granted = List.of(grant("_email", "x"), grant("_demographic", "r"));
        EmailManager emails = mock(EmailManager.class);
        EmailConfigDao senders = mock(EmailConfigDao.class);
        when(senders.findActiveEmailConfig(SENDER)).thenReturn(mock(EmailConfig.class));
        EmailReminderChannel channel = new EmailReminderChannel(emails, mock(EmailComposeManager.class), senders,
                demographics, mock(ClinicDAO.class), providers, security, () -> SENDER);

        // The unstubbed manager returns no patient, which the channel records as no address.
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_ADDRESS);
    }
}
