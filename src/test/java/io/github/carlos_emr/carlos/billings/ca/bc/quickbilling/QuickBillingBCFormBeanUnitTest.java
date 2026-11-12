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
package io.github.carlos_emr.carlos.billings.ca.bc.quickbilling;

import java.util.ArrayList;
import java.util.Iterator;

import io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSessionBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The quick billing form's bill list as the page reads it: {@link QuickBillingBCFormBean#getBillingData()}
 * hands out a copy, so a page going through the bills is not disturbed by another request of the same
 * session adding or removing one, and nothing outside the form's own methods changes the list.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Quick Billing form bill list")
class QuickBillingBCFormBeanUnitTest {

    private static BillingSessionBean bill(String patientNo) {
        BillingSessionBean bill = new BillingSessionBean();
        bill.setPatientNo(patientNo);
        return bill;
    }

    @Test
    @DisplayName("should let the page go through its bills while another request adds and removes one")
    void shouldKeepSnapshotStable_whenListChangesDuringRendering() {
        QuickBillingBCFormBean form = new QuickBillingBCFormBean();
        form.addBill(bill("1"));
        form.addBill(bill("2"));

        ArrayList<BillingSessionBean> shown = form.getBillingData();
        Iterator<BillingSessionBean> rendering = shown.iterator();
        rendering.next();
        form.addBill(bill("3"));
        form.removeBill(0);

        assertThatCode(rendering::next).doesNotThrowAnyException();
        assertThat(shown).extracting("patientNo").containsExactly("1", "2");
        assertThat(form.getBillingData()).extracting("patientNo").containsExactly("2", "3");
    }

    @Test
    @DisplayName("should not change the form's bills when the copy it handed out is changed")
    void shouldKeepBills_whenHandedOutCopyIsChanged() {
        QuickBillingBCFormBean form = new QuickBillingBCFormBean();
        form.addBill(bill("1"));

        form.getBillingData().clear();

        assertThat(form.getBillingData()).extracting("patientNo").containsExactly("1");
    }

    @Test
    @DisplayName("should hand out an empty list, never null, before any bill is added")
    void shouldReturnEmptyList_whenNoBillAdded() {
        QuickBillingBCFormBean form = new QuickBillingBCFormBean();
        form.setBillingData(null);

        assertThat(form.getBillingData()).isNotNull().isEmpty();
    }
}
