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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each open BC bill is found only by its own key (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC bill staging")
class BcBillStagingUnitTest {

    private final MockHttpSession session = new MockHttpSession();

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "AAAAAAAAAAAAAAAAAAAAA!", "AAAAAAAAAAAAAAAAAAAAAAA"})
    void shouldFindNoBill_whenTheKeyIsMalformed(String key) {
        open(bill("101"));
        MockHttpServletRequest request = request();
        request.setParameter(BcBillStaging.KEY_PARAMETER, key);

        assertThat(BcBillStaging.resume(request)).isNull();
        assertThat(request.getAttribute(BcBillStaging.BEAN_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldDropTheOldestBill_whenMoreBillsAreOpenedThanTheCap() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i <= BcBillStaging.MAX_ENTRIES; i++) {
            keys.add(open(bill(Integer.toString(100 + i))));
        }

        assertThat(resume(keys.get(0))).isNull();
        assertThat(resume(keys.get(keys.size() - 1)).getPatientNo())
                .isEqualTo(Integer.toString(100 + BcBillStaging.MAX_ENTRIES));
    }

    @Test
    void shouldStoreTheUpdatedBill_whenAStepSavesIt() {
        String key = open(bill("101"));
        MockHttpServletRequest step = request();
        step.setParameter(BcBillStaging.KEY_PARAMETER, key);
        BillingSessionBean bill = BcBillStaging.resume(step);
        Object before = session.getAttributeNames().nextElement();
        Object storedBefore = session.getAttribute((String) before);

        bill.setDx1("FAKE-dx");
        BcBillStaging.save(step, bill);

        assertThat(session.getAttribute((String) before)).isNotSameAs(storedBefore);
        assertThat(resume(key).getDx1()).isEqualTo("FAKE-dx");
    }

    private String open(BillingSessionBean bill) {
        return BcBillStaging.open(request(), bill);
    }

    private BillingSessionBean resume(String key) {
        MockHttpServletRequest request = request();
        request.setParameter(BcBillStaging.KEY_PARAMETER, key);
        return BcBillStaging.resume(request);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private static BillingSessionBean bill(String patient) {
        BillingSessionBean bill = new BillingSessionBean();
        bill.setPatientNo(patient);
        return bill;
    }
}
