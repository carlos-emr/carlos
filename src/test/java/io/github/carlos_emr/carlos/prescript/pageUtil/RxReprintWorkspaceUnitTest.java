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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RxReprintWorkspace}: per-patient reprints follow the Rx bean lifecycle and stay bounded,
 * so reprinting for many patients cannot retain their scripts for the whole session (#3908).
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("RxReprintWorkspace lifecycle")
class RxReprintWorkspaceUnitTest {

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    private RxSessionBean openPatient(int demographicNo) {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        RxSessionBeanResolver.register(session, bean);
        return bean;
    }

    private static RxSessionBean reprintFor(int demographicNo) {
        RxSessionBean reprint = new RxSessionBean();
        reprint.setDemographicNo(demographicNo);
        return reprint;
    }

    @Test
    @DisplayName("should keep one reprint per open patient and end only the cleared patient's")
    void shouldKeepReprintPerPatient_whenCleared() {
        openPatient(1);
        openPatient(2);
        RxReprintWorkspace.store(session, reprintFor(1), "first");
        RxReprintWorkspace.store(session, reprintFor(2), null);

        RxReprintWorkspace.clear(session, 1);

        assertThat(RxReprintWorkspace.isReprinting(session, 1)).isFalse();
        assertThat(RxReprintWorkspace.find(session, 2)).isNotNull();
        assertThat(RxReprintWorkspace.find(session, 2).comment()).isEmpty();
    }

    @Test
    @DisplayName("should drop the reprint of a patient the session no longer holds")
    void shouldDropReprint_whenPatientBeanWasEvicted() {
        openPatient(1);
        RxReprintWorkspace.store(session, reprintFor(1), "old");
        // Open more patients than the cap: patient 1 (nothing staged) is evicted from the beans.
        for (int demographicNo = 2; demographicNo <= RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 2; demographicNo++) {
            openPatient(demographicNo);
        }
        assertThat(RxSessionBeanResolver.find(session, 1)).isNull();

        RxReprintWorkspace.store(session, reprintFor(2), "new");

        assertThat(RxReprintWorkspace.find(session, 1)).isNull();
        assertThat(RxReprintWorkspace.find(session, 2)).isNotNull();
    }

    @Test
    @DisplayName("should never hold more reprints than the per-patient cap, dropping the oldest")
    void shouldEvictOldestReprint_whenOverCap() {
        int cap = RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION;
        for (int demographicNo = 1; demographicNo <= cap; demographicNo++) {
            openPatient(demographicNo);
            RxReprintWorkspace.store(session, reprintFor(demographicNo), "r" + demographicNo);
        }
        assertThat(RxReprintWorkspace.find(session, 1)).isNotNull();

        // Patient 1's bean is evicted by opening one more, and its reprint goes with it; the
        // count still never exceeds the cap even for patients the beans still hold.
        openPatient(cap + 1);
        RxReprintWorkspace.store(session, reprintFor(cap + 1), "newest");

        assertThat(RxReprintWorkspace.find(session, 1)).isNull();
        assertThat(RxReprintWorkspace.find(session, cap + 1)).isNotNull();
        long held = 0;
        for (int demographicNo = 1; demographicNo <= cap + 1; demographicNo++) {
            if (RxReprintWorkspace.isReprinting(session, demographicNo)) {
                held++;
            }
        }
        assertThat(held).isLessThanOrEqualTo(cap);
    }
}
