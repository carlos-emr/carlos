/**
 * Copyright (c) 2026 CARLOS Contributors.
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
package io.github.carlos_emr.carlos.appt;

import io.github.carlos_emr.carlos.appt.status.service.impl.AppointmentStatusMgrImpl;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link ApptStatusData#getNextStatus()} on the database-driven path (appointment status
 * editing is on by default): the schedule click-through must walk the active
 * {@code appointment_status} rows without running off the end of the list.
 *
 * @since 2026-09-28
 */
@DisplayName("ApptStatusData next status from appointment_status rows")
@Tag("unit")
@Tag("appointment")
class ApptStatusDataNextStatusUnitTest {

    private MockedStatic<AppointmentStatusMgrImpl> statusMgr;

    @AfterEach
    void tearDown() {
        if (statusMgr != null) {
            statusMgr.close();
        }
    }

    @Test
    void shouldReturnFollowingRow_forMidCycleStatus() {
        serve("t", "T", "H", "P");

        assertThat(nextOf("T")).isEqualTo("H");
    }

    @Test
    void shouldWrapToFirstRow_whenCurrentStatusIsLastActiveRow() {
        // A custom terminal status at the end of the list used to throw
        // IndexOutOfBoundsException from the unguarded increment.
        serve("t", "T", "H", "z");

        assertThat(nextOf("z")).isEqualTo("t");
    }

    @Test
    void shouldKeepSignedSuffix_whenWrapping() {
        serve("t", "T", "z");

        assertThat(nextOf("zS")).isEqualTo("tS");
    }

    @Test
    void shouldReturnEmpty_forBilledStatus() {
        serve("t", "T", "B");

        assertThat(nextOf("B")).isEmpty();
    }

    private void serve(String... codes) {
        List<AppointmentStatus> rows = Arrays.stream(codes).map(code -> {
            AppointmentStatus status = new AppointmentStatus();
            status.setStatus(code);
            status.setActive(1);
            return status;
        }).toList();
        statusMgr = mockStatic(AppointmentStatusMgrImpl.class);
        statusMgr.when(AppointmentStatusMgrImpl::getCachedActiveStatuses).thenReturn(rows);
    }

    private static String nextOf(String current) {
        ApptStatusData data = new ApptStatusData();
        data.setApptStatus(current);
        return data.getNextStatus();
    }
}
