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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Group Update's reminder steps run only after the group page's transaction has committed (#5887), so a reminder
 * that cannot be stored never rolls a booking back.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Group Update reminders after the group transaction")
class GroupUpdateRemindersUnitTest extends CarlosUnitTestBase {

    private final LoggedInInfo user = mock(LoggedInInfo.class);

    private static MockHttpServletRequest groupForm() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/appointment/appointmentgrouprecords");
        request.setParameter(AppointmentReminderChoice.SHOWN_PARAMETER, "1");
        request.setParameter(AppointmentReminderChoice.PARAMETER, "email reminder");
        return request;
    }

    private static Appointment booking(Integer id) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setDemographicNo(42);
        return appointment;
    }

    /**
     * A stand-in for the group page's transaction manager: what the page writes inside the transaction is saved
     * only when it commits, and nothing is saved when it rolls back.
     */
    private static final class GroupTransaction extends AbstractPlatformTransactionManager {
        final List<Integer> pending = new ArrayList<>();
        final List<Integer> saved = new ArrayList<>();
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            pending.clear();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            saved.addAll(pending);
            pending.clear();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
            pending.clear();
        }
    }

    @Test
    void shouldCallNoReminderService_untilTheGroupTransactionHasCommitted() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        GroupUpdateReminders steps = new GroupUpdateReminders(groupForm(), user);

        steps.moveReminders(101, booking(201));
        steps.recordAfterGroupUpdate(booking(201));
        verifyNoInteractions(reminders);

        steps.runAfterCommit();
        verify(reminders).moveReminders(user, 101, 201);
        verify(reminders).recordRequest(user, 201, 42, ReminderChannel.EMAIL, true);
    }

    /**
     * The bookings always save. Group Update rebooks two appointments in one transaction;
     * storing the reminders then fails. Both bookings stay saved, nothing is rolled back, the later reminder steps
     * still run, and the page is told to say the reminder was not recorded.
     */
    @Test
    void shouldLeaveEveryGroupBookingSaved_whenAReminderFails() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.moveReminders(any(), eq(101), anyInt())).thenThrow(new IllegalStateException("FAKE"));
        when(reminders.recordRequest(any(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("FAKE"));
        MockHttpServletRequest request = groupForm();
        GroupTransaction transactions = new GroupTransaction();
        GroupUpdateReminders steps = new GroupUpdateReminders(request, user);

        TransactionStatus groupTx = transactions.getTransaction(new DefaultTransactionDefinition());
        for (int[] rebooking : new int[][] {{101, 201}, {102, 202}}) {
            transactions.pending.add(rebooking[1]);
            steps.moveReminders(rebooking[0], booking(rebooking[1]));
        }
        steps.recordAfterGroupUpdate(booking(202));
        transactions.commit(groupTx);

        assertThatCode(steps::runAfterCommit).doesNotThrowAnyException();
        assertThat(transactions.saved).containsExactly(201, 202);
        assertThat(transactions.rollbacks).isZero();
        verify(reminders).moveReminders(user, 102, 202);
        verify(reminders).recordRequest(user, 202, 42, ReminderChannel.EMAIL, true);
        assertThat(AppointmentReminderChoice.notRecorded(request)).isTrue();
    }

    /**
     * #5887 (review): the rebooked appointment's reminder rows did not move, so the start already reminded for stayed
     * with the removed booking. A fresh choice on the new one would forget it and could remind the patient again, so
     * the Edit form's box is not written for it either, and the page says the setting was not recorded.
     */
    @Test
    void shouldNotWriteTheChoice_forARebookedAppointmentWhoseRemindersDidNotMove() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.moveReminders(any(), eq(101), anyInt())).thenThrow(new IllegalStateException("FAKE"));
        MockHttpServletRequest request = groupForm();
        GroupUpdateReminders steps = new GroupUpdateReminders(request, user);
        steps.moveReminders(101, booking(201));
        steps.moveReminders(102, booking(202));
        steps.recordAfterGroupUpdate(booking(201));

        steps.runAfterCommit();

        verify(reminders).moveReminders(user, 102, 202);
        verify(reminders, never()).recordRequest(any(), anyInt(), anyInt(), any(), anyBoolean());
        assertThat(AppointmentReminderChoice.notRecorded(request)).isTrue();
    }

    @Test
    void shouldRunEachStepOnce_whenRunAfterCommitIsCalledAgain() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        GroupUpdateReminders steps = new GroupUpdateReminders(groupForm(), user);
        steps.moveReminders(101, booking(201));

        steps.runAfterCommit();
        steps.runAfterCommit();

        verify(reminders).moveReminders(user, 101, 201);
    }
}
